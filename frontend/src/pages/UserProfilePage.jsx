import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import RequestMentorshipModal from '../components/RequestMentorshipModal'
import ReportModal from '../components/ReportModal'
import { showTransientToast } from '../utils/toast'
import FeedPostCard from '../components/FeedPostCard'
import {
  getUserById,
  createMentorshipRequest,
  getMentorAvailability,
  followUser,
  unfollowUser,
  getFollowing,
  getUserFeedPosts,
  getMentorRatings,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import { useMentorship } from '../context/MentorshipContext'
import '../styles/main.css'

function formatRatingDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

function ProfileField({ label, value, chips = false }) {
  const isEmpty = !value && value !== 0
  const isEmptyList = Array.isArray(value) && value.length === 0
  if (isEmpty || isEmptyList) return null
  return (
    <div style={{ marginBottom: '18px' }}>
      <div className="section-label" style={{ marginBottom: '8px' }}>{label}</div>
      {chips && Array.isArray(value) ? (
        <div className="profile-chips">
          {value.map(v => <span className="profile-chip" key={v}>{v}</span>)}
        </div>
      ) : Array.isArray(value) ? (
        <div className="profile-chips">
          {value.map(v => <span className="profile-chip" key={v}>{v}</span>)}
        </div>
      ) : (
        <div style={{ fontSize: '14px', color: 'var(--text-mid)', lineHeight: 1.6 }}>
          {String(value)}
        </div>
      )}
    </div>
  )
}

export default function UserProfilePage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { role, userId } = useAuth()
  const isMentee = role === 'MENTEE'
  const isMentor = role === 'MENTOR'
  const isOwnProfile = String(id) === String(userId)

  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [canSeePhoto, setCanSeePhoto] = useState(false)
  const [availability, setAvailability] = useState([])

  const [modalVisible, setModalVisible] = useState(false)
  const [requestLoading, setRequestLoading] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const [requestError, setRequestError] = useState('')

  const {
    sentRequests,
    activeMentorships,
    refresh: refreshMentorships,
    isMentee: userIsMentee
  } = useMentorship()

  const [isFollowing, setIsFollowing] = useState(false)
  const [followLoading, setFollowLoading] = useState(false)

  // Author posts (#546). Lightweight pagination — Show more loads the next
  // page and appends. Empty page is rendered as "No posts yet" rather than
  // hiding the whole section so first-time visitors see what the section is.
  const [posts, setPosts] = useState([])
  const [postsPage, setPostsPage] = useState(0)
  const [postsHasMore, setPostsHasMore] = useState(false)
  const [postsLoading, setPostsLoading] = useState(true)
  const [postsLoadingMore, setPostsLoadingMore] = useState(false)

  // Mentor ratings ("Recent feedback", #556 / backend #534). Only mentors
  // accumulate ratings; mentee profiles skip the section entirely.
  const [ratings, setRatings] = useState([])
  const [ratingsPage, setRatingsPage] = useState(0)
  const [ratingsHasMore, setRatingsHasMore] = useState(false)
  const [ratingsLoading, setRatingsLoading] = useState(true)
  const [ratingsLoadingMore, setRatingsLoadingMore] = useState(false)

  useEffect(() => {
    getUserById(id)
      .then(data => {
        setProfile(data)
        setLoading(false)
      })
      .catch(err => {
        // #359 / backend #579: server returns 403 "Profile is private" when
        // the target's profileVisibility=false and the viewer isn't the
        // owner or an admin. Mentees can also be hidden from each other
        // entirely (req 1.1.2.7). Map both to a friendly private-profile
        // state so the page doesn't render a generic error card.
        const msg = err?.message || ''
        const isPrivate = /private/i.test(msg) || /not visible/i.test(msg)
        if (isPrivate) {
          setError('This profile is private.')
        } else {
          setError(msg || 'Failed to load profile.')
        }
        setLoading(false)
      })

    getMentorAvailability(id)
      .then(data => {
        const slots = Array.isArray(data) ? data : data?.slots ?? []
        if (slots.length > 0) setAvailability(slots)
      })
      .catch(() => {})

    if (userIsMentee && id) {
      // Check if we already have a pending request to this specific mentor
      // This is now handled by derived state from context (sentRequests)
    }

    if (!userIsMentee && id && activeMentorships) {
      // Mentor viewing a mentee: only show photo if there's an active mentorship between them.
      const active = (activeMentorships || []).some(m => m.status === 'ACTIVE' && String(m.menteeId) === String(id))
      setCanSeePhoto(active)
    }
  }, [id, userIsMentee, activeMentorships])

  useEffect(() => {
    if (!id) return
    let ignore = false
    setPostsLoading(true)
    setPosts([])
    setPostsPage(0)
    getUserFeedPosts(id, 0, 10)
      .then(page => {
        if (ignore) return
        const items = page?.content ?? page ?? []
        setPosts(items)
        setPostsHasMore(page && page.last === false)
      })
      .catch(() => { if (!ignore) setPosts([]) })
      .finally(() => { if (!ignore) setPostsLoading(false) })
    return () => { ignore = true }
  }, [id])

  async function loadMorePosts() {
    if (postsLoadingMore) return
    const next = postsPage + 1
    setPostsLoadingMore(true)
    try {
      const page = await getUserFeedPosts(id, next, 10)
      const items = page?.content ?? page ?? []
      setPosts(prev => [...prev, ...items])
      setPostsPage(next)
      setPostsHasMore(page && page.last === false)
    } catch {
      /* swallow — keep the existing list, user can retry */
    } finally {
      setPostsLoadingMore(false)
    }
  }

  // Mentor ratings ("Recent feedback") — only fetch once profile is loaded
  // and the viewer is on a mentor profile. Mentees never accumulate ratings.
  useEffect(() => {
    if (!id || !profile || profile.role !== 'MENTOR') {
      setRatingsLoading(false)
      return undefined
    }
    let ignore = false
    setRatingsLoading(true)
    setRatings([])
    setRatingsPage(0)
    getMentorRatings(id, 0, 10)
      .then(page => {
        if (ignore) return
        const items = page?.content ?? page ?? []
        setRatings(items)
        setRatingsHasMore(page && page.last === false)
      })
      .catch(() => { if (!ignore) setRatings([]) })
      .finally(() => { if (!ignore) setRatingsLoading(false) })
    return () => { ignore = true }
  }, [id, profile])

  async function loadMoreRatings() {
    if (ratingsLoadingMore) return
    const next = ratingsPage + 1
    setRatingsLoadingMore(true)
    try {
      const page = await getMentorRatings(id, next, 10)
      const items = page?.content ?? page ?? []
      setRatings(prev => [...prev, ...items])
      setRatingsPage(next)
      setRatingsHasMore(page && page.last === false)
    } catch {
      /* swallow — keep the existing list, user can retry */
    } finally {
      setRatingsLoadingMore(false)
    }
  }

  useEffect(() => {
    if (!userId || !id || !profile) return
    if (String(userId) === String(id)) return
    if (role === 'MENTEE' && profile.role === 'MENTEE') return
    let ignore = false
    async function loadFollowState() {
      try {
        const page = await getFollowing(userId, 0, 200)
        const list = page?.content || []
        if (!ignore) {
          setIsFollowing(list.some(u => String(u.id) === String(id)))
        }
      } catch {
        // ignore follow-state failures; toggle will still work
      }
    }
    loadFollowState()
    return () => { ignore = true }
  }, [userId, id, profile, role])

  async function handleSubmitRequest(message) {
    setRequestLoading(true)
    setRequestError('')
    try {
      await createMentorshipRequest({ mentorId: parseInt(id), message })
      refreshMentorships() // Refresh context to update sentRequests and pending counts
      setModalVisible(false)
    } catch (err) {
      setRequestError(err.message || 'Failed to send request.')
    } finally {
      setRequestLoading(false)
    }
  }

  async function toggleFollow() {
    if (followLoading) return
    setFollowLoading(true)
    try {
      if (isFollowing) {
        await unfollowUser(id)
        setIsFollowing(false)
        setProfile(prev => prev ? { ...prev, followerCount: Math.max(0, (prev.followerCount ?? 0) - 1) } : prev)
      } else {
        await followUser(id)
        setIsFollowing(true)
        setProfile(prev => prev ? { ...prev, followerCount: (prev.followerCount ?? 0) + 1 } : prev)
      }
    } catch (err) {
      window.alert(err?.message || 'Failed to update follow status')
    } finally {
      setFollowLoading(false)
    }
  }

  if (loading) {
    return (
      <MainLayout>
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>Loading profile...</div>
      </MainLayout>
    )
  }

  if (error) {
    const lower = error.toLowerCase()
    const isPrivate = lower.includes('private')
    const is403 = !isPrivate && (error.includes('403') || lower.includes('not allowed') || lower.includes('cannot view') || lower.includes('not visible'))
    let title = 'User not found'
    let body = 'This user does not exist.'
    if (isPrivate) {
      title = 'This profile is private'
      body = 'The owner has chosen to hide their profile from other users.'
    } else if (is403) {
      title = 'Profile not available'
      body = 'You do not have permission to view this profile.'
    }
    return (
      <MainLayout>
        <div style={{ padding: '40px', textAlign: 'center' }}>
          <div style={{ fontSize: '18px', fontWeight: 600, marginBottom: '8px' }}>
            {title}
          </div>
          <div style={{ color: 'var(--text-muted)', marginBottom: '24px' }}>
            {body}
          </div>
          <button className="action-btn" onClick={() => navigate(-1)}>Go Back</button>
        </div>
      </MainLayout>
    )
  }

  const isMentorProfile = profile.role === 'MENTOR'
  const initials = [profile.firstName, isMentorProfile ? profile.lastName : null]
    .filter(Boolean).map(w => w[0]).join('').toUpperCase() || '?'

  // Per req 1.1.2.6: do not show mentee lastName or profilePhoto to mentors
  const displayName = isMentorProfile
    ? [profile.firstName, profile.lastName].filter(Boolean).join(' ')
    : profile.firstName

  const avatarSrc = isMentorProfile
    ? profile.profilePhoto
    : (canSeePhoto ? profile.profilePhoto : null)

  const followerCount = profile.followerCount ?? 0
  const followingCount = profile.followingCount ?? 0
  const canFollow = !isOwnProfile && !(role === 'MENTEE' && profile.role === 'MENTEE')
  const canViewFollowGraph = !(role === 'MENTEE' && profile.role === 'MENTEE' && !isOwnProfile)

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
          <div className="page-title">{displayName}</div>
        </div>
      </div>

      <div className="profile-layout">
        {/* Left — avatar + summary */}
        <div>
          <div className="profile-card-hero">
            <Avatar src={avatarSrc} initials={initials} size="lg" className="profile-avatar-lg" />
            <div className="profile-name" data-testid="user-profile-name">{displayName}</div>
            <div className="profile-role">{isMentorProfile ? 'Mentor' : 'Mentee'}</div>
            {isMentorProfile && profile.maxMenteeCapacity != null && (
              <span className={`availability-badge ${(profile.currentMenteeCount ?? 0) < profile.maxMenteeCapacity ? 'available' : 'full'}`}>
                {(profile.currentMenteeCount ?? 0) < profile.maxMenteeCapacity ? 'Available for Requests' : 'Currently Full'}
              </span>
            )}
          </div>

          <div className="profile-follow-stats">
            <button
              type="button"
              className="profile-follow-stat"
              onClick={() => canViewFollowGraph && navigate(`/users/${id}/followers`)}
              disabled={!canViewFollowGraph}
            >
              <div className="profile-follow-num">{followerCount}</div>
              <div className="profile-follow-label">Followers</div>
            </button>
            <button
              type="button"
              className="profile-follow-stat"
              onClick={() => canViewFollowGraph && navigate(`/users/${id}/following`)}
              disabled={!canViewFollowGraph}
            >
              <div className="profile-follow-num">{followingCount}</div>
              <div className="profile-follow-label">Following</div>
            </button>
          </div>

          {canFollow && (
            <button
              type="button"
              className={`follow-btn${isFollowing ? ' follow-btn--active' : ''}`}
              onClick={toggleFollow}
              disabled={followLoading}
            >
              {followLoading ? 'Updating...' : isFollowing ? 'Unfollow' : 'Follow'}
            </button>
          )}

          {isMentorProfile && (
            <div className="profile-stats-row">
              <div className="psr-item">
                <div className="psr-num">{(profile.interests || []).length}</div>
                <div className="psr-lbl">Topics</div>
              </div>
              <div className="psr-item">
                <div className="psr-num">{profile.mentorshipDuration ?? '—'}</div>
                <div className="psr-lbl">Months</div>
              </div>
              <div className="psr-item">
                <div className="psr-num">{profile.currentMenteeCount ?? 0}/{profile.maxMenteeCapacity ?? '∞'}</div>
                <div className="psr-lbl">Mentees</div>
              </div>
              <div className="psr-item" title={profile.ratingCount ? `${profile.ratingCount} rating${profile.ratingCount === 1 ? '' : 's'}` : 'No ratings yet'}>
                <div className="psr-num">
                  {profile.averageRating != null
                    ? `★ ${profile.averageRating.toFixed(1)}`
                    : '—'}
                </div>
                <div className="psr-lbl">
                  {profile.ratingCount
                    ? `${profile.ratingCount} rating${profile.ratingCount === 1 ? '' : 's'}`
                    : 'Rating'}
                </div>
              </div>
            </div>
          )}

          {userIsMentee && isMentorProfile && (
            <div style={{ textAlign: 'center' }}>
              {(() => {
                const isPending = (sentRequests || []).some(r => String(r.mentorId) === String(id) && r.status === 'PENDING')
                const hasActiveMentor = (activeMentorships || []).some(m => m.status === 'ACTIVE')
                const isFull = profile.maxMenteeCapacity != null && (profile.currentMenteeCount ?? 0) >= profile.maxMenteeCapacity

                const btnDisabled = isPending || hasActiveMentor || isFull
                const btnLabel = isPending ? '✓ Request Sent' : isFull ? 'At Capacity' : hasActiveMentor ? 'Already Mentored' : 'Send Request'

                return (
                  <>
                    <button
                      className={`send-request-btn${isPending ? ' sent' : ''}${hasActiveMentor && !isPending ? ' locked' : ''}`}
                      disabled={btnDisabled}
                      onClick={() => !btnDisabled && setModalVisible(true)}
                      style={{ width: '100%', height: '44px', fontSize: '14px', fontWeight: 700 }}
                    >
                      {btnLabel}
                    </button>
                    {/* #128: mentees can report mentors. Backend rejects self-reports
                        and duplicates; UI surfaces those errors inside the modal. */}
                    <button
                      type="button"
                      onClick={() => setReportOpen(true)}
                      style={{
                        marginTop: '8px',
                        background: 'transparent',
                        border: 'none',
                        color: 'var(--text-muted)',
                        fontSize: '12px',
                        fontWeight: 500,
                        cursor: 'pointer',
                        textDecoration: 'underline',
                      }}
                    >
                      Report this mentor
                    </button>
                  </>
                )
              })()}
            </div>
          )}

          {/* Mentor-to-mentor messaging entry point — only when viewer and target are
              both mentors AND the viewer isn't looking at their own profile. */}
          {isMentor && isMentorProfile && !isOwnProfile && (
            <div style={{ textAlign: 'center' }}>
              <button
                className="send-request-btn"
                onClick={() => navigate(`/messages?peerId=${id}`)}
                style={{ width: '100%', height: '44px', fontSize: '14px', fontWeight: 700 }}
              >
                Send Message
              </button>
            </div>
          )}
        </div>

        {/* Right — profile details */}
        <div className="card">
          {isMentorProfile ? (
            <>
              <ProfileField label="Bio" value={profile.bio} />
              <ProfileField label="Field" value={profile.field} />
              <ProfileField label="Expertise" value={profile.expertise} />
              <ProfileField label="Affiliation" value={profile.affiliation} />
              <ProfileField label="Interests" value={profile.interests} chips />

              <div className="divider" />

              <ProfileField label="Mentoring Goals" value={profile.mentoringGoals} />
              <ProfileField label="Preferred Mentee Major" value={profile.preferredMenteeMajor} />
              <ProfileField label="Preferred Mentee Skills" value={profile.preferredMenteeSkills} chips />

              {availability.length > 0 && (
                <>
                  <div className="divider" />
                  <div style={{ marginBottom: '18px' }}>
                    <div className="section-label" style={{ marginBottom: '10px' }}>Weekly Availability</div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      {availability.map((slot, i) => {
                        const fmt = t => {
                          if (!t) return ''
                          if (typeof t === 'string') return t.slice(0, 5)
                          return `${String(t.hour).padStart(2,'0')}:${String(t.minute).padStart(2,'0')}`
                        }
                        const day = slot.dayOfWeek
                          ? slot.dayOfWeek.charAt(0) + slot.dayOfWeek.slice(1).toLowerCase()
                          : ''
                        return (
                          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '14px', color: 'var(--text-mid)' }}>
                            <span style={{ width: '90px', fontWeight: 500, color: 'var(--text-main)' }}>{day}</span>
                            <span>{fmt(slot.startTime)} – {fmt(slot.endTime)}</span>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                </>
              )}
            </>
          ) : (
            <>
              {/* Per req 1.1.2.6: hide lastName and profilePhoto for unmatched mentees */}
              <ProfileField label="Background" value={profile.backgroundInfo} />
              <ProfileField label="Goals" value={profile.goals} />
              <ProfileField label="Affiliation" value={profile.affiliation} />
              <ProfileField label="Interests" value={profile.interests} chips />
              <ProfileField label="Skills" value={profile.skills} chips />
            </>
          )}
        </div>
      </div>

      {isMentorProfile && (
        <div className="card" style={{ marginTop: '16px' }}>
          <div className="section-label" style={{ marginBottom: '12px' }}>Recent Feedback</div>
          {ratingsLoading ? (
            <div className="md-loading">Loading feedback…</div>
          ) : ratings.length === 0 ? (
            <div className="empty-state" style={{ padding: '20px 0' }}>No ratings yet.</div>
          ) : (
            <ul className="rating-list">
              {ratings.map(r => (
                <li key={r.id} className="rating-card">
                  <div className="rating-card-head">
                    <span className="md-rating-stars md-rating-stars--readonly" aria-label={`${r.score} out of 5 stars`}>
                      {[1, 2, 3, 4, 5].map(n => (
                        <span
                          key={n}
                          className={`md-rating-star${r.score >= n ? ' md-rating-star--filled' : ''}`}
                        >★</span>
                      ))}
                    </span>
                    <span className="rating-card-date">{formatRatingDate(r.createdAt)}</span>
                  </div>
                  {r.comment && (
                    <p className="rating-card-comment">"{r.comment}"</p>
                  )}
                </li>
              ))}
              {ratingsHasMore && (
                <button
                  type="button"
                  className="action-btn"
                  onClick={loadMoreRatings}
                  disabled={ratingsLoadingMore}
                  style={{ alignSelf: 'center', marginTop: '8px' }}
                >
                  {ratingsLoadingMore ? 'Loading…' : 'Show more'}
                </button>
              )}
            </ul>
          )}
        </div>
      )}

      <div className="card" style={{ marginTop: '16px' }}>
        <div className="section-label" style={{ marginBottom: '12px' }}>Posts</div>
        {postsLoading ? (
          <div className="md-loading">Loading posts…</div>
        ) : posts.length === 0 ? (
          <div className="empty-state" style={{ padding: '20px 0' }}>No posts yet.</div>
        ) : (
          <div className="feed-list">
            {posts.map(p => (
              <FeedPostCard
                key={p.id}
                post={p}
                viewerUserId={userId}
              />
            ))}
            {postsHasMore && (
              <button
                type="button"
                className="action-btn"
                onClick={loadMorePosts}
                disabled={postsLoadingMore}
                style={{ alignSelf: 'center', marginTop: '8px' }}
              >
                {postsLoadingMore ? 'Loading…' : 'Show more'}
              </button>
            )}
          </div>
        )}
      </div>

      {isMentee && isMentorProfile && (
        <RequestMentorshipModal
          visible={modalVisible}
          onClose={() => { setModalVisible(false); setRequestError('') }}
          onSubmit={handleSubmitRequest}
          loading={requestLoading}
          error={requestError}
          mentorName={displayName}
          defaultMessage=""
        />
      )}

      <ReportModal
        open={reportOpen}
        targetType="USER"
        targetId={id}
        targetLabel={displayName || 'this user'}
        onClose={() => setReportOpen(false)}
        onSubmitted={() => showTransientToast('Report submitted. Admins will review it.')}
      />
    </MainLayout>
  )
}
