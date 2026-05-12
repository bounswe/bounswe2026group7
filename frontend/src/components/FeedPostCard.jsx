import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { MoreHorizontal, Pencil, Trash2, History, Flag, Share2, Repeat2, Bookmark, Heart, MessageCircle } from 'lucide-react'
import Avatar from './Avatar'
import FeedAttachmentGrid from './FeedAttachmentGrid'
import EditHistoryModal from './EditHistoryModal'
import RepostModal from './RepostModal'
import ReportModal from './ReportModal'
import { showTransientToast } from '../utils/toast'
import { linkify } from '../utils/linkify'
import {
  toggleBookmarkOnPost,
  recordShareOnPost,
  toggleLikeOnPost,
  getPostInteractions,
  getPostComments,
  addCommentToPost,
  toggleLikeOnComment,
  repostPost,
} from '../services/api'

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
  const [historyOpen, setHistoryOpen] = useState(false)
  const [repostOpen, setRepostOpen] = useState(false)
  const [repostBusy, setRepostBusy] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const menuRef = useRef(null)

  // Local state mirrors viewer-relative interaction toggles + counts; backend
  // is the source of truth so we replace from the FeedPostInteractionState
  // every toggle response returns.
  const [bookmarked, setBookmarked] = useState(initialBookmarked)
  const [bookmarkBusy, setBookmarkBusy] = useState(false)
  const [shareBusy, setShareBusy] = useState(false)
  const [likeBusy, setLikeBusy] = useState(false)
  const [commentsOpen, setCommentsOpen] = useState(false)
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [commentBusy, setCommentBusy] = useState(false)
  const [commentError, setCommentError] = useState(null)
  const [commentDraft, setCommentDraft] = useState('')
  const [comments, setComments] = useState([])
  const [liked, setLiked] = useState(Boolean(post?.viewerHasLiked))
  const [bookmarkCount, setBookmarkCount] = useState(post?.bookmarkCount ?? 0)
  const [shareCount, setShareCount] = useState(post?.shareCount ?? 0)
  const [likeCount, setLikeCount] = useState(post?.likeCount ?? 0)
  const [commentCount, setCommentCount] = useState(post?.commentCount ?? 0)

  // Re-seed if parent swaps the post in (e.g., navigating between posts on
  // the detail page) or hands us a fresh `initialBookmarked` value.
  useEffect(() => {
    setBookmarked(initialBookmarked)
    setLiked(Boolean(post?.viewerHasLiked))
    setBookmarkCount(post?.bookmarkCount ?? 0)
    setShareCount(post?.shareCount ?? 0)
    setLikeCount(post?.likeCount ?? 0)
    setCommentCount(post?.commentCount ?? 0)
    setComments([])
    setCommentsOpen(false)
    setCommentDraft('')
    setCommentError(null)
  }, [
    post?.id,
    initialBookmarked,
    post?.bookmarkCount,
    post?.shareCount,
    post?.likeCount,
    post?.commentCount,
    post?.viewerHasLiked,
  ])

  useEffect(() => {
    let cancelled = false
    if (!post?.id) return undefined
    const needsInteractionState = post?.viewerHasLiked == null || post?.viewerHasBookmarked == null
    if (!needsInteractionState) return undefined
    getPostInteractions(post.id)
      .then((state) => {
        if (cancelled) return
        setLiked(state.viewerHasLiked)
        setLikeCount(state.likeCount)
        setCommentCount(state.commentCount)
        setBookmarkCount(state.bookmarkCount)
        setBookmarked(state.viewerHasBookmarked)
        setShareCount(state.shareCount)
      })
      .catch(() => {})
    return () => { cancelled = true }
  }, [post?.id, post?.viewerHasLiked, post?.viewerHasBookmarked])

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
  // #358: every viewer can report. Authors additionally get edit/delete/history.
  const canReport = viewerUserId != null && !isAuthor
  const showOverflow = canReport || (isAuthor && (onEdit || onDelete || post?.isEdited))

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

  async function handleLike(e) {
    e.stopPropagation()
    e.preventDefault()
    if (likeBusy) return
    const prevLiked = liked
    const prevCount = likeCount
    setLiked(!prevLiked)
    setLikeCount(prevLiked ? Math.max(0, prevCount - 1) : prevCount + 1)
    setLikeBusy(true)
    try {
      const state = await toggleLikeOnPost(post.id)
      setLiked(state.viewerHasLiked)
      setLikeCount(state.likeCount)
      setCommentCount(state.commentCount)
    } catch (err) {
      setLiked(prevLiked)
      setLikeCount(prevCount)
      window.alert(err?.message || 'Failed to update like')
    } finally {
      setLikeBusy(false)
    }
  }

  async function loadComments() {
    if (commentsLoading) return
    setCommentsLoading(true)
    setCommentError(null)
    try {
      const res = await getPostComments(post.id, 0, 50)
      const items = Array.isArray(res?.content) ? res.content : (Array.isArray(res) ? res : [])
      const ordered = [...items].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
      setComments(ordered)
    } catch (err) {
      setCommentError(err?.message || 'Failed to load comments')
      setComments([])
    } finally {
      setCommentsLoading(false)
    }
  }

  function handleToggleComments(e) {
    e.stopPropagation()
    e.preventDefault()
    const nextOpen = !commentsOpen
    setCommentsOpen(nextOpen)
    if (nextOpen && comments.length === 0) loadComments()
  }

  async function handleSubmitComment() {
    if (commentBusy) return
    const body = commentDraft.trim()
    if (!body) {
      setCommentError('Comment cannot be empty')
      return
    }
    setCommentBusy(true)
    setCommentError(null)
    try {
      const created = await addCommentToPost(post.id, body)
      setComments((prev) => [created, ...prev])
      setCommentDraft('')
      setCommentCount((prev) => prev + 1)
    } catch (err) {
      setCommentError(err?.message || 'Failed to post comment')
    } finally {
      setCommentBusy(false)
    }
  }

  async function handleCommentLike(commentId) {
    // Optimistic update
    const prevComments = [...comments]
    const commentIndex = comments.findIndex(c => c.id === commentId)
    if (commentIndex === -1) return

    const target = comments[commentIndex]
    const prevLiked = Boolean(target.viewerHasLiked)
    const prevCount = target.likeCount || 0

    const nextLiked = !prevLiked
    const nextCount = prevLiked ? Math.max(0, prevCount - 1) : prevCount + 1

    const updatedComments = [...comments]
    updatedComments[commentIndex] = {
      ...target,
      viewerHasLiked: nextLiked,
      likeCount: nextCount
    }
    setComments(updatedComments)

    try {
      const updated = await toggleLikeOnComment(commentId)
      // Sync with server response
      setComments(prev => {
        const idx = prev.findIndex(c => c.id === commentId)
        if (idx === -1) return prev
        const next = [...prev]
        next[idx] = updated
        return next
      })
    } catch (err) {
      // Rollback
      setComments(prevComments)
      window.alert(err?.message || 'Failed to update comment like')
    }
  }

  async function handleRepostConfirm(body) {
    if (repostBusy) return
    setRepostBusy(true)
    try {
      const state = await repostPost(post.id, body)
      // Backend returns the original post's updated interaction state; bump
      // the share count locally so the count next to the Share button matches.
      if (state?.shareCount != null) setShareCount(state.shareCount)
      onShared?.(state)
      setRepostOpen(false)
    } catch (err) {
      window.alert(err?.message || 'Failed to repost')
    } finally {
      setRepostBusy(false)
    }
  }

  function openRepost(e) {
    e.stopPropagation()
    e.preventDefault()
    setRepostOpen(true)
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

  // #542: when the Following-feed surfaces a repost, the FeedPostListItem
  // carries sharedById / sharedByFirstName / commentary / sharedAt. Render
  // a small "Reposted by X" attribution above the original-post body and,
  // for quote-shares, a commentary block between the attribution and body.
  const isRepostSurface = post?.sharedById != null

  return (
    <article
      className={`feed-card${clickable ? ' feed-card--clickable' : ''}`}
      onClick={openDetail}
      data-testid={`feed-post-card-${post.id}`}
    >
      {isRepostSurface && (
        <div className="feed-card-repost-banner">
          <Repeat2 size={13} strokeWidth={2} />
          <span>
            Reposted by <strong>{post.sharedByFirstName || 'someone'}</strong>
            {post.sharedAt && <span className="feed-card-repost-time"> · {formatPostTime(post.sharedAt)}</span>}
          </span>
        </div>
      )}

      {isRepostSurface && post.commentary && (
        <div className="feed-card-repost-commentary">{post.commentary}</div>
      )}

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
              {post.isEdited && <span className="feed-card-edited" data-testid={`feed-post-edited-${post.id}`}> · edited</span>}
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
              data-testid={`feed-post-menu-${post.id}`}
            >
              <MoreHorizontal size={18} strokeWidth={1.75} />
            </button>
            {menuOpen && (
              <div className="feed-card-menu" role="menu">
                {onEdit && (
                  <button
                    type="button"
                    className="feed-card-menu-item"
                    onClick={stopAndRun(onEdit)}
                    role="menuitem"
                    data-testid={`feed-post-edit-${post.id}`}
                  >
                    <Pencil size={14} strokeWidth={1.75} /> Edit
                  </button>
                )}
                {post?.isEdited && (
                  <button
                    type="button"
                    className="feed-card-menu-item"
                    onClick={(e) => { e.stopPropagation(); e.preventDefault(); setMenuOpen(false); setHistoryOpen(true) }}
                    role="menuitem"
                  >
                    <History size={14} strokeWidth={1.75} /> View edit history
                  </button>
                )}
                {onDelete && (
                  <button
                    type="button"
                    className="feed-card-menu-item feed-card-menu-item--danger"
                    onClick={stopAndRun(onDelete)}
                    role="menuitem"
                    data-testid={`feed-post-delete-${post.id}`}
                  >
                    <Trash2 size={14} strokeWidth={1.75} /> Delete
                  </button>
                )}
                {canReport && (
                  <button
                    type="button"
                    className="feed-card-menu-item feed-card-menu-item--danger"
                    onClick={(e) => { e.stopPropagation(); e.preventDefault(); setMenuOpen(false); setReportOpen(true) }}
                    role="menuitem"
                  >
                    <Flag size={14} strokeWidth={1.75} /> Report
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      <div className="feed-card-body" data-testid={`feed-post-body-${post.id}`}>{renderBody(post.body)}</div>

      <div data-testid={`feed-post-attachments-${post.id}`}>
        <FeedAttachmentGrid attachments={post.attachments} />
      </div>

      {Array.isArray(post.hashtags) && post.hashtags.length > 0 && (
        <div className="feed-card-tags">
          {post.hashtags.map(t => (
            <span key={t} className="feed-card-tag" data-testid={`feed-post-hashtag-${post.id}-${t}`}>#{t}</span>
          ))}
        </div>
      )}

      <div className="feed-card-actions">
        <button
          type="button"
          className={`feed-card-action-btn${liked ? ' feed-card-action-btn--active' : ''}`}
          onClick={handleLike}
          disabled={likeBusy}
          aria-label={liked ? 'Unlike post' : 'Like post'}
          aria-pressed={liked}
          title={liked ? 'Liked' : 'Like'}
          data-testid={`feed-post-like-${post.id}`}
        >
          <Heart size={16} strokeWidth={1.75} fill={liked ? 'currentColor' : 'none'} />
          <span>{likeCount}</span>
        </button>
        <button
          type="button"
          className={`feed-card-action-btn${commentsOpen ? ' feed-card-action-btn--active' : ''}`}
          onClick={handleToggleComments}
          aria-label="View comments"
          aria-expanded={commentsOpen}
          title="Comments"
          data-testid={`feed-post-comment-${post.id}`}
        >
          <MessageCircle size={16} strokeWidth={1.75} />
          <span>{commentCount}</span>
        </button>
        <span className="feed-card-action-spacer" aria-hidden="true" />
        <button
          type="button"
          className="feed-card-action-btn"
          onClick={openRepost}
          disabled={repostBusy}
          aria-label="Repost or quote-share"
          title="Repost"
        >
          <Repeat2 size={16} strokeWidth={1.75} />
        </button>
        <button
          type="button"
          className="feed-card-action-btn"
          onClick={handleShare}
          disabled={shareBusy}
          aria-label="Share post"
          title="Share"
          data-testid={`feed-post-share-${post.id}`}
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
          data-testid={`feed-post-bookmark-${post.id}`}
        >
          <Bookmark
            size={16}
            strokeWidth={1.75}
            fill={bookmarked ? 'currentColor' : 'none'}
          />
          <span>{bookmarkCount}</span>
        </button>
      </div>

      {commentsOpen && (
        <div className="feed-card-comments" onClick={(e) => e.stopPropagation()}>
          {commentsLoading ? (
            <div className="feed-comment-loading">Loading comments…</div>
          ) : comments.length === 0 ? (
            <div className="feed-comment-empty">No comments yet.</div>
          ) : (
            <div className="feed-comment-list">
              {comments.map((comment) => (
                <div key={comment.id} className="feed-comment">
                  <div className="feed-comment-avatar">
                    {(comment.authorFirstName?.[0] || '?').toUpperCase()}
                  </div>
                  <div className="feed-comment-content">
                    <div className="feed-comment-meta">
                      {comment.authorId ? (
                        <button
                          type="button"
                          className="feed-comment-author"
                          onClick={() => navigate(`/users/${comment.authorId}`)}
                        >
                          {comment.authorFirstName || 'User'}
                        </button>
                      ) : (
                        <span className="feed-comment-author feed-comment-author--muted">Deleted user</span>
                      )}
                      <span className="feed-comment-time">
                        {formatPostTime(comment.createdAt)}
                        {comment.isEdited && <span className="feed-card-edited"> · edited</span>}
                      </span>
                    </div>
                    <div className="feed-comment-body">
                      {comment.isDeleted || !comment.body
                        ? <span className="feed-comment-deleted">Comment removed</span>
                        : renderTextWithMentions(comment.body, 'comment')}
                    </div>
                    {!comment.isDeleted && (
                      <button
                        type="button"
                        className={`feed-comment-like-btn${comment.viewerHasLiked ? ' feed-comment-like-btn--active' : ''}`}
                        onClick={() => handleCommentLike(comment.id)}
                        aria-label={comment.viewerHasLiked ? 'Unlike comment' : 'Like comment'}
                        title={comment.viewerHasLiked ? 'Liked' : 'Like'}
                      >
                        <Heart
                          size={13}
                          strokeWidth={2}
                          fill={comment.viewerHasLiked ? 'currentColor' : 'none'}
                        />
                        <span>{comment.likeCount || 0}</span>
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="feed-comment-composer">
            <textarea
              className="feed-comment-input"
              placeholder="Write a comment…"
              rows={3}
              value={commentDraft}
              onChange={(e) => setCommentDraft(e.target.value)}
              disabled={commentBusy}
            />
            <button
              type="button"
              className="feed-comment-submit"
              onClick={handleSubmitComment}
              disabled={commentBusy || !commentDraft.trim()}
            >
              {commentBusy ? 'Posting…' : 'Post'}
            </button>
          </div>
          {commentError && <div className="feed-comment-error">{commentError}</div>}
        </div>
      )}

      <EditHistoryModal
        open={historyOpen}
        postId={post.id}
        onClose={() => setHistoryOpen(false)}
      />

      <RepostModal
        open={repostOpen}
        post={post}
        onClose={() => !repostBusy && setRepostOpen(false)}
        onConfirm={handleRepostConfirm}
        loading={repostBusy}
      />

      <ReportModal
        open={reportOpen}
        targetType="POST"
        targetId={post.id}
        targetLabel="post"
        onClose={() => setReportOpen(false)}
        onSubmitted={() => showTransientToast('Report submitted. Admins will review it.')}
      />
    </article>
  )
}

function renderBody(body) {
  if (!body) return null
  return renderTextWithMentions(body, 'post')
}

function renderTextWithMentions(text, keyPrefix) {
  if (!text) return null
  const output = []
  let key = 0
  linkify(text).forEach((part) => {
    if (part && typeof part === 'object' && part.kind === 'url') {
      output.push(
        <a
          key={`${keyPrefix}-url-${key++}`}
          href={part.url}
          target="_blank"
          rel="noopener noreferrer"
          className="md-message-link"
          onClick={(e) => e.stopPropagation()}
        >
          {part.url}
        </a>
      )
      return
    }
    const segment = String(part)
    const mentionRegex = /@([a-zA-Z0-9_.-]+)/g
    let lastIndex = 0
    let match
    while ((match = mentionRegex.exec(segment)) !== null) {
      if (match.index > lastIndex) {
        output.push(
          <span key={`${keyPrefix}-text-${key++}`}>
            {segment.slice(lastIndex, match.index)}
          </span>
        )
      }
      const handle = match[1]
      output.push(
        <a
          key={`${keyPrefix}-mention-${key++}`}
          href={`/users/${encodeURIComponent(handle)}`}
          className="mention-link"
          onClick={(e) => e.stopPropagation()}
        >
          @{handle}
        </a>
      )
      lastIndex = match.index + match[0].length
    }
    if (lastIndex < segment.length) {
      output.push(
        <span key={`${keyPrefix}-tail-${key++}`}>
          {segment.slice(lastIndex)}
        </span>
      )
    }
  })
  return output
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
