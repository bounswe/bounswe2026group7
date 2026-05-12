import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import FeedPostCard from '../components/FeedPostCard'
import FeedImageUploader from '../components/FeedImageUploader'
import { getFeedPostById, updateFeedPost, deleteFeedPost, restoreFeedPost } from '../services/api'
import { useAuth } from '../context/AuthContext'
import { showUndoToast } from '../utils/toast'
import useFeedSubscription from '../hooks/useFeedSubscription'
import '../styles/main.css'

export default function FeedPostDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { userId } = useAuth()

  const [post, setPost] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // #563: subscribe to the viewer's feed topic so a like / comment / share
  // on the post being viewed updates the counts in place. Engagement frames
  // for other posts are ignored. New-post and repost frames are ignored on
  // the detail surface — there's no list to insert them into.
  useFeedSubscription(userId, {
    onEngagement: (payload) => {
      if (!post || String(post.id) !== String(payload.postId)) return
      setPost(prev => prev ? {
        ...prev,
        likeCount: payload.likeCount,
        commentCount: payload.commentCount,
        shareCount: payload.shareCount,
      } : prev)
    },
  })

  // Edit modal state — same pattern as FeedPage; would extract a shared
  // <EditPostModal /> if/when a third caller appears.
  const [editing, setEditing] = useState(null)
  const [editBody, setEditBody] = useState('')
  const [editHashtags, setEditHashtags] = useState('')
  const [editAttachments, setEditAttachments] = useState([])
  const [editBusy, setEditBusy] = useState(false)
  const [editError, setEditError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getFeedPostById(id)
      .then(p => { if (!cancelled) setPost(p) })
      .catch(err => { if (!cancelled) setError(err?.message || 'Failed to load post') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [id])

  function openEdit(p) {
    setEditing(p)
    setEditBody(p.body || '')
    setEditHashtags((p.hashtags || []).join(' '))
    setEditAttachments(Array.isArray(p.attachments) ? p.attachments : [])
    setEditError(null)
  }

  async function submitEdit() {
    if (!editing) return
    const body = editBody.trim()
    if (!body) { setEditError('Body cannot be empty.'); return }
    setEditBusy(true)
    setEditError(null)
    try {
      const tags = editHashtags
        .split(/\s+/)
        .map(t => t.replace(/^#/, '').trim())
        .filter(Boolean)
      const updated = await updateFeedPost(editing.id, {
        body,
        hashtags: tags,
        attachmentIds: editAttachments.map(a => a.id),
      })
      setPost(updated)
      setEditing(null)
    } catch (err) {
      setEditError(err?.message || 'Failed to update post')
    } finally {
      setEditBusy(false)
    }
  }

  async function handleDelete(p) {
    if (!p) return
    const confirmed = window.confirm(
      'Hide this post? You can restore it within 30 days from the toast on the feed page.'
    )
    if (!confirmed) return
    try {
      await deleteFeedPost(p.id)
      navigate('/feed')
      // Drop the toast on the feed route the user just landed on. The async
      // restore re-routes them to the detail page on success.
      showUndoToast('Post hidden.', async () => {
        try {
          const restored = await restoreFeedPost(p.id)
          navigate(`/feed/${restored.id}`)
        } catch (err) {
          window.alert(err?.message || 'Failed to restore post')
        }
      })
    } catch (err) {
      window.alert(err?.message || 'Failed to delete post')
    }
  }

  return (
    <MainLayout>
      <div className="feed-page">
        <div className="page-header">
          <div>
            <button
              onClick={() => navigate('/feed')}
              style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px', padding: 0, marginBottom: '8px' }}
            >
              ← Back to feed
            </button>
            <div className="page-title">Post</div>
          </div>
        </div>

        {loading ? (
          <div className="md-loading">Loading post…</div>
        ) : error ? (
          <div className="md-error-card">
            <div className="md-error-title">Couldn’t load post</div>
            <div className="md-error-sub">{error}</div>
          </div>
        ) : post ? (
          <FeedPostCard
            post={post}
            viewerUserId={userId}
            onEdit={openEdit}
            onDelete={handleDelete}
            clickable={false}
          />
        ) : null}
      </div>

      {editing && (
        <div className="modal-backdrop" onClick={() => !editBusy && setEditing(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <h2>Edit post</h2>
            <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Body</label>
            <textarea
              className="modal-textarea"
              value={editBody}
              onChange={(e) => setEditBody(e.target.value)}
              rows={5}
              disabled={editBusy}
            />
            <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Hashtags (space-separated)</label>
            <input
              className="modal-textarea"
              value={editHashtags}
              onChange={(e) => setEditHashtags(e.target.value)}
              placeholder="design react ux"
              disabled={editBusy}
              style={{ minHeight: 'auto', height: '40px' }}
            />
            <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Images</label>
            <FeedImageUploader
              value={editAttachments}
              onChange={setEditAttachments}
              disabled={editBusy}
            />
            {editError && <div className="md-composer-error" style={{ marginTop: '8px' }}>{editError}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
              <button className="action-btn" onClick={() => setEditing(null)} disabled={editBusy}>Cancel</button>
              <button className="md-composer-send" onClick={submitEdit} disabled={editBusy || !editBody.trim()}>
                {editBusy ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}
    </MainLayout>
  )
}
