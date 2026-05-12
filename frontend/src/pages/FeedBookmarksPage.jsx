import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import FeedPostCard from '../components/FeedPostCard'
import { getMyBookmarks } from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

/**
 * /feed/bookmarks — the viewer's bookmarked feed posts (#340).
 *
 * Every visible post is bookmarked by definition, so each card is rendered
 * with `initialBookmarked={true}`. When the user removes a bookmark from
 * here we drop the row in place via `onBookmarkToggled` rather than
 * leaving a now-stale card sitting around.
 */
export default function FeedBookmarksPage() {
  const navigate = useNavigate()
  const { userId } = useAuth()

  const [posts, setPosts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getMyBookmarks(0, 30)
      .then(page => { if (!cancelled) setPosts(page?.content || []) })
      .catch(err => { if (!cancelled) setError(err?.message || 'Failed to load bookmarks') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  function handleBookmarkToggled(post, state) {
    // If the user just removed the bookmark, drop the row from the list.
    if (state && state.viewerHasBookmarked === false) {
      setPosts(prev => prev.filter(p => p.id !== post.id))
    }
  }

  return (
    <MainLayout>
      <div className="feed-page" data-testid="feed-bookmarks-page">
        <div className="page-header">
          <div>
            <button
              onClick={() => navigate('/feed')}
              style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px', padding: 0, marginBottom: '8px' }}
            >
              ← Back to feed
            </button>
            <div className="page-title">Bookmarks</div>
            <div className="page-sub">Posts you saved for later</div>
          </div>
        </div>

        {loading ? (
          <div className="md-loading">Loading bookmarks…</div>
        ) : error ? (
          <div className="md-error-card">
            <div className="md-error-title">Couldn’t load bookmarks</div>
            <div className="md-error-sub">{error}</div>
          </div>
        ) : posts.length === 0 ? (
          <div className="empty-state" data-testid="feed-bookmarks-empty">
            You haven’t bookmarked any posts yet. Tap the bookmark icon on a post in your feed to save it here.
          </div>
        ) : (
          <div className="feed-list" data-testid="feed-bookmarks-list">
            {posts.map(p => (
              <FeedPostCard
                key={p.id}
                post={p}
                viewerUserId={userId}
                initialBookmarked
                onBookmarkToggled={(state) => handleBookmarkToggled(p, state)}
              />
            ))}
          </div>
        )}
      </div>
    </MainLayout>
  )
}
