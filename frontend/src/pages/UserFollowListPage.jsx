import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import { getUserById, getFollowers, getFollowing } from '../services/api'
import '../styles/main.css'

const PAGE_SIZE = 12

function displayProfileName(profile) {
  if (!profile) return 'User'
  if (profile.role === 'MENTOR') {
    return [profile.firstName, profile.lastName].filter(Boolean).join(' ')
  }
  return profile.firstName || 'User'
}

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

export default function UserFollowListPage({ mode }) {
  const { id } = useParams()
  const navigate = useNavigate()

  const [profile, setProfile] = useState(null)
  const [profileLoading, setProfileLoading] = useState(true)
  const [profileError, setProfileError] = useState('')

  const [list, setList] = useState([])
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)

  const title = mode === 'following' ? 'Following' : 'Followers'

  useEffect(() => {
    let ignore = false
    setProfileLoading(true)
    setProfileError('')
    getUserById(id)
      .then(data => {
        if (!ignore) setProfile(data)
      })
      .catch(err => {
        if (!ignore) setProfileError(err?.message || 'Failed to load profile.')
      })
      .finally(() => {
        if (!ignore) setProfileLoading(false)
      })
    return () => { ignore = true }
  }, [id])

  useEffect(() => {
    let ignore = false
    setListLoading(true)
    setListError('')
    async function loadList() {
      try {
        const res = mode === 'following'
          ? await getFollowing(id, page, PAGE_SIZE)
          : await getFollowers(id, page, PAGE_SIZE)
        if (ignore) return
        setList(res?.content || [])
        setTotalPages(Math.max(res?.totalPages ?? 1, 1))
      } catch (err) {
        if (ignore) return
        setList([])
        setTotalPages(1)
        setListError(err?.message || `Could not load ${title.toLowerCase()}.`)
      } finally {
        if (!ignore) setListLoading(false)
      }
    }
    loadList()
    return () => { ignore = true }
  }, [id, page, mode, title])

  const subtitle = profileLoading
    ? ''
    : profileError
      ? ''
      : `for ${displayProfileName(profile)}`

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <button
            onClick={() => navigate(-1)}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px', padding: 0, marginBottom: '8px' }}
          >
            ← Back
          </button>
          <div className="page-title">{title}</div>
          {subtitle && <div className="page-sub">{subtitle}</div>}
        </div>
      </div>

      <div className="follow-page">
        {profileError && (
          <div className="md-error-card">
            <div className="md-error-title">Profile unavailable</div>
            <div className="md-error-sub">{profileError}</div>
          </div>
        )}

        {listLoading ? (
          <div className="md-loading">Loading {title.toLowerCase()}…</div>
        ) : listError ? (
          <div className="md-error-card">
            <div className="md-error-title">Couldn’t load {title.toLowerCase()}</div>
            <div className="md-error-sub">{listError}</div>
          </div>
        ) : list.length === 0 ? (
          <div className="empty-state">
            {mode === 'following'
              ? 'Not following anyone yet.'
              : 'No followers yet.'}
          </div>
        ) : (
          <>
            <div className="follow-list">
              {list.map(user => (
                <div className="follow-card" key={user.id}>
                  <div className="follow-card-main">
                    <Avatar
                      src={user.profilePhoto}
                      initials={userInitials(user)}
                      size="md"
                    />
                    <div className="follow-card-meta">
                      <div className="follow-card-name">{displayUserName(user)}</div>
                      <div className="follow-card-role">{user.role === 'MENTOR' ? 'Mentor' : 'Mentee'}</div>
                    </div>
                  </div>
                  <button
                    className="view-profile-btn"
                    type="button"
                    onClick={() => navigate(`/users/${user.id}`)}
                  >
                    View Profile
                  </button>
                </div>
              ))}
            </div>

            {totalPages > 1 && (
              <div className="follow-pagination">
                <button
                  className="action-btn"
                  type="button"
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  Previous
                </button>
                <div className="follow-page-indicator">
                  Page {page + 1} of {totalPages}
                </div>
                <button
                  className="action-btn"
                  type="button"
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </MainLayout>
  )
}
