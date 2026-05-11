import { useEffect, useState, useCallback } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import FeedPostCard from '../components/FeedPostCard'
import {
  getForYouFeed,
  getFollowingFeed,
  searchFeed,
  createFeedPost,
  updateFeedPost,
  deleteFeedPost,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

const TABS = [
  { key: 'for-you', label: 'For you' },
  { key: 'following', label: 'Following' },
]

export default function FeedPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const { userId } = useAuth()

  const tab = TABS.some(t => t.key === params.get('tab')) ? params.get('tab') : 'for-you'
  const [posts, setPosts] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const [searchQuery, setSearchQuery] = useState('')
  const [activeSearch, setActiveSearch] = useState(null) // { q?, hashtag? } or null

  // Minimal compose — full UI lands in #353
  const [composeOpen, setComposeOpen] = useState(false)
  const [composeBody, setComposeBody] = useState('')
  const [composeBusy, setComposeBusy] = useState(false)
  const [composeError, setComposeError] = useState(null)

  // Edit modal state
  const [editing, setEditing] = useState(null) // post object or null
  const [editBody, setEditBody] = useState('')
  const [editHashtags, setEditHashtags] = useState('')
  const [editBusy, setEditBusy] = useState(false)
  const [editError, setEditError] = useState(null)

  // ── Load posts ────────────────────────────────────────────────────────
  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      let page
      if (activeSearch) {
        page = await searchFeed({ ...activeSearch, page: 0, size: 30 })
      } else if (tab === 'following') {
        page = await getFollowingFeed(0, 30)
      } else {
        page = await getForYouFeed(0, 30)
      }
      setPosts(page?.content || [])
    } catch (err) {
      setError(err?.message || 'Failed to load feed')
      setPosts([])
    } finally {
      setLoading(false)
    }
  }, [tab, activeSearch])

  useEffect(() => { reload() }, [reload])

  // ── Tab nav ───────────────────────────────────────────────────────────
  function changeTab(next) {
    setActiveSearch(null)
    setSearchQuery('')
    const newParams = new URLSearchParams(params)
    newParams.set('tab', next)
    setParams(newParams, { replace: true })
  }

  // ── Search ────────────────────────────────────────────────────────────
  function handleSearchSubmit(e) {
    e.preventDefault()
    const trimmed = searchQuery.trim()
    if (!trimmed) {
      setActiveSearch(null)
      return
    }
    // Treat any leading "#word" as a hashtag query
    if (/^#?\w+$/.test(trimmed) && trimmed.startsWith('#')) {
      setActiveSearch({ hashtag: trimmed.slice(1) })
    } else {
      setActiveSearch({ q: trimmed })
    }
  }

  function clearSearch() {
    setSearchQuery('')
    setActiveSearch(null)
  }

  // ── Compose ───────────────────────────────────────────────────────────
  async function submitCompose() {
    const body = composeBody.trim()
    if (!body) return
    setComposeBusy(true)
    setComposeError(null)
    try {
      const created = await createFeedPost({ body, hashtags: extractHashtags(body) })
      setComposeBody('')
      setComposeOpen(false)
      // Optimistically prepend for visibility; reload picks up the canonical state
      setPosts(prev => [created, ...prev])
    } catch (err) {
      setComposeError(err?.message || 'Failed to publish post')
    } finally {
      setComposeBusy(false)
    }
  }

  // ── Edit ──────────────────────────────────────────────────────────────
  function openEdit(post) {
    setEditing(post)
    setEditBody(post.body || '')
    setEditHashtags((post.hashtags || []).join(' '))
    setEditError(null)
  }

  async function submitEdit() {
    if (!editing) return
    const body = editBody.trim()
    if (!body) {
      setEditError('Body cannot be empty.')
      return
    }
    setEditBusy(true)
    setEditError(null)
    try {
      const tags = editHashtags
        .split(/\s+/)
        .map(t => t.replace(/^#/, '').trim())
        .filter(Boolean)
      const updated = await updateFeedPost(editing.id, { body, hashtags: tags })
      setPosts(prev => prev.map(p => p.id === updated.id
        ? { ...p, body: updated.body, hashtags: updated.hashtags, isEdited: true }
        : p))
      setEditing(null)
    } catch (err) {
      setEditError(err?.message || 'Failed to update post')
    } finally {
      setEditBusy(false)
    }
  }

  // ── Delete ────────────────────────────────────────────────────────────
  async function handleDelete(post) {
    if (!post) return
    const confirmed = window.confirm('Delete this post? This cannot be undone.')
    if (!confirmed) return
    try {
      await deleteFeedPost(post.id)
      setPosts(prev => prev.filter(p => p.id !== post.id))
    } catch (err) {
      window.alert(err?.message || 'Failed to delete post')
    }
  }

  // ── Render ────────────────────────────────────────────────────────────
  return (
    <MainLayout>
      <div className="feed-page">
        <div className="page-header">
          <div>
            <div className="page-title">Feed</div>
            <div className="page-sub">Posts from people you follow and recommendations</div>
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              className="action-btn"
              onClick={() => navigate('/feed/bookmarks')}
              title="View bookmarked posts"
            >
              Bookmarks
            </button>
            <button
              className="action-btn"
              onClick={() => setComposeOpen(v => !v)}
            >
              {composeOpen ? 'Close' : 'New post'}
            </button>
          </div>
        </div>

        {composeOpen && (
          <div className="card" style={{ marginBottom: '16px' }}>
            <textarea
              className="md-composer-input"
              rows={3}
              placeholder="Share something with the community… (#hashtags supported)"
              value={composeBody}
              onChange={(e) => setComposeBody(e.target.value)}
              disabled={composeBusy}
              style={{ width: '100%', maxHeight: 'none', resize: 'vertical' }}
            />
            {composeError && <div className="md-composer-error">{composeError}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '8px' }}>
              <button
                className="md-composer-send"
                onClick={submitCompose}
                disabled={composeBusy || !composeBody.trim()}
              >
                {composeBusy ? 'Publishing…' : 'Publish'}
              </button>
            </div>
          </div>
        )}

        <div className="feed-tabs">
          {TABS.map(t => (
            <button
              key={t.key}
              type="button"
              className={`feed-tab${tab === t.key && !activeSearch ? ' feed-tab--active' : ''}`}
              onClick={() => changeTab(t.key)}
            >
              {t.label}
            </button>
          ))}
        </div>

        <form className="feed-search" onSubmit={handleSearchSubmit}>
          <input
            type="text"
            className="feed-search-input"
            placeholder="Search posts (or #hashtag)"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          <button type="submit" className="action-btn">Search</button>
          {activeSearch && (
            <button type="button" className="action-btn" onClick={clearSearch}>Clear</button>
          )}
        </form>

        {loading ? (
          <div className="md-loading">Loading feed…</div>
        ) : error ? (
          <div className="md-error-card">
            <div className="md-error-title">Couldn’t load feed</div>
            <div className="md-error-sub">{error}</div>
          </div>
        ) : posts.length === 0 ? (
          <div className="empty-state">
            {activeSearch
              ? 'No posts match this search.'
              : tab === 'following'
                ? 'You don\'t follow anyone yet — switch to For you to discover posts.'
                : 'No posts yet — be the first to share something.'}
          </div>
        ) : (
          <div className="feed-list">
            {posts.map(p => (
              <FeedPostCard
                key={p.id}
                post={p}
                viewerUserId={userId}
                onEdit={openEdit}
                onDelete={handleDelete}
              />
            ))}
          </div>
        )}
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

function extractHashtags(body) {
  const tags = new Set()
  const re = /#(\w+)/g
  let match
  while ((match = re.exec(body)) !== null) {
    tags.add(match[1].toLowerCase())
  }
  return Array.from(tags)
}
