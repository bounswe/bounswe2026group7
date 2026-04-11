import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import RequestMentorshipModal from '../components/RequestMentorshipModal'
import { getUserById, createMentorshipRequest, getSentMentorshipRequests, getActiveMentorships, getMentorAvailability } from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

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
  const { role } = useAuth()
  const isMentee = role === 'MENTEE'

  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [canSeePhoto, setCanSeePhoto] = useState(false)
  const [availability, setAvailability] = useState([])
  const [modalVisible, setModalVisible] = useState(false)
  const [requestLoading, setRequestLoading] = useState(false)
  const [requestError, setRequestError] = useState('')
  const [requestSent, setRequestSent] = useState(false)

  useEffect(() => {
    getUserById(id)
      .then(data => {
        setProfile(data)
        setLoading(false)
      })
      .catch(err => {
        setError(err.message || 'Failed to load profile.')
        setLoading(false)
      })

    getMentorAvailability(id)
      .then(data => { if (Array.isArray(data) && data.length > 0) setAvailability(data) })
      .catch(() => {})

    if (isMentee) {
      getSentMentorshipRequests()
        .then(data => {
          const sent = (data.content || []).some(r => String(r.mentorId) === String(id))
          setRequestSent(sent)
        })
        .catch(() => {})
    }

    if (!isMentee) {
      // Mentor viewing a mentee: only show photo if there's an active mentorship
      getActiveMentorships()
        .then(data => {
          const active = (data || []).some(m => m.status === 'ACTIVE' && String(m.menteeId) === String(id))
          setCanSeePhoto(active)
        })
        .catch(() => {})
    }
  }, [id, isMentee])

  async function handleSubmitRequest(message) {
    setRequestLoading(true)
    setRequestError('')
    try {
      await createMentorshipRequest({ mentorId: parseInt(id), message })
      setRequestSent(true)
      setModalVisible(false)
    } catch (err) {
      setRequestError(err.message || 'Failed to send request.')
    } finally {
      setRequestLoading(false)
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
    const is403 = error.includes('403') || error.toLowerCase().includes('not allowed') || error.toLowerCase().includes('cannot view')
    return (
      <MainLayout>
        <div style={{ padding: '40px', textAlign: 'center' }}>
          <div style={{ fontSize: '18px', fontWeight: 600, marginBottom: '8px' }}>
            {is403 ? 'Profile not available' : 'User not found'}
          </div>
          <div style={{ color: 'var(--text-muted)', marginBottom: '24px' }}>
            {is403 ? 'You do not have permission to view this profile.' : 'This user does not exist.'}
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

  const avatarSrc = isMentorProfile ? profile.profilePhoto : (canSeePhoto ? profile.profilePhoto : null)

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
            <div className="profile-name">{displayName}</div>
            <div className="profile-role">{isMentorProfile ? 'Mentor' : 'Mentee'}</div>
            {isMentorProfile && profile.maxMenteeCapacity != null && (
              <span className={`availability-badge ${(profile.currentMenteeCount ?? 0) < profile.maxMenteeCapacity ? 'available' : 'full'}`}>
                {(profile.currentMenteeCount ?? 0) < profile.maxMenteeCapacity ? 'Available for Requests' : 'Currently Full'}
              </span>
            )}
          </div>

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
            </div>
          )}

          {isMentee && isMentorProfile && (
            <div style={{ textAlign: 'center' }}>
              <button
                className={`send-request-btn${requestSent ? ' sent' : ''}`}
                disabled={requestSent}
                onClick={() => !requestSent && setModalVisible(true)}
                style={{ width: '100%', height: '44px', fontSize: '14px', fontWeight: 700 }}
              >
                {requestSent ? '✓ Request Sent' : 'Send Request'}
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
                        const day = slot.dayOfWeek.charAt(0) + slot.dayOfWeek.slice(1).toLowerCase()
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
              <ProfileField label="Interests" value={profile.interests} chips />
              <ProfileField label="Skills" value={profile.skills} chips />
            </>
          )}
        </div>
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
    </MainLayout>
  )
}
