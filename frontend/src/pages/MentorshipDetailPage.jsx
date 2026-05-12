import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import MentorshipMilestones from '../components/MentorshipMilestones'
import MentorshipProgressTimeline from '../components/MentorshipProgressTimeline'
import MentorMenteesProgress from '../components/MentorMenteesProgress'
import {
  getMentorshipById,
  getUserById,
  updateSharedGoal,
  cancelMentorship,
  endMentorship,
  extendMentorship,
  rateMentor,
  listMentorshipMeetings,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import { useMentorship } from '../context/MentorshipContext'
import '../styles/main.css'
import '../styles/modal.css'

/**
 * Derive the "next upcoming meeting" card payload from the real meetings
 * list — replaces the legacy `getNextUpcomingMeeting` mock (#506).
 *
 * Picks the meeting with the smallest startTime ≥ now whose status is
 * PENDING_CONFIRMATION or CONFIRMED. Returns the meeting with two derived
 * fields the existing card markup expects:
 *   - `date`: alias of startTime so existing JSX keeps working
 *   - `durationMin`: computed from (endTime − startTime)
 *   - `status`: passes through; the badge renderer normalises display
 */
function deriveNextUpcomingMeeting(meetings) {
  if (!Array.isArray(meetings) || meetings.length === 0) return null
  const now = Date.now()
  const upcoming = meetings.filter(m => {
    if (m?.status !== 'PENDING_CONFIRMATION' && m?.status !== 'CONFIRMED') return false
    const start = new Date(m.startTime).getTime()
    return Number.isFinite(start) && start >= now
  })
  if (upcoming.length === 0) return null
  upcoming.sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
  const next = upcoming[0]
  const start = new Date(next.startTime).getTime()
  const end = new Date(next.endTime).getTime()
  const durationMin = Number.isFinite(start) && Number.isFinite(end) && end > start
    ? Math.round((end - start) / 60000)
    : null
  return { ...next, date: next.startTime, durationMin }
}

// Map a backend MeetingStatus to the existing CSS class suffix + display label.
// Existing classes: confirmed, pending, completed. PENDING_CONFIRMATION
// collapses to `pending`; declined/expired/cancelled won't appear in the
// upcoming bucket but are rendered defensively.
function meetingStatusUi(status) {
  switch (status) {
    case 'CONFIRMED':            return { cls: 'confirmed', label: 'Confirmed' }
    case 'PENDING_CONFIRMATION': return { cls: 'pending', label: 'Pending' }
    case 'COMPLETED':            return { cls: 'completed', label: 'Completed' }
    case 'DECLINED':             return { cls: 'pending', label: 'Declined' }
    case 'EXPIRED':              return { cls: 'pending', label: 'Expired' }
    case 'CANCELLED':            return { cls: 'pending', label: 'Cancelled' }
    default:                     return { cls: 'pending', label: status || '—' }
  }
}

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
 * Mentor-side extension modal (#276 / 1.1.1.2.13). Backend requires
 * additionalMonths ∈ {1, 3, 6}; we render the choice as a 3-up segmented
 * picker rather than a free-form input so we never send an invalid value.
 */
function ExtendMentorshipModal({ open, onClose, onConfirm, loading, otherName, currentEndDate }) {
  const overlayRef = useRef(null)
  const [months, setMonths] = useState(3)

  useEffect(() => {
    if (open) setMonths(3)
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const onKey = e => { if (e.key === 'Escape' && !loading) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, loading])

  if (!open) return null

  const projectedEnd = currentEndDate
    ? (() => {
        const d = new Date(currentEndDate)
        if (isNaN(d.getTime())) return null
        d.setMonth(d.getMonth() + months)
        return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
      })()
    : null

  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current && !loading) onClose() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="extendMentorshipTitle">
        <div className="modal-header">
          <div>
            <h2 id="extendMentorshipTitle">Extend mentorship?</h2>
            <p className="modal-subtitle">
              Push the end date of your mentorship with {otherName || 'this mentee'} forward.
              Your mentee will be notified.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>
          Add to current end date
        </label>
        <div className="md-extend-options">
          {[1, 3, 6].map(m => (
            <button
              key={m}
              type="button"
              className={`md-extend-option${months === m ? ' md-extend-option--active' : ''}`}
              onClick={() => setMonths(m)}
              disabled={loading}
              aria-pressed={months === m}
            >
              +{m} month{m !== 1 ? 's' : ''}
            </button>
          ))}
        </div>

        {projectedEnd && (
          <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '12px' }}>
            New end date: <strong style={{ color: 'var(--text)' }}>{projectedEnd}</strong>
          </div>
        )}

        <div className="modal-actions" style={{ marginTop: '16px' }}>
          <button type="button" className="modal-btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
          <button
            type="button"
            className="modal-btn-primary"
            onClick={() => onConfirm(months)}
            disabled={loading}
          >
            {loading ? 'Extending…' : `Extend by ${months} month${months !== 1 ? 's' : ''}`}
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * Mentee-side mentor rating modal (#278 / 1.1.1.1.11). Backend caps the
 * score at 1..5 and the optional comment at 1000 chars. One-shot per
 * mentorship — duplicate POST returns 409, surfaced here as an error.
 */
function RateMentorModal({ open, onClose, onConfirm, loading, mentorName }) {
  const overlayRef = useRef(null)
  const [score, setScore] = useState(0)
  const [hoverScore, setHoverScore] = useState(0)
  const [comment, setComment] = useState('')

  useEffect(() => {
    if (open) { setScore(0); setHoverScore(0); setComment('') }
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const onKey = e => { if (e.key === 'Escape' && !loading) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, loading])

  if (!open) return null

  const displayScore = hoverScore || score

  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current && !loading) onClose() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="rateMentorTitle">
        <div className="modal-header">
          <div>
            <h2 id="rateMentorTitle">Rate {mentorName || 'your mentor'}</h2>
            <p className="modal-subtitle">
              Your rating helps other mentees find a great match. Only the average and rating
              count are shown publicly — your comment may appear without your name attached.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>
          Score (required)
        </label>
        <div
          className="md-rating-stars"
          role="radiogroup"
          aria-label="Score from 1 to 5 stars"
          onMouseLeave={() => setHoverScore(0)}
        >
          {[1, 2, 3, 4, 5].map(n => (
            <button
              key={n}
              type="button"
              role="radio"
              aria-checked={score === n}
              aria-label={`${n} star${n !== 1 ? 's' : ''}`}
              className={`md-rating-star${displayScore >= n ? ' md-rating-star--filled' : ''}`}
              onClick={() => setScore(n)}
              onMouseEnter={() => setHoverScore(n)}
              onFocus={() => setHoverScore(n)}
              onBlur={() => setHoverScore(0)}
              disabled={loading}
            >
              ★
            </button>
          ))}
          <span className="md-rating-value">
            {displayScore > 0 ? `${displayScore} / 5` : 'Pick a score'}
          </span>
        </div>

        <label className="section-label" style={{ marginTop: '14px', display: 'block' }} htmlFor="rateComment">
          Comment (optional)
        </label>
        <textarea
          id="rateComment"
          className="modal-textarea"
          rows={4}
          maxLength={1000}
          value={comment}
          onChange={e => setComment(e.target.value)}
          placeholder="Tell future mentees what made working with this mentor valuable."
          disabled={loading}
        />
        <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'right' }}>
          {comment.length}/1000
        </div>

        <div className="modal-actions" style={{ marginTop: '16px' }}>
          <button type="button" className="modal-btn-secondary" onClick={onClose} disabled={loading}>
            Cancel
          </button>
          <button
            type="button"
            className="modal-btn-primary"
            onClick={() => onConfirm(score, comment.trim() || undefined)}
            disabled={loading || score < 1}
          >
            {loading ? 'Submitting…' : 'Submit rating'}
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

/**
 * Shared goal definition modal (#277). Either party can edit while ACTIVE.
 * Validates non-empty input and shares the single text field via backend.
 */
function SharedGoalModal({ open, initial, onClose, onSubmit, loading, error }) {
  const overlayRef = useRef(null)
  const [text, setText] = useState(initial || '')

  useEffect(() => {
    if (open) setText(initial || '')
  }, [open, initial])

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
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="goalModalTitle">
        <div className="modal-header">
          <div>
            <h2 id="goalModalTitle">Shared Goal</h2>
            <p className="modal-subtitle">
              Define a single overarching goal for this mentorship. Work together
              to keep it focused and achievable.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        <textarea
          className="modal-textarea"
          rows={5}
          maxLength={500}
          value={text}
          onChange={e => setText(e.target.value)}
          placeholder="E.g. Help the mentee secure a software internship by August."
          disabled={loading}
          autoFocus
          style={{ marginTop: '16px' }}
        />
        <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'right' }}>
          {text.length}/500
        </div>
        {error && (
          <div className="md-composer-error" style={{ marginTop: '8px' }}>{error}</div>
        )}

        <div className="modal-actions" style={{ marginTop: '16px' }}>
          <button type="button" className="modal-btn-secondary" onClick={onClose} disabled={loading}>
            Cancel
          </button>
          <button
            type="button"
            className="modal-btn-primary"
            onClick={() => onSubmit(text.trim())}
            disabled={loading || !text.trim()}
          >
            {loading ? 'Saving…' : 'Save'}
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
  const [extendOpen, setExtendOpen] = useState(false)
  const [extendLoading, setExtendLoading] = useState(false)
  const [extendError, setExtendError] = useState(null)
  const [rateOpen, setRateOpen] = useState(false)
  const [rateLoading, setRateLoading] = useState(false)
  const [rateError, setRateError] = useState(null)
  // Backend doesn't expose a GET-rating endpoint today, so we mirror successful
  // submissions in localStorage to suppress the prompt on subsequent visits.
  // The 409-on-duplicate-POST path also flips this so cross-device
  // resubmission is caught.
  const [submittedRating, setSubmittedRating] = useState(null)

  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelLoading, setCancelLoading] = useState(false)
  const [cancelError, setCancelError] = useState(null)

  // Hydrate already-submitted rating from localStorage so the prompt doesn't
  // reappear on revisit. Backend has no GET-rating endpoint today (#278 follow-up).
  useEffect(() => {
    if (!id) return
    try {
      const raw = localStorage.getItem(`rated_mentorship_${id}`)
      if (raw) setSubmittedRating(JSON.parse(raw))
    } catch { /* malformed entry — ignore */ }
  }, [id])

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
          listMentorshipMeetings(m.id).catch(() => []),
        ])
      })
      .then(pair => {
        if (cancelled || !pair) return
        const [user, meetings] = pair
        setOtherUser(user)
        setUpcoming(deriveNextUpcomingMeeting(meetings))
        setLoading(false)
      })
      .catch(err => {
        if (cancelled) return
        setError(err)
        setLoading(false)
      })

    return () => { cancelled = true }
  }, [id, userId])

  async function handleSaveGoal(newText) {
    setGoalSaving(true)
    setGoalError('')
    try {
      const updated = await updateSharedGoal(mentorship.id, newText)
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

  async function handleRateConfirm(score, comment) {
    setRateLoading(true)
    setRateError(null)
    try {
      const created = await rateMentor(mentorship.id, score, comment)
      setSubmittedRating(created)
      try {
        localStorage.setItem(`rated_mentorship_${mentorship.id}`, JSON.stringify({
          score: created.score,
          comment: created.comment,
          createdAt: created.createdAt,
        }))
      } catch { /* localStorage disabled — fall back to in-memory state */ }
      setRateOpen(false)
    } catch (err) {
      const msg = err?.message || ''
      // Duplicate-rating path: backend returns 409 with "already rated" wording.
      // Treat as success-ish — flip to the read-only block so the user isn't stuck.
      if (msg.includes('409') || /already.*rated/i.test(msg)) {
        setSubmittedRating({ score, comment, createdAt: new Date().toISOString() })
        try {
          localStorage.setItem(`rated_mentorship_${mentorship.id}`, JSON.stringify({
            score, comment, createdAt: new Date().toISOString(),
          }))
        } catch { /* ignore */ }
        setRateOpen(false)
      } else {
        setRateError(msg || 'Failed to submit rating')
      }
    } finally {
      setRateLoading(false)
    }
  }

  async function handleExtendConfirm(additionalMonths) {
    setExtendLoading(true)
    setExtendError(null)
    try {
      const updated = await extendMentorship(mentorship.id, additionalMonths)
      setMentorship(updated)
      setExtendOpen(false)
      refresh()
    } catch (err) {
      setExtendError(err?.message || 'Failed to extend mentorship')
    } finally {
      setExtendLoading(false)
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

      {!isActive && !viewerIsMentor && (
        submittedRating ? (
          <div className="md-rating-card md-rating-card--done" role="status">
            <div className="md-rating-card-head">
              <strong>You rated {otherFirstName || 'your mentor'}</strong>
              <span className="md-rating-stars md-rating-stars--readonly" aria-hidden="true">
                {[1, 2, 3, 4, 5].map(n => (
                  <span
                    key={n}
                    className={`md-rating-star${submittedRating.score >= n ? ' md-rating-star--filled' : ''}`}
                  >★</span>
                ))}
                <span className="md-rating-value">{submittedRating.score} / 5</span>
              </span>
            </div>
            {submittedRating.comment && (
              <p className="md-rating-card-comment">"{submittedRating.comment}"</p>
            )}
          </div>
        ) : (
          <div className="md-rating-card" role="region" aria-label="Rate your mentor">
            <div className="md-rating-card-head">
              <strong>Rate {otherFirstName || 'your mentor'}</strong>
              <span className="md-rating-card-sub">
                Help future mentees by sharing your experience.
              </span>
            </div>
            <button
              type="button"
              className="md-action-btn md-action-primary"
              onClick={() => setRateOpen(true)}
            >
              Rate your mentor
            </button>
          </div>
        )
      )}

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

      {/* Shared goal CTA for active mentorships without a goal (#277) */}
      {!mentorship.sharedGoal && isActive && (
        <section className="card md-goal-cta" style={{ border: '1px solid var(--primary-color)', backgroundColor: 'rgba(59, 130, 246, 0.05)' }}>
          <div className="md-section-header" style={{ marginBottom: '8px' }}>
            <div className="section-label" style={{ marginBottom: 0, color: 'var(--primary-color)' }}>
              Define a shared goal
            </div>
          </div>
          <p style={{ margin: '0 0 16px', color: 'var(--text-color)', fontSize: '14px', lineHeight: '1.5' }}>
            Your mentorship is active! Work together to define a shared goal. Setting a goal
            unlocks milestones and progress tracking features.
          </p>
          <button
            className="md-action-btn md-action-primary"
            style={{ width: 'auto', padding: '8px 16px', fontSize: '13px' }}
            onClick={() => { setGoalDraft(''); setEditingGoal(true); setGoalError('') }}
          >
            Add Goal
          </button>
        </section>
      )}

      {/* Shared goal */}
      {(mentorship.sharedGoal || !isActive) && (
        <section className="card md-goal">
          <div className="md-section-header">
            <div className="section-label" style={{ marginBottom: 0 }}>Shared Goal</div>
            {isActive && (
              <button
                className="md-link-btn"
                onClick={() => { setGoalDraft(mentorship.sharedGoal || ''); setEditingGoal(true); setGoalError('') }}
              >
                {mentorship.sharedGoal ? 'Edit' : 'Add goal'}
              </button>
            )}
          </div>

          {mentorship.sharedGoal ? (
            <div className="md-goal-text">"{mentorship.sharedGoal}"</div>
          ) : (
            <div className="md-goal-empty">No shared goal was set during this mentorship.</div>
          )}
        </section>
      )}

      {/* Progress + Timeline (#126 + #333, gated in #277) */}
      {mentorship.sharedGoal && (
        <MentorshipProgressTimeline mentorshipId={mentorship.id} />
      )}

      {/* Milestones (#288, gated by goal in #277) */}
      <MentorshipMilestones
        mentorshipId={mentorship.id}
        isMentor={viewerIsMentor}
        isActive={isActive}
        hasSharedGoal={!!mentorship.sharedGoal}
      />

      {/* Mentor cross-mentee comparison (#126). Self-hides when viewer is a
          mentee or when no other active mentees exist. */}
      {viewerIsMentor && (
        <MentorMenteesProgress currentMentorshipId={mentorship.id} />
      )}

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
            {(() => {
              const ui = meetingStatusUi(upcoming.status)
              return (
                <span className={`md-meeting-status md-meeting-status-${ui.cls}`}>
                  {ui.label}
                </span>
              )
            })()}
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
        {viewerIsMentor && (
          <button
            className="md-action-btn"
            onClick={() => setExtendOpen(true)}
            disabled={!isActive}
            title={isActive ? 'Add 1, 3, or 6 months to the end date' : 'This mentorship has already ended'}
          >
            Extend Duration
          </button>
        )}
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

      {extendError && (
        <div className="md-error-card" style={{ marginTop: '12px' }}>
          <div className="md-error-title">Couldn’t extend mentorship</div>
          <div className="md-error-sub">{extendError}</div>
        </div>
      )}

      <ExtendMentorshipModal
        open={extendOpen}
        onClose={() => !extendLoading && setExtendOpen(false)}
        onConfirm={handleExtendConfirm}
        loading={extendLoading}
        otherName={displayName || otherFirstName}
        currentEndDate={mentorship.endDate}
      />

      <RateMentorModal
        open={rateOpen}
        onClose={() => !rateLoading && setRateOpen(false)}
        onConfirm={handleRateConfirm}
        loading={rateLoading}
        mentorName={mentorship.mentorFirstName || otherFirstName}
      />

      {rateError && (
        <div className="md-error-card" style={{ marginTop: '12px' }}>
          <div className="md-error-title">Couldn’t submit rating</div>
          <div className="md-error-sub">{rateError}</div>
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

      <SharedGoalModal
        open={editingGoal}
        initial={goalDraft}
        onClose={() => !goalSaving && setEditingGoal(false)}
        onSubmit={handleSaveGoal}
        loading={goalSaving}
        error={goalError}
      />
    </MainLayout>
  )
}
