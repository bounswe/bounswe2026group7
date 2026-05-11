import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { MoreHorizontal, Pencil, Trash2, Share2, Bookmark } from 'lucide-react'
import Avatar from './Avatar'
import { linkify } from '../utils/linkify'
import { toggleBookmarkOnPost, recordShareOnPost } from '../services/api'

/**
 * Renders a single feed post.
 *
 * Accepts either a FeedPostListItem (from /for-you, /following, /search)
 * or a FeedPostResponse (from /feed/posts/{id}). The two shapes share the
 * core fields (id, authorId, authorFirstName, body, hashtags, createdAt);
 * detail-only fields (isEdited, isAuthor) are read defensively.
 *
 * Author affordances (edit / delete) are rendered when:
 *   - `viewerUserId` matches `post.authorId`, OR
 *   - the detail-only `post.isAuthor === true`.
 *
 * Edit and Delete actions delegate to props so the page can decide
 * whether to open a modal, navigate, or just remove from a list:
 *   - onEdit(post)   — invoked when the user picks "Edit"
 *   - onDelete(post) — invoked when the user picks "Delete"
 *
 * Both are optional; the menu hides any item without a handler.
 *
 * Bookmark + Share (#340):
 *   - `initialBookmarked` seeds the bookmark toggle state for callers that
 *     know it (e.g. the /feed/bookmarks page where every visible post is
 *     bookmarked by definition). On the For-You / Following list we don't
 *     have viewer state up front, so the icon starts unfilled and flips on
 *     first click.
 *   - `onBookmarkToggled(state)` fires after a successful bookmark POST
 *     with the FeedPostInteractionState the backend returned. Used by
 *     /feed/bookmarks to drop the row when the user removes a bookmark.
 *   - `onShared(state)` fires after a successful share POST.
 */
export default function FeedPostCard({
  post,
  viewerUserId = null,
  onEdit = null,
  onDelete = null,
  clickable = true,
  initialBookmarked = false,
  onBookmarkToggled = null,
  onShared = null,
}) {
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef(null)

  // Local state mirrors viewer-relative interaction toggles + counts; backend
  // is the source of truth so we replace from the FeedPostInteractionState
  // every toggle response returns.
  const [bookmarked, setBookmarked] = useState(initialBookmarked)
  const [bookmarkBusy, setBookmarkBusy] = useState(false)
  const [shareBusy, setShareBusy] = useState(false)
  const [bookmarkCount, setBookmarkCount] = useState(post?.bookmarkCount ?? 0)
  const [shareCount, setShareCount] = useState(post?.shareCount ?? 0)

  // Re-seed if parent swaps the post in (e.g., navigating between posts on
  // the detail page) or hands us a fresh `initialBookmarked` value.
  useEffect(() => {
    setBookmarked(initialBookmarked)
    setBookmarkCount(post?.bookmarkCount ?? 0)
    setShareCount(post?.shareCount ?? 0)
  }, [post?.id, initialBookmarked, post?.bookmarkCount, post?.shareCount])

  const isAuthor = post?.isAuthor === true ||
    (viewerUserId != null && String(post?.authorId) === String(viewerUserId))

  useEffect(() => {
    function handleClickOutside(e) {
      if (menuRef.current && !menuRef.current.contains(e.target)) setMenuOpen(false)
    }
    if (menuOpen) document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [menuOpen])

  if (!post) return null

  const initials = (post.authorFirstName?.[0] || '?').toUpperCase()
  const showOverflow = isAuthor && (onEdit || onDelete)

  function openDetail() {
    if (clickable) navigate(`/feed/${post.id}`)
  }

  function stopAndRun(fn) {
    return (e) => {
      e.stopPropagation()
      e.preventDefault()
      setMenuOpen(false)
      fn?.(post)
    }
  }

  async function handleBookmark(e) {
    e.stopPropagation()
    e.preventDefault()
    if (bookmarkBusy) return
    // Optimistic flip; rollback on error.
    const prevBookmarked = bookmarked
    const prevCount = bookmarkCount
    setBookmarked(!prevBookmarked)
    setBookmarkCount(prevBookmarked ? Math.max(0, prevCount - 1) : prevCount + 1)
    setBookmarkBusy(true)
    try {
      const state = await toggleBookmarkOnPost(post.id)
      setBookmarked(state.viewerHasBookmarked)
      setBookmarkCount(state.bookmarkCount)
      onBookmarkToggled?.(state)
    } catch (err) {
      setBookmarked(prevBookmarked)
      setBookmarkCount(prevCount)
      window.alert(err?.message || 'Failed to update bookmark')
    } finally {
      setBookmarkBusy(false)
    }
  }

  async function handleShare(e) {
    e.stopPropagation()
    e.preventDefault()
    if (shareBusy) return
    setShareBusy(true)
    try {
      // Append-only on the backend — every click records an event.
      const state = await recordShareOnPost(post.id)
      setShareCount(state.shareCount)
      onShared?.(state)
      // Native share sheet on mobile / supporting browsers; fallback to
      // copying the link so non-supporting browsers still get value.
      const shareUrl = `${window.location.origin}/feed/${post.id}`
      if (navigator.share) {
        try { await navigator.share({ url: shareUrl, text: post.body?.slice(0, 120) }) }
        catch { /* user dismissed */ }
      } else if (navigator.clipboard?.writeText) {
        try {
          await navigator.clipboard.writeText(shareUrl)
          showTransientToast('Link copied to clipboard')
        } catch { /* clipboard blocked — silent */ }
      }
    } catch (err) {
      window.alert(err?.message || 'Failed to share post')
    } finally {
      setShareBusy(false)
    }
  }

  return (
    <article
      className={`feed-card${clickable ? ' feed-card--clickable' : ''}`}
      onClick={openDetail}
    >
      <div className="feed-card-header">
        <button
          type="button"
          className="feed-card-author"
          onClick={(e) => { e.stopPropagation(); navigate(`/users/${post.authorId}`) }}
          aria-label={`View ${post.authorFirstName}'s profile`}
        >
          <Avatar initials={initials} size="sm" />
          <div className="feed-card-author-meta">
            <span className="feed-card-author-name">{post.authorFirstName || '—'}</span>
            <span className="feed-card-time">
              {formatPostTime(post.createdAt)}
              {post.isEdited && <span className="feed-card-edited"> · edited</span>}
            </span>
          </div>
        </button>

        {showOverflow && (
          <div className="feed-card-menu-wrap" ref={menuRef}>
            <button
              type="button"
              className="feed-card-menu-trigger"
              onClick={(e) => { e.stopPropagation(); setMenuOpen(v => !v) }}
              aria-haspopup="true"
              aria-expanded={menuOpen}
              aria-label="Post options"
            >
              <MoreHorizontal size={18} strokeWidth={1.75} />
            </button>
            {menuOpen && (
              <div className="feed-card-menu" role="menu">
                {onEdit && (
                  <button type="button" className="feed-card-menu-item" onClick={stopAndRun(onEdit)} role="menuitem">
                    <Pencil size={14} strokeWidth={1.75} /> Edit
                  </button>
                )}
                {onDelete && (
                  <button
                    type="button"
                    className="feed-card-menu-item feed-card-menu-item--danger"
                    onClick={stopAndRun(onDelete)}
                    role="menuitem"
                  >
                    <Trash2 size={14} strokeWidth={1.75} /> Delete
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      <div className="feed-card-body">{renderBody(post.body)}</div>

      {Array.isArray(post.hashtags) && post.hashtags.length > 0 && (
        <div className="feed-card-tags">
          {post.hashtags.map(t => (
            <span key={t} className="feed-card-tag">#{t}</span>
          ))}
        </div>
      )}

      <div className="feed-card-actions">
        <span className="feed-card-action-meta">{post.likeCount ?? 0} likes</span>
        <span className="feed-card-action-meta">{post.commentCount ?? 0} comments</span>
        <button
          type="button"
          className="feed-card-action-btn"
          onClick={handleShare}
          disabled={shareBusy}
          aria-label="Share post"
          title="Share"
        >
          <Share2 size={16} strokeWidth={1.75} />
          <span>{shareCount}</span>
        </button>
        <button
          type="button"
          className={`feed-card-action-btn${bookmarked ? ' feed-card-action-btn--active' : ''}`}
          onClick={handleBookmark}
          disabled={bookmarkBusy}
          aria-label={bookmarked ? 'Remove bookmark' : 'Bookmark post'}
          aria-pressed={bookmarked}
          title={bookmarked ? 'Bookmarked' : 'Bookmark'}
        >
          <Bookmark
            size={16}
            strokeWidth={1.75}
            fill={bookmarked ? 'currentColor' : 'none'}
          />
          <span>{bookmarkCount}</span>
        </button>
      </div>
    </article>
  )
}

// Lightweight toast helper for share-link copy feedback. Avoids pulling in
// a toast library for this single use; if more callers appear, extract.
function showTransientToast(text) {
  const el = document.createElement('div')
  el.className = 'toast toast-success'
  el.textContent = text
  document.body.appendChild(el)
  setTimeout(() => el.remove(), 2500)
}

function renderBody(body) {
  if (!body) return null
  return linkify(body).map((part, i) => {
    if (part && typeof part === 'object' && part.kind === 'url') {
      return (
        <a
          key={i}
          href={part.url}
          target="_blank"
          rel="noopener noreferrer"
          className="md-message-link"
          onClick={(e) => e.stopPropagation()}
        >
          {part.url}
        </a>
      )
    }
    return <span key={i}>{part}</span>
  })
}

function formatPostTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const diffMs = Date.now() - d.getTime()
  const m = Math.floor(diffMs / 60000)
  if (m < 1) return 'just now'
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}
