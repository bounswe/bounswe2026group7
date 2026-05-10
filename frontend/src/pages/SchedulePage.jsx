import { useEffect, useMemo, useState, useCallback, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import {
  getActiveMentorships,
  listMentorshipMeetings,
  createMeeting,
  confirmMeeting,
  declineMeeting,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'
import '../styles/modal.css'

/**
 * #337 — Meetings list + create + confirm/decline.
 *
 * Backend statuses (MeetingStatus): PENDING_CONFIRMATION, CONFIRMED,
 * DECLINED, EXPIRED, COMPLETED, CANCELLED.
 *
 * Bucketing:
 *   - Upcoming: startTime >= now AND status ∈ { PENDING_CONFIRMATION, CONFIRMED }
 *   - Past: everything else
 *
 * Mentor-only "+ New meeting" modal supports:
 *   - one-time OR weekly-recurring (RFC 5545: FREQ=WEEKLY)
 *   - ONLINE (link required) or IN_PERSON (link optional)
 *   - title + description + start/end (datetime-local) + duration helper
 *
 * Mentee sees Confirm / Decline on PENDING_CONFIRMATION rows. Reschedule and
 * Cancel actions land in #338 (separate PR).
 */
const ACTIVE_STATUSES = new Set(['PENDING_CONFIRMATION', 'CONFIRMED'])

function isUpcoming(m) {
  if (!m) return false
  if (!ACTIVE_STATUSES.has(m.status)) return false
  const t = new Date(m.startTime).getTime()
  return Number.isFinite(t) && t >= Date.now()
}

function statusBadgeClass(status) {
  switch (status) {
    case 'CONFIRMED': return 'task-badge task-badge--completed'
    case 'PENDING_CONFIRMATION': return 'task-badge task-badge--pending'
    case 'DECLINED':
    case 'CANCELLED': return 'task-badge task-badge--revision'
    case 'EXPIRED': return 'task-badge task-badge--pending'
    case 'COMPLETED': return 'task-badge task-badge--submitted'
    default: return 'task-badge'
  }
}

function statusLabel(status) {
  if (!status) return ''
  return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
}

function formatStart(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) {
    return `Today · ${d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}`
  }
  return d.toLocaleString('en-GB', {
    weekday: 'short', day: 'numeric', month: 'short',
    hour: '2-digit', minute: '2-digit',
  })
}

function durationMinutes(start, end) {
  if (!start || !end) return null
  const ms = new Date(end).getTime() - new Date(start).getTime()
  if (!Number.isFinite(ms) || ms <= 0) return null
  return Math.round(ms / 60000)
}

export default function SchedulePage() {
  const [params] = useSearchParams()
  const scopedId = params.get('mentorshipId')
  const navigate = useNavigate()
  const { role } = useAuth()
  const isMentor = role === 'MENTOR'

  const [mentorships, setMentorships] = useState([])
  const [meetings, setMeetings] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [busyMeetingId, setBusyMeetingId] = useState(null)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const list = await getActiveMentorships()
      const ms = list || []
      setMentorships(ms)
      const targetIds = scopedId
        ? ms.filter(m => String(m.id) === String(scopedId)).map(m => m.id)
        : ms.map(m => m.id)
      const perMentorship = await Promise.all(
        targetIds.map(async mId => {
          try {
            const result = await listMentorshipMeetings(mId)
            return (result || []).map(meeting => ({ ...meeting, mentorshipId: mId }))
          } catch {
            return []
          }
        })
      )
      setMeetings(perMentorship.flat())
    } catch (err) {
      setError(err?.message || 'Failed to load meetings')
      setMeetings([])
    } finally {
      setLoading(false)
    }
  }, [scopedId])

  useEffect(() => { reload() }, [reload])

  const counterpartFor = useCallback((mentorshipId) => {
    const m = mentorships.find(x => String(x.id) === String(mentorshipId))
    if (!m) return null
    return isMentor ? m.menteeFirstName : m.mentorFirstName
  }, [mentorships, isMentor])

  const buckets = useMemo(() => {
    const upcoming = []
    const past = []
    for (const m of meetings) (isUpcoming(m) ? upcoming : past).push(m)
    upcoming.sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
    past.sort((a, b) => new Date(b.startTime) - new Date(a.startTime))
    return { upcoming, past }
  }, [meetings])

  const scopedMentorship = scopedId
    ? mentorships.find(m => String(m.id) === String(scopedId))
    : null

  async function handleConfirm(meeting) {
    setBusyMeetingId(meeting.id)
    try {
      await confirmMeeting(meeting.id)
      reload()
    } catch (err) {
      window.alert(err?.message || 'Failed to confirm meeting')
    } finally {
      setBusyMeetingId(null)
    }
  }

  async function handleDecline(meeting) {
    if (!window.confirm(`Decline "${meeting.title}"? Your mentor will be notified.`)) return
    setBusyMeetingId(meeting.id)
    try {
      await declineMeeting(meeting.id)
      reload()
    } catch (err) {
      window.alert(err?.message || 'Failed to decline meeting')
    } finally {
      setBusyMeetingId(null)
    }
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          {scopedId && (
            <button
              onClick={() => navigate(`/mentorships/${scopedId}`)}
              style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px', padding: 0, marginBottom: '8px' }}
            >
              ← Back to mentorship
            </button>
          )}
          <div className="page-title">Schedule</div>
          <div className="page-sub">
            {scopedMentorship
              ? `Meetings with ${counterpartFor(scopedId) || '…'}`
              : 'Meetings across all your active mentorships'}
          </div>
        </div>
        {isMentor && scopedId && (
          <button className="action-btn" onClick={() => setCreateOpen(true)}>
            + New meeting
          </button>
        )}
      </div>

      {loading ? (
        <div className="md-loading">Loading meetings…</div>
      ) : error ? (
        <div className="md-error-card">
          <div className="md-error-title">Couldn’t load meetings</div>
          <div className="md-error-sub">{error}</div>
        </div>
      ) : meetings.length === 0 ? (
        <div className="empty-state">
          {isMentor && scopedId
            ? 'No meetings yet. Use “New meeting” to schedule one.'
            : 'No meetings yet.'}
        </div>
      ) : (
        <div className="task-buckets">
          <MeetingBucket
            title="Upcoming"
            meetings={buckets.upcoming}
            isMentor={isMentor}
            counterpartFor={counterpartFor}
            scoped={!!scopedId}
            busyMeetingId={busyMeetingId}
            onConfirm={handleConfirm}
            onDecline={handleDecline}
          />
          <MeetingBucket
            title="Past"
            meetings={buckets.past}
            isMentor={isMentor}
            counterpartFor={counterpartFor}
            scoped={!!scopedId}
            past
          />
        </div>
      )}

      {createOpen && scopedId && (
        <NewMeetingModal
          mentorshipId={scopedId}
          onClose={() => setCreateOpen(false)}
          onCreated={() => { setCreateOpen(false); reload() }}
        />
      )}
    </MainLayout>
  )
}

// ── Bucket ─────────────────────────────────────────────────────────────────

function MeetingBucket({
  title, meetings, isMentor, counterpartFor, scoped, past = false,
  busyMeetingId, onConfirm, onDecline,
}) {
  return (
    <section className={`task-bucket${past ? ' task-bucket--past' : ''}`}>
      <div className="task-bucket-header">
        <h2 className="task-bucket-title">{title}</h2>
        <span className="task-bucket-count">{meetings.length}</span>
      </div>
      {meetings.length === 0 ? (
        <div className="task-bucket-empty">Nothing here.</div>
      ) : (
        <div className="task-list">
          {meetings.map(m => (
            <MeetingCard
              key={m.id}
              meeting={m}
              isMentor={isMentor}
              counterpart={counterpartFor(m.mentorshipId)}
              showCounterpart={!scoped}
              busy={busyMeetingId === m.id}
              onConfirm={onConfirm}
              onDecline={onDecline}
            />
          ))}
        </div>
      )}
    </section>
  )
}

// ── Meeting card ───────────────────────────────────────────────────────────

function MeetingCard({ meeting, isMentor, counterpart, showCounterpart, busy, onConfirm, onDecline }) {
  const dur = durationMinutes(meeting.startTime, meeting.endTime)
  const showMenteeActions = !isMentor && meeting.status === 'PENDING_CONFIRMATION'
  return (
    <article className="task-card">
      <div className="task-card-summary" style={{ cursor: 'default' }}>
        <div className="task-card-main">
          <div className="task-card-title">{meeting.title || 'Meeting'}</div>
          <div className="task-card-meta">
            <span>{formatStart(meeting.startTime)}</span>
            {dur && <span> · {dur} min</span>}
            {showCounterpart && counterpart && <span> · with {counterpart}</span>}
            {meeting.recurring && <span> · Recurring</span>}
            {meeting.meetingType === 'ONLINE' ? <span> · Online</span> : <span> · In-person</span>}
          </div>
          {meeting.meetingLink && (
            <a
              className="meeting-card-link"
              href={meeting.meetingLink}
              target="_blank"
              rel="noopener noreferrer"
              onClick={e => e.stopPropagation()}
            >
              Open meeting link ↗
            </a>
          )}
        </div>
        <span className={statusBadgeClass(meeting.status)}>{statusLabel(meeting.status)}</span>
      </div>

      {showMenteeActions && (
        <div className="task-card-actions">
          <button
            className="task-action-primary"
            onClick={() => onConfirm?.(meeting)}
            disabled={busy}
          >
            {busy ? 'Confirming…' : 'Confirm'}
          </button>
          <button
            className="task-action-danger"
            onClick={() => onDecline?.(meeting)}
            disabled={busy}
          >
            Decline
          </button>
        </div>
      )}
    </article>
  )
}

// ── New meeting modal (mentor only, scoped) ───────────────────────────────

function NewMeetingModal({ mentorshipId, onClose, onCreated }) {
  const overlayRef = useRef(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [startStr, setStartStr] = useState('')
  const [durationMin, setDurationMin] = useState(60)
  const [meetingType, setMeetingType] = useState('ONLINE')
  const [meetingLink, setMeetingLink] = useState('')
  const [recurring, setRecurring] = useState(false)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState(null)

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape' && !busy) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  function validate() {
    if (!title.trim()) return 'Title is required.'
    if (!startStr) return 'Start time is required.'
    const startDate = new Date(startStr)
    if (isNaN(startDate.getTime())) return 'Start time is invalid.'
    if (startDate.getTime() <= Date.now()) return 'Start time must be in the future.'
    if (!Number.isInteger(durationMin) || durationMin < 15 || durationMin > 480) {
      return 'Duration must be between 15 and 480 minutes.'
    }
    if (meetingType === 'ONLINE') {
      if (!meetingLink.trim()) return 'Online meetings require a meeting link.'
      try { new URL(meetingLink.trim()) } catch { return 'Meeting link must be a valid URL.' }
    }
    return null
  }

  async function handleSubmit() {
    const v = validate()
    if (v) { setErr(v); return }
    setBusy(true); setErr(null)
    try {
      const startDate = new Date(startStr)
      const endDate = new Date(startDate.getTime() + durationMin * 60000)
      const payload = {
        title: title.trim(),
        description: description.trim() || undefined,
        startTime: startDate.toISOString(),
        endTime: endDate.toISOString(),
        meetingType,
        meetingLink: meetingType === 'ONLINE' ? meetingLink.trim() : undefined,
        recurring,
        recurrenceRule: recurring ? 'FREQ=WEEKLY' : undefined,
      }
      await createMeeting(mentorshipId, payload)
      onCreated()
    } catch (e) {
      setErr(e?.message || 'Failed to create meeting')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current && !busy) onClose() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="newMeetingTitle">
        <div className="modal-header">
          <div>
            <h2 id="newMeetingTitle">New meeting</h2>
            <p className="modal-subtitle">
              Schedule a meeting with your mentee. They’ll get a confirmation request and a reminder before it starts.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Title (required)</label>
        <input
          className="modal-textarea"
          style={{ minHeight: 'auto', height: '40px' }}
          value={title}
          onChange={e => setTitle(e.target.value)}
          maxLength={200}
          disabled={busy}
          placeholder="e.g. Weekly sync"
        />

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Description</label>
        <textarea
          className="modal-textarea"
          rows={3}
          value={description}
          onChange={e => setDescription(e.target.value)}
          disabled={busy}
          placeholder="What should the mentee expect?"
        />

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 140px', gap: '10px', marginTop: '12px' }}>
          <div>
            <label className="section-label" style={{ display: 'block' }}>Start (required)</label>
            <input
              type="datetime-local"
              className="modal-textarea"
              style={{ minHeight: 'auto', height: '40px' }}
              value={startStr}
              onChange={e => setStartStr(e.target.value)}
              disabled={busy}
            />
          </div>
          <div>
            <label className="section-label" style={{ display: 'block' }}>Duration (min)</label>
            <input
              type="number"
              className="modal-textarea"
              style={{ minHeight: 'auto', height: '40px' }}
              value={durationMin}
              onChange={e => setDurationMin(parseInt(e.target.value, 10) || 0)}
              min={15}
              max={480}
              step={15}
              disabled={busy}
            />
          </div>
        </div>

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Meeting type</label>
        <div className="task-review-decision">
          <label>
            <input
              type="radio"
              name="meeting-type"
              value="ONLINE"
              checked={meetingType === 'ONLINE'}
              onChange={() => setMeetingType('ONLINE')}
              disabled={busy}
            />
            <span>Online (link required)</span>
          </label>
          <label>
            <input
              type="radio"
              name="meeting-type"
              value="IN_PERSON"
              checked={meetingType === 'IN_PERSON'}
              onChange={() => setMeetingType('IN_PERSON')}
              disabled={busy}
            />
            <span>In-person</span>
          </label>
        </div>

        {meetingType === 'ONLINE' && (
          <>
            <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Meeting link (required)</label>
            <input
              className="modal-textarea"
              style={{ minHeight: 'auto', height: '40px' }}
              value={meetingLink}
              onChange={e => setMeetingLink(e.target.value)}
              disabled={busy}
              placeholder="https://meet.google.com/abc-defg-hij"
            />
          </>
        )}

        <label
          style={{ marginTop: '12px', display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '13px' }}
        >
          <input
            type="checkbox"
            checked={recurring}
            onChange={e => setRecurring(e.target.checked)}
            disabled={busy}
          />
          <span>Repeat weekly (RFC 5545 RRULE)</span>
        </label>

        {err && <div className="md-composer-error" style={{ marginTop: '8px' }}>{err}</div>}

        <div className="modal-actions" style={{ marginTop: '16px' }}>
          <button className="modal-btn-secondary" onClick={onClose} disabled={busy}>Cancel</button>
          <button className="modal-btn-primary" onClick={handleSubmit} disabled={busy}>
            {busy ? 'Creating…' : 'Create meeting'}
          </button>
        </div>
      </div>
    </div>
  )
}
