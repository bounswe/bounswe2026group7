import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { MoreHorizontal, Pencil, Trash2 } from 'lucide-react'
import Avatar from './Avatar'
import { linkify } from '../utils/linkify'

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
 */
export default function FeedPostCard({
  post,
  viewerUserId = null,
  onEdit = null,
  onDelete = null,
  clickable = true,
}) {
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef(null)

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

      {(post.likeCount != null || post.commentCount != null) && (
        <div className="feed-card-counts">
          <span>{post.likeCount ?? 0} likes</span>
          <span>{post.commentCount ?? 0} comments</span>
        </div>
      )}
    </article>
  )
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
