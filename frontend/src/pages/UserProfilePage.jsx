import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import RequestMentorshipModal from '../components/RequestMentorshipModal'
import { getUserById, createMentorshipRequest, getSentMentorshipRequests } from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

function ProfileField({ label, value }) {
  if (!value && value !== 0) return null
  return (
    <div style={{ marginBottom: '16px' }}>
      <div style={{ fontSize: '11px', fontWeight: 700, letterSpacing: '1px', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: '4px' }}>
        {label}
      </div>
      <div style={{ fontSize: '14px', color: 'var(--text-mid)', lineHeight: 1.6 }}>
        {Array.isArray(value) ? value.join(', ') : String(value)}
      </div>
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

    if (isMentee) {
      getSentMentorshipRequests()
        .then(data => {
          const sent = (data.content || []).some(r => String(r.mentorId) === String(id))
          setRequestSent(sent)
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

  const avatarSrc = isMentorProfile ? profile.profilePhoto : null

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
            {avatarSrc
              ? <img src={avatarSrc} alt={initials} className="profile-avatar-lg" style={{ objectFit: 'cover' }} />
              : <div className="profile-avatar-lg">{initials}</div>
            }
            <div className="profile-name">{displayName}</div>
            <div className="profile-role">{isMentorProfile ? 'Mentor' : 'Mentee'}</div>
          </div>

          {isMentee && isMentorProfile && (
            <div style={{ marginTop: '16px', textAlign: 'center' }}>
              <button
                className={`send-request-btn${requestSent ? ' sent' : ''}`}
                disabled={requestSent}
                onClick={() => !requestSent && setModalVisible(true)}
              >
                {requestSent ? 'Request Sent' : 'Send Request'}
              </button>
            </div>
          )}
        </div>

        {/* Right — profile details */}
        <div className="card">
          {isMentorProfile ? (
            <>
              <div className="section-label">About</div>
              <ProfileField label="Bio" value={profile.bio} />
              <ProfileField label="Field" value={profile.field} />
              <ProfileField label="Expertise" value={profile.expertise} />
              <ProfileField label="Affiliation" value={profile.affiliation} />
              <ProfileField label="Interests" value={profile.interests} />

              <div className="divider" />
              <div className="section-label">Mentoring</div>
              <ProfileField label="Mentoring Goals" value={profile.mentoringGoals} />
              <ProfileField label="Preferred Mentee Major" value={profile.preferredMenteeMajor} />
              <ProfileField label="Preferred Mentee Skills" value={profile.preferredMenteeSkills} />
              <ProfileField label="Mentorship Duration" value={profile.mentorshipDuration ? `${profile.mentorshipDuration} months` : null} />
              <ProfileField label="Capacity" value={
                profile.maxMenteeCapacity != null
                  ? `${profile.currentMenteeCount ?? 0} / ${profile.maxMenteeCapacity} mentees`
                  : null
              } />
            </>
          ) : (
            <>
              <div className="section-label">About</div>
              {/* Per req 1.1.2.6: hide lastName and profilePhoto for unmatched mentees */}
              <ProfileField label="Background" value={profile.backgroundInfo} />
              <ProfileField label="Goals" value={profile.goals} />
              <ProfileField label="Interests" value={profile.interests} />
              <ProfileField label="Skills" value={profile.skills} />
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
