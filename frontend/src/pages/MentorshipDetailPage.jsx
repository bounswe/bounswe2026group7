import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import { getMentorshipById, getUserById, updateSharedGoal } from '../services/api'
import { endMentorship, getNextUpcomingMeeting } from '../services/mentorshipMocks'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'
import '../styles/modal.css'

function formatDate(iso, opts = { day: 'numeric', month: 'short', year: 'numeric' }) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (isNaN(d.getTime())) return '—'
  return d.toLocaleDateString('en-GB', opts)
}

function daysRemaining(endIso) {
  if (!endIso) return 0
  const end = new Date(endIso)
  const now = new Date()
  return Math.max(0, Math.ceil((end - now) / 86400000))
}

function Field({ label, value, chips = false }) {
  const empty = value == null || value === '' || (Array.isArray(value) && value.length === 0)
  if (empty) return null
  return (
    <div className="md-field">
      <div className="section-label">{label}</div>
      {Array.isArray(value) ? (
        <div className="profile-chips">
          {value.map(v => <span className="profile-chip" key={v}>{v}</span>)}
        </div>
      ) : (
        <div className="md-field-value">{String(value)}</div>
      )}
      {!chips && null}
    </div>
  )
}

function EndMentorshipModal({ open, onClose, onConfirm, loading, otherName }) {
  const overlayRef = useRef(null)
  useEffect(() => {
    if (!open) return
    const onKey = e => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current) onClose() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="endMentorshipTitle">
        <div className="modal-header">
          <div>
            <h2 id="endMentorshipTitle">End mentorship?</h2>
            <p className="modal-subtitle">
              This will mark the mentorship with {otherName || 'this user'} as completed.
              Messages and meeting history will remain, but no new meetings or tasks can be added.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>
        <div className="modal-actions">
          <button type="button" className="modal-btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
          <button
            type="button"
            className="modal-btn-primary md-danger-btn"
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? 'Ending…' : 'End Mentorship'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function MentorshipDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { userId } = useAuth()

  const [mentorship, setMentorship] = useState(null)
  const [otherUser, setOtherUser] = useState(null)
  const [upcoming, setUpcoming] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [editingGoal, setEditingGoal] = useState(false)
  const [goalDraft, setGoalDraft] = useState('')
  const [goalSaving, setGoalSaving] = useState(false)
  const [goalError, setGoalError] = useState('')

  const [endOpen, setEndOpen] = useState(false)
  const [endLoading, setEndLoading] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    getMentorshipById(id)
      .then(m => {
        if (cancelled) return
        setMentorship(m)
        const viewerIsMentor = String(userId) === String(m.mentorId)
        const otherId = viewerIsMentor ? m.menteeId : m.mentorId
        return Promise.all([
          getUserById(otherId).catch(() => null),
          getNextUpcomingMeeting(m.id).catch(() => null),
        ])
      })
      .then(pair => {
        if (cancelled || !pair) return
        const [user, meeting] = pair
        setOtherUser(user)
        setUpcoming(meeting)
        setLoading(false)
      })
      .catch(err => {
        if (cancelled) return
        setError(err)
        setLoading(false)
      })

    return () => { cancelled = true }
  }, [id, userId])

  async function handleSaveGoal() {
    setGoalSaving(true)
    setGoalError('')
    try {
      const updated = await updateSharedGoal(mentorship.id, goalDraft.trim())
      setMentorship(updated)
      setEditingGoal(false)
    } catch (err) {
      setGoalError(err.message || 'Failed to update goal.')
    } finally {
      setGoalSaving(false)
    }
  }

  async function handleEndConfirm() {
    setEndLoading(true)
    try {
      await endMentorship(mentorship.id)
      setEndOpen(false)
      navigate('/home')
    } catch {
      setEndLoading(false)
    }
  }

  if (loading) {
    return (
      <MainLayout>
        <div className="md-loading">Loading mentorship…</div>
      </MainLayout>
    )
  }

  if (error) {
    const msg = String(error.message || '').toLowerCase()
    const is403 = error.status === 403 || msg.includes('forbidden') || msg.includes('not allowed')
    return (
      <MainLayout>
        <div className="md-error-card">
          <div className="md-error-title">{is403 ? 'Mentorship not available' : 'Something went wrong'}</div>
          <div className="md-error-sub">
            {is403
              ? 'You do not have access to this mentorship, or it no longer exists.'
              : (error.message || 'Please try again in a moment.')}
          </div>
          <button className="action-btn" onClick={() => navigate('/home')}>Back to Home</button>
        </div>
      </MainLayout>
    )
  }

  const viewerIsMentor = String(userId) === String(mentorship.mentorId)
  const roleBadgeText = viewerIsMentor ? 'Your Mentee' : 'Your Mentor'
  const otherFirstName = viewerIsMentor ? mentorship.menteeFirstName : mentorship.mentorFirstName

  // Per req 1.1.2.6: mentor sees mentee first name only (no last name or photo)
  const otherIsMentor = !viewerIsMentor
  const otherLastName = otherIsMentor ? (otherUser?.lastName || '') : ''
  const displayName = [otherFirstName, otherLastName].filter(Boolean).join(' ')
  const avatarSrc = otherIsMentor ? otherUser?.profilePhoto : null
  const initials = [otherFirstName, otherLastName]
    .filter(Boolean)
    .map(w => w[0])
    .join('')
    .toUpperCase() || '?'

  const fieldOrMajor = otherIsMentor ? otherUser?.field : otherUser?.major

  const status = mentorship.status || 'ACTIVE'
  const isActive = status === 'ACTIVE'
  const daysLeft = daysRemaining(mentorship.endDate)

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
          <div className="page-title">Mentorship</div>
          <div className="page-sub">Your active mentorship with {otherFirstName}</div>
        </div>
      </div>

      {/* Header hero */}
      <section className="md-hero">
        <Avatar src={avatarSrc} initials={initials} size="lg" className="md-hero-avatar" />
        <div className="md-hero-info">
          <div className="md-hero-label">{roleBadgeText}</div>
          <div className="md-hero-name">{displayName || otherFirstName}</div>
          {fieldOrMajor && <div className="md-hero-meta">{fieldOrMajor}</div>}
        </div>
        <span className={`md-status-badge md-status-${status.toLowerCase()}`}>
          {status.charAt(0) + status.slice(1).toLowerCase()}
        </span>
      </section>

      {/* Stats */}
      <div className="md-stats-row">
        <div className="md-stat-card">
          <span className="md-stat-num">{formatDate(mentorship.startDate)}</span>
          <span className="md-stat-lbl">Started</span>
        </div>
        <div className="md-stat-card">
          <span className="md-stat-num">{mentorship.duration} mo</span>
          <span className="md-stat-lbl">Duration</span>
        </div>
        <div className="md-stat-card">
          <span className="md-stat-num">{daysLeft}</span>
          <span className="md-stat-lbl">Days Remaining</span>
        </div>
        <div className="md-stat-card">
          <span className="md-stat-num">{status.charAt(0) + status.slice(1).toLowerCase()}</span>
          <span className="md-stat-lbl">Status</span>
        </div>
      </div>

      {/* Shared goal */}
      <section className="card md-goal">
        <div className="md-section-header">
          <div className="section-label" style={{ marginBottom: 0 }}>Shared Goal</div>
          {isActive && !editingGoal && (
            <button
              className="md-link-btn"
              onClick={() => { setGoalDraft(mentorship.sharedGoal || ''); setEditingGoal(true); setGoalError('') }}
            >
              {mentorship.sharedGoal ? 'Edit' : 'Add goal'}
            </button>
          )}
        </div>

        {!editingGoal && (
          mentorship.sharedGoal
            ? <div className="md-goal-text">"{mentorship.sharedGoal}"</div>
            : <div className="md-goal-empty">No shared goal yet. Define a goal together to keep your mentorship focused.</div>
        )}

        {editingGoal && (
          <div className="md-goal-edit">
            <textarea
              className="md-textarea"
              maxLength={500}
              rows={4}
              value={goalDraft}
              onChange={e => setGoalDraft(e.target.value)}
              placeholder="E.g. Help the mentee secure a software internship by August."
              autoFocus
            />
            <div className="md-goal-edit-footer">
              <span className="md-char-count">{goalDraft.length}/500</span>
              <div className="md-goal-actions">
                <button
                  className="modal-btn-secondary"
                  onClick={() => { setEditingGoal(false); setGoalError('') }}
                  disabled={goalSaving}
                >
                  Cancel
                </button>
                <button
                  className="modal-btn-primary md-primary-btn"
                  onClick={handleSaveGoal}
                  disabled={goalSaving || goalDraft.trim().length === 0}
                >
                  {goalSaving ? 'Saving…' : 'Save'}
                </button>
              </div>
            </div>
            {goalError && <div className="modal-api-error" style={{ marginTop: '12px' }}>{goalError}</div>}
          </div>
        )}
      </section>

      {/* Profile */}
      <section className="card md-profile">
        <div className="section-label">
          {otherIsMentor ? 'About your mentor' : 'About your mentee'}
        </div>
        {!otherUser && (
          <div className="md-error-sub" style={{ marginTop: '8px' }}>
            Could not load profile details.
          </div>
        )}
        {otherUser && otherIsMentor && (
          <>
            <Field label="Bio" value={otherUser.bio} />
            <Field label="Field" value={otherUser.field} />
            <Field label="Expertise" value={otherUser.expertise} />
            <Field label="Interests" value={otherUser.interests} chips />
            <div className="divider" />
            <Field label="Mentoring Goals" value={otherUser.mentoringGoals} />
            <Field label="Preferred Mentee Major" value={otherUser.preferredMenteeMajor} />
            <Field label="Preferred Mentee Skills" value={otherUser.preferredMenteeSkills} chips />
          </>
        )}
        {otherUser && !otherIsMentor && (
          <>
            <Field label="Background" value={otherUser.backgroundInfo} />
            <Field label="Major" value={otherUser.major} />
            <Field label="Goals" value={otherUser.goals} />
            <Field label="Career Interest" value={otherUser.careerInterest} />
            <Field label="Interests" value={otherUser.interests} chips />
            <Field label="Skills" value={otherUser.skills} chips />
          </>
        )}
      </section>

      {/* Upcoming meeting preview */}
      <section className="card md-meeting">
        <div className="md-section-header">
          <div className="section-label" style={{ marginBottom: 0 }}>Upcoming Meeting</div>
          <button
            className="md-link-btn"
            onClick={() => navigate(`/schedule?mentorshipId=${mentorship.id}`)}
          >
            See all
          </button>
        </div>
        {upcoming ? (
          <div className="md-meeting-card">
            <div className="md-meeting-day">
              <div className="md-meeting-day-num">{new Date(upcoming.date).getDate()}</div>
              <div className="md-meeting-day-mo">{new Date(upcoming.date).toLocaleDateString('en-GB', { month: 'short' })}</div>
            </div>
            <div className="md-meeting-info">
              <div className="md-meeting-title">{upcoming.title}</div>
              <div className="md-meeting-time">
                {new Date(upcoming.date).toLocaleDateString('en-GB', { weekday: 'long' })}
                {' · '}
                {new Date(upcoming.date).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}
                {' · '}
                {upcoming.durationMin} min
              </div>
            </div>
            <span className={`md-meeting-status md-meeting-status-${upcoming.status.toLowerCase()}`}>
              {upcoming.status.charAt(0) + upcoming.status.slice(1).toLowerCase()}
            </span>
          </div>
        ) : (
          <div className="md-goal-empty">No upcoming meetings. Schedule one from the Schedule page.</div>
        )}
      </section>

      {/* Actions */}
      <div className="md-actions-grid">
        <button
          className="md-action-btn md-action-primary"
          onClick={() => navigate(`/messages?mentorshipId=${mentorship.id}`)}
        >
          Open Messages
        </button>
        <button
          className="md-action-btn"
          onClick={() => navigate(`/schedule?mentorshipId=${mentorship.id}`)}
        >
          Schedule
        </button>
        <button
          className="md-action-btn"
          onClick={() => navigate(`/tasks?mentorshipId=${mentorship.id}`)}
        >
          My Tasks
        </button>
        <button
          className="md-action-btn md-action-danger"
          onClick={() => setEndOpen(true)}
          disabled={!isActive}
        >
          End Mentorship
        </button>
      </div>

      <EndMentorshipModal
        open={endOpen}
        onClose={() => !endLoading && setEndOpen(false)}
        onConfirm={handleEndConfirm}
        loading={endLoading}
        otherName={displayName || otherFirstName}
      />
    </MainLayout>
  )
}
