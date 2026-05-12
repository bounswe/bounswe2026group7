import { useEffect, useState, useCallback } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import FeedPostCard from '../components/FeedPostCard'
import FeedImageUploader from '../components/FeedImageUploader'
import Avatar from '../components/Avatar'
import {
  getForYouFeed,
  getFollowingFeed,
  searchFeed,
  createFeedPost,
  updateFeedPost,
  deleteFeedPost,
  getFollowRecommendations,
  followUser,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

const TABS = [
  { key: 'for-you', label: 'For you' },
  { key: 'following', label: 'Following' },
]

function displayUserName(user) {
  if (!user) return 'User'
  if (user.role === 'MENTOR') {
    return [user.firstName, user.lastName].filter(Boolean).join(' ')
  }
  return user.firstName || 'User'
}

function userInitials(user) {
  if (!user) return '?'
  const parts = user.role === 'MENTOR'
    ? [user.firstName, user.lastName]
    : [user.firstName]
  const initials = parts.filter(Boolean).map(w => w[0]).join('')
  return initials ? initials.toUpperCase().slice(0, 2) : '?'
}

function formatFollowFactor(factor) {
  if (!factor) return ''
  if (factor.startsWith('shared-interest:')) {
    const label = factor.replace('shared-interest:', '')
    return `Shared interest: ${label}`
  }
  if (factor.startsWith('followed-by-')) {
    const match = factor.match(/followed-by-(\d+)-of-your-follows/)
    if (match) return `Followed by ${match[1]} of your follows`
  }
  return factor
}

export default function FeedPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const { userId } = useAuth()

  const tab = TABS.some(t => t.key === params.get('tab')) ? params.get('tab') : 'for-you'
  const [posts, setPosts] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const [recommendations, setRecommendations] = useState([])
  const [recLoading, setRecLoading] = useState(false)
  const [recError, setRecError] = useState(null)

  const [searchQuery, setSearchQuery] = useState('')
  const [activeSearch, setActiveSearch] = useState(null) // { q?, hashtag? } or null

  // Minimal compose — full UI lands in #353
  const [composeOpen, setComposeOpen] = useState(false)
  const [composeBody, setComposeBody] = useState('')
  const [composeAttachments, setComposeAttachments] = useState([]) // AttachmentSummary[]
  const [composeBusy, setComposeBusy] = useState(false)
  const [composeError, setComposeError] = useState(null)

  // Edit modal state
  const [editing, setEditing] = useState(null) // post object or null
  const [editBody, setEditBody] = useState('')
  const [editHashtags, setEditHashtags] = useState('')
  const [editAttachments, setEditAttachments] = useState([])
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

  const loadRecommendations = useCallback(async () => {
    setRecLoading(true)
    setRecError(null)
    try {
      const page = await getFollowRecommendations(0, 8)
      const list = (page?.content || []).map(r => ({ ...r, isFollowing: false, busy: false }))
      setRecommendations(list)
    } catch (err) {
      setRecError(err?.message || 'Failed to load suggestions')
      setRecommendations([])
    } finally {
      setRecLoading(false)
    }
  }, [])

  useEffect(() => {
    if (tab !== 'following' || activeSearch || loading || posts.length > 0) return
    loadRecommendations()
  }, [tab, activeSearch, loading, posts.length, loadRecommendations])

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
      const created = await createFeedPost({
        body,
        hashtags: extractHashtags(body),
        attachmentIds: composeAttachments.map(a => a.id),
      })
      setComposeBody('')
      setComposeAttachments([])
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
    setEditAttachments(Array.isArray(post.attachments) ? post.attachments : [])
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
      const updated = await updateFeedPost(editing.id, {
        body,
        hashtags: tags,
        attachmentIds: editAttachments.map(a => a.id),
      })
      setPosts(prev => prev.map(p => p.id === updated.id
        ? { ...p, body: updated.body, hashtags: updated.hashtags, attachments: updated.attachments, isEdited: true }
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

  async function handleFollowSuggestion(userId) {
    setRecommendations(prev => prev.map(r => r.id === userId ? { ...r, busy: true } : r))
    try {
      await followUser(userId)
      setRecommendations(prev => prev.map(r => r.id === userId
        ? { ...r, busy: false, isFollowing: true }
        : r))
    } catch (err) {
      setRecommendations(prev => prev.map(r => r.id === userId ? { ...r, busy: false } : r))
      window.alert(err?.message || 'Failed to follow user')
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
            <FeedImageUploader
              value={composeAttachments}
              onChange={setComposeAttachments}
              disabled={composeBusy}
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
          activeSearch ? (
            <div className="empty-state">No posts match this search.</div>
          ) : tab === 'following' ? (
            <div className="empty-following">
              <div className="empty-state">
                You do not follow anyone yet. Find people to follow to populate your feed.
              </div>

              <div className="follow-suggested">
                <div className="follow-suggested-header">
                  <div className="follow-suggested-title">Suggested to follow</div>
                  {recLoading && <div className="follow-suggested-sub">Loading…</div>}
                  {!recLoading && recError && <div className="follow-suggested-sub">{recError}</div>}
                </div>

                {recLoading ? (
                  <div className="md-loading">Loading suggestions…</div>
                ) : recError ? null : recommendations.length === 0 ? (
                  <div className="empty-state">No suggestions available right now.</div>
                ) : (
                  <div className="follow-suggested-rail">
                    {recommendations.map(rec => (
                      <div className="follow-suggested-card" key={rec.id}>
                        <div className="follow-suggested-main">
                          <Avatar
                            src={rec.profilePhoto}
                            initials={userInitials(rec)}
                            size="md"
                          />
                          <div>
                            <div className="follow-suggested-name">{displayUserName(rec)}</div>
                            <div className="follow-suggested-role">{rec.role === 'MENTOR' ? 'Mentor' : 'Mentee'}</div>
                          </div>
                        </div>
                        {Array.isArray(rec.factors) && rec.factors.length > 0 && (
                          <div className="follow-suggested-factors">
                            {rec.factors.slice(0, 2).map((f, i) => (
                              <span className="follow-suggested-factor" key={`${rec.id}-f-${i}`}>
                                {formatFollowFactor(f)}
                              </span>
                            ))}
                          </div>
                        )}
                        <div className="follow-suggested-actions">
                          <button
                            className="view-profile-btn"
                            type="button"
                            onClick={() => navigate(`/users/${rec.id}`)}
                          >
                            View Profile
                          </button>
                          <button
                            className={`follow-btn${rec.isFollowing ? ' follow-btn--active' : ''}`}
                            type="button"
                            onClick={() => !rec.isFollowing && !rec.busy && handleFollowSuggestion(rec.id)}
                            disabled={rec.busy || rec.isFollowing}
                          >
                            {rec.busy ? 'Updating...' : rec.isFollowing ? 'Unfollow' : 'Follow'}
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="empty-state">No posts yet — be the first to share something.</div>
          )
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

function extractHashtags(body) {
  const tags = new Set()
  const re = /#(\w+)/g
  let match
  while ((match = re.exec(body)) !== null) {
    tags.add(match[1].toLowerCase())
  }
  return Array.from(tags)
}
