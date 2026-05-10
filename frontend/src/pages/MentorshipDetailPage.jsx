import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import { getMentorshipById, getUserById, updateSharedGoal, cancelMentorship, endMentorship } from '../services/api'
import { getNextUpcomingMeeting } from '../services/mentorshipMocks'
import { useAuth } from '../context/AuthContext'
import { useMentorship } from '../context/MentorshipContext'
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
  const [reason, setReason] = useState('')

  useEffect(() => {
    if (!open) setReason('')
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const onKey = e => { if (e.key === 'Escape' && !loading) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, loading])

  if (!open) return null
  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current && !loading) onClose() }}
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

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }} htmlFor="endReason">
          Wrap-up note (optional)
        </label>
        <textarea
          id="endReason"
          className="modal-textarea"
          rows={3}
          maxLength={500}
          value={reason}
          onChange={e => setReason(e.target.value)}
          placeholder="Optional note shared with the mentee — e.g. 'Goal achieved — congrats!'"
          disabled={loading}
        />
        <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'right' }}>
          {reason.length}/500
        </div>

        <div className="modal-actions" style={{ marginTop: '16px' }}>
          <button type="button" className="modal-btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
          <button
            type="button"
            className="modal-btn-primary md-danger-btn"
            onClick={() => onConfirm(reason.trim() || undefined)}
            disabled={loading}
          >
            {loading ? 'Ending…' : 'End Mentorship'}
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * Mentee-side cancellation modal (#127). Reason is required by the backend
 * (CancelMentorshipRequest @NotBlank); the warning copy is intentionally
 * loud because frequent cancellations escalate into a temporary ban via
 * the auto-ban system (#134) and that fact isn't surfaced in the cancel
 * response — the user needs to be informed up front.
 */
function CancelMentorshipModal({ open, onClose, onConfirm, otherName, loading }) {
  const overlayRef = useRef(null)
  const [reason, setReason] = useState('')
  const [localError, setLocalError] = useState(null)

  useEffect(() => {
    if (!open) { setReason(''); setLocalError(null) }
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const onKey = e => { if (e.key === 'Escape' && !loading) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, loading])

  if (!open) return null

  function handleConfirm() {
    const trimmed = reason.trim()
    if (!trimmed) {
      setLocalError('Please provide a reason for cancellation.')
      return
    }
    setLocalError(null)
    onConfirm(trimmed)
  }

  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current && !loading) onClose() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="cancelMentorshipTitle">
        <div className="modal-header">
          <div>
            <h2 id="cancelMentorshipTitle">Cancel mentorship?</h2>
            <p className="modal-subtitle">
              This will end your mentorship with {otherName || 'this user'} immediately. Meetings,
              tasks, and messages associated with this mentorship will be removed.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        <div className="md-cancel-warning" role="alert">
          <strong>Heads up:</strong> the platform tracks mentee cancellations of active mentorships
          (per requirement 2.2.4). Frequent cancellations can result in a temporary ban from sending
          new mentorship requests.
        </div>

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }} htmlFor="cancelReason">
          Reason (required)
        </label>
        <textarea
          id="cancelReason"
          className="modal-textarea"
          rows={4}
          maxLength={500}
          value={reason}
          onChange={e => setReason(e.target.value)}
          placeholder="Tell your mentor why you're ending the mentorship. Visible to the mentor and platform admins."
          disabled={loading}
        />
        <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'right' }}>
          {reason.length}/500
        </div>
        {localError && (
          <div className="md-composer-error" style={{ marginTop: '8px' }}>{localError}</div>
        )}

        <div className="modal-actions" style={{ marginTop: '16px' }}>
          <button type="button" className="modal-btn-secondary" onClick={onClose} disabled={loading}>
            Keep mentorship
          </button>
          <button
            type="button"
            className="modal-btn-primary md-danger-btn"
            onClick={handleConfirm}
            disabled={loading || !reason.trim()}
          >
            {loading ? 'Cancelling…' : 'Cancel mentorship'}
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
  const { refresh } = useMentorship()

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
  const [endError, setEndError] = useState(null)

  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelLoading, setCancelLoading] = useState(false)
  const [cancelError, setCancelError] = useState(null)

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

  async function handleEndConfirm(reason) {
    setEndLoading(true)
    setEndError(null)
    try {
      // Real backend now (PATCH /api/mentorships/{id}/end). Replace local
      // state with the response so the page collapses into the #341
      // read-only banner (status=COMPLETED, endDate=now). Mirror the
      // cancel UX rather than bouncing the user to /home.
      const updated = await endMentorship(mentorship.id, reason)
      setMentorship(updated)
      setEndOpen(false)
      refresh()
    } catch (err) {
      setEndError(err?.message || 'Failed to end mentorship')
    } finally {
      setEndLoading(false)
    }
  }

  async function handleCancelConfirm(reason) {
    setCancelLoading(true)
    setCancelError(null)
    try {
      const updated = await cancelMentorship(mentorship.id, reason)
      // Replace local state with the canonical updated mentorship; the #341
      // banner picks up the non-ACTIVE status and re-renders read-only.
      setMentorship(updated)
      setCancelOpen(false)
      // Sync sidebar / navbar / dashboard counts with the now-cancelled state.
      refresh()
    } catch (err) {
      setCancelError(err?.message || 'Failed to cancel mentorship')
    } finally {
      setCancelLoading(false)
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
  // 1.1.4.10 — auto-termination at duration end. Backend marks the
  // mentorship as COMPLETED / TERMINATED / CANCELLED depending on the cause;
  // from the UI perspective they all collapse to "this mentorship has ended".
  const endedLabel = (() => {
    if (isActive) return null
    switch (status) {
      case 'COMPLETED': return 'Ended'           // duration reached or mentor early-end
      case 'TERMINATED': return 'Terminated'     // admin / system termination
      case 'CANCELLED': return 'Cancelled'       // mentee cancellation
      default: return 'Ended'
    }
  })()

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
          <div className="page-sub">
            {isActive
              ? `Your active mentorship with ${otherFirstName}`
              : `Your past mentorship with ${otherFirstName}`}
          </div>
        </div>
      </div>

      {!isActive && (
        <div className="md-ended-banner" role="status">
          <strong>{endedLabel}</strong>
          {mentorship.endDate && (
            <> · {endedLabel === 'Cancelled' ? 'Cancelled on' : 'Ended on'} {formatDate(mentorship.endDate)}</>
          )}
          <span className="md-ended-banner-sub">
            This mentorship is read-only. Messages, tasks, and meetings remain accessible for history.
          </span>
        </div>
      )}

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
          Meetings
        </button>
        <button
          className="md-action-btn"
          onClick={() => navigate(`/tasks?mentorshipId=${mentorship.id}`)}
        >
          My Tasks
        </button>
        {viewerIsMentor ? (
          <button
            className="md-action-btn md-action-danger"
            onClick={() => setEndOpen(true)}
            disabled={!isActive}
          >
            End Mentorship
          </button>
        ) : (
          // Mentee uses /cancel (#127); backend rejects POST /end from a mentee.
          <button
            className="md-action-btn md-action-danger"
            onClick={() => setCancelOpen(true)}
            disabled={!isActive}
            title={isActive ? 'Cancel this mentorship' : 'This mentorship has already ended'}
          >
            Cancel Mentorship
          </button>
        )}
      </div>

      {cancelError && (
        <div className="md-error-card" style={{ marginTop: '12px' }}>
          <div className="md-error-title">Couldn’t cancel mentorship</div>
          <div className="md-error-sub">{cancelError}</div>
        </div>
      )}

      {endError && (
        <div className="md-error-card" style={{ marginTop: '12px' }}>
          <div className="md-error-title">Couldn’t end mentorship</div>
          <div className="md-error-sub">{endError}</div>
        </div>
      )}

      <EndMentorshipModal
        open={endOpen}
        onClose={() => !endLoading && setEndOpen(false)}
        onConfirm={handleEndConfirm}
        loading={endLoading}
        otherName={displayName || otherFirstName}
      />

      <CancelMentorshipModal
        open={cancelOpen}
        onClose={() => !cancelLoading && setCancelOpen(false)}
        onConfirm={handleCancelConfirm}
        loading={cancelLoading}
        otherName={displayName || otherFirstName}
      />
    </MainLayout>
  )
}
