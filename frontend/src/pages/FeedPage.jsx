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
  restoreFeedPost,
  getFollowRecommendations,
  followUser,
  getTrendingHashtags,
  markFeedRead,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import { showUndoToast } from '../utils/toast'
import useFeedSubscription from '../hooks/useFeedSubscription'
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

  // Trending hashtags (#545). Backend refreshes hourly so a single fetch on
  // mount is enough; per-tab refetch would just thrash the cache without new
  // data. Hidden during active search to avoid double-filtering the view.
  const [trending, setTrending] = useState([])

  // Live-feed buffer (#356). When the STOMP push arrives while the user is
  // scrolled away from the top or on a different tab, we surface a "X new
  // posts" pill instead of yanking the list. Clicking the pill reloads.
  // We keep just the count, not the slim payloads, since the reload path
  // fetches the canonical full FeedPostListItem from the REST endpoint.
  const [newPostsCount, setNewPostsCount] = useState(0)

  const [searchQuery, setSearchQuery] = useState('')
  const [activeSearch, setActiveSearch] = useState(null) // { q?, hashtag?, since?, until?, lang? } or null
  // #543: advanced filters. Open state is a toggle; values persist across
  // searches so users don't lose a date range when they refine the keyword.
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [filterSince, setFilterSince] = useState('')
  const [filterUntil, setFilterUntil] = useState('')
  const [filterLang, setFilterLang] = useState('')

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

  // #356: subscribe to /topic/feed.{userId} so a new post from someone the
  // user follows surfaces a "X new posts" pill without a refresh. The pill
  // only fires on the For-You / Following tabs without an active search —
  // a hashtag-search view shouldn't pretend a new post arrived for it.
  useFeedSubscription(userId, {
    onPost: () => {
      if (activeSearch || loading) return
      setNewPostsCount(c => c + 1)
    },
    onShare: () => {
      if (activeSearch || loading) return
      setNewPostsCount(c => c + 1)
    },
  })

  // #356: mark the feed read whenever the page loads with results. Cheap on
  // backend (single UPDATE) and idempotent, so re-firing on tab change is
  // harmless. Triggers only when posts > 0 — empty feed has nothing to read.
  useEffect(() => {
    if (loading || activeSearch || posts.length === 0) return
    markFeedRead().catch(() => { /* swallow — best-effort */ })
  }, [loading, activeSearch, posts.length])

  function handleClickNewPosts() {
    setNewPostsCount(0)
    reload()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  useEffect(() => {
    let cancelled = false
    getTrendingHashtags(10)
      .then(list => { if (!cancelled && Array.isArray(list)) setTrending(list) })
      .catch(() => { /* silent — empty rail is the no-data state */ })
    return () => { cancelled = true }
  }, [])

  const loadRecommendations = useCallback(async () => {
    setRecLoading(true)
    setRecError(null)
    try {
      const page = await getFollowRecommendations(0, 8)
      const list = (page?.content || []).map(r => ({ ...r, busy: false }))
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
    // Date inputs are LocalDate (YYYY-MM-DD). Backend wants ISO-8601 datetime.
    // since = start of day inclusive, until = start of next day exclusive
    // (matches backend's half-open semantics).
    const sinceIso = filterSince ? new Date(`${filterSince}T00:00:00Z`).toISOString() : undefined
    const untilIso = filterUntil ? (() => {
      const d = new Date(`${filterUntil}T00:00:00Z`)
      d.setUTCDate(d.getUTCDate() + 1)
      return d.toISOString()
    })() : undefined
    const lang = filterLang || undefined

    const hasKeyword = trimmed.length > 0
    const hasHashtag = hasKeyword && /^#?\w+$/.test(trimmed) && trimmed.startsWith('#')
    const hasAnyFilter = hasKeyword || sinceIso || untilIso || lang
    if (!hasAnyFilter) {
      setActiveSearch(null)
      return
    }
    const next = { since: sinceIso, until: untilIso, lang }
    if (hasHashtag) next.hashtag = trimmed.slice(1)
    else if (hasKeyword) next.q = trimmed
    setActiveSearch(next)
  }

  function clearSearch() {
    setSearchQuery('')
    setFilterSince('')
    setFilterUntil('')
    setFilterLang('')
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
  // Backend soft-deletes posts (#487 / #544); restoring is possible within
  // a 30-day window via POST /api/feed/posts/{id}/restore. The confirm copy
  // reflects that, and a successful delete drops an undo toast that calls
  // restoreFeedPost on click.
  async function handleDelete(post) {
    if (!post) return
    const confirmed = window.confirm(
      'Hide this post? You can restore it within 30 days from the toast below.'
    )
    if (!confirmed) return
    try {
      await deleteFeedPost(post.id)
      setPosts(prev => prev.filter(p => p.id !== post.id))
      showUndoToast('Post hidden.', async () => {
        try {
          const restored = await restoreFeedPost(post.id)
          // Reinsert at the original index if we still have the list around.
          setPosts(prev => [restored, ...prev])
        } catch (err) {
          window.alert(err?.message || 'Failed to restore post')
        }
      })
    } catch (err) {
      window.alert(err?.message || 'Failed to delete post')
    }
  }

  async function handleFollowSuggestion(userId) {
    setRecommendations(prev => prev.map(r => r.id === userId ? { ...r, busy: true } : r))
    try {
      await followUser(userId)
      // #512: drop the followed user from the rail entirely. Keeping the card
      // with the label flipped to "Unfollow" leaves a dead-looking button
      // (the original click handler short-circuits on isFollowing=true). The
      // recommendation no longer applies once we're following, so removal is
      // the cleanest UX.
      setRecommendations(prev => prev.filter(r => r.id !== userId))
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
          <button
            type="button"
            className={`action-btn${filtersOpen || filterSince || filterUntil || filterLang ? ' action-btn--active' : ''}`}
            onClick={() => setFiltersOpen(v => !v)}
            aria-expanded={filtersOpen}
            aria-controls="feed-filters-panel"
          >
            Filters{(filterSince || filterUntil || filterLang) ? ' •' : ''}
          </button>
          {activeSearch && (
            <button type="button" className="action-btn" onClick={clearSearch}>Clear</button>
          )}
        </form>

        {filtersOpen && (
          <div id="feed-filters-panel" className="feed-filters">
            <div className="feed-filter-field">
              <label htmlFor="feed-filter-since">From</label>
              <input
                id="feed-filter-since"
                type="date"
                className="feed-filter-input"
                value={filterSince}
                max={filterUntil || undefined}
                onChange={(e) => setFilterSince(e.target.value)}
              />
            </div>
            <div className="feed-filter-field">
              <label htmlFor="feed-filter-until">To</label>
              <input
                id="feed-filter-until"
                type="date"
                className="feed-filter-input"
                value={filterUntil}
                min={filterSince || undefined}
                onChange={(e) => setFilterUntil(e.target.value)}
              />
            </div>
            <div className="feed-filter-field">
              <label htmlFor="feed-filter-lang">Language</label>
              <select
                id="feed-filter-lang"
                className="feed-filter-input"
                value={filterLang}
                onChange={(e) => setFilterLang(e.target.value)}
              >
                <option value="">Any</option>
                <option value="en">English</option>
                <option value="tr">Türkçe</option>
                <option value="de">Deutsch</option>
                <option value="fr">Français</option>
                <option value="es">Español</option>
              </select>
            </div>
            <button
              type="button"
              className="action-btn"
              onClick={() => {
                setFilterSince('')
                setFilterUntil('')
                setFilterLang('')
              }}
              disabled={!filterSince && !filterUntil && !filterLang}
            >
              Reset
            </button>
          </div>
        )}

        {!activeSearch && trending.length > 0 && (
          <div className="feed-trending" aria-label="Trending hashtags">
            <span className="feed-trending-label">Trending</span>
            <div className="feed-trending-rail">
              {trending.map(h => (
                <button
                  key={h.tag}
                  type="button"
                  className="feed-trending-chip"
                  onClick={() => {
                    setSearchQuery(`#${h.tag}`)
                    setActiveSearch({ hashtag: h.tag })
                  }}
                  title={`${h.postCount ?? 0} post${h.postCount === 1 ? '' : 's'} in the last 24h`}
                >
                  <span className="feed-trending-tag">#{h.tag}</span>
                  {h.postCount != null && (
                    <span className="feed-trending-count">{h.postCount}</span>
                  )}
                </button>
              ))}
            </div>
          </div>
        )}

        {newPostsCount > 0 && !loading && !activeSearch && (
          <button
            type="button"
            className="feed-new-pill"
            onClick={handleClickNewPosts}
            aria-live="polite"
          >
            {newPostsCount} new post{newPostsCount === 1 ? '' : 's'} · click to refresh
          </button>
        )}

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
                            className="follow-btn"
                            type="button"
                            onClick={() => !rec.busy && handleFollowSuggestion(rec.id)}
                            disabled={rec.busy}
                          >
                            {rec.busy ? 'Following…' : 'Follow'}
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
