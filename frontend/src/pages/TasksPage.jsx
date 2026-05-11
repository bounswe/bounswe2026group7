import { useEffect, useMemo, useState, useCallback, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import {
  getActiveMentorships,
  listMentorshipTasks,
  getTaskDetail,
  createTask,
  submitTask,
  reviewTask,
  deleteTask,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'
import '../styles/modal.css'

/**
 * #125 — Task assignment / submission / tracking.
 *
 * Three-section layout per spec: Pending → Awaiting Feedback → Completed.
 * Backend status enum is PENDING / SUBMITTED / REVISION_REQUESTED / COMPLETED.
 * REVISION_REQUESTED collapses into "Pending" because the next action belongs
 * to the mentee (resubmit) — same UX bucket as a freshly assigned task.
 */
const STATUS_BUCKETS = {
  pending: ['PENDING', 'REVISION_REQUESTED'],
  awaiting: ['SUBMITTED'],
  completed: ['COMPLETED'],
}

function bucketOf(status) {
  for (const [key, statuses] of Object.entries(STATUS_BUCKETS)) {
    if (statuses.includes(status)) return key
  }
  return 'pending'
}

function formatDate(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (isNaN(d.getTime())) return '—'
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

function formatDateTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}

function statusBadgeClass(status) {
  switch (status) {
    case 'PENDING': return 'task-badge task-badge--pending'
    case 'SUBMITTED': return 'task-badge task-badge--submitted'
    case 'REVISION_REQUESTED': return 'task-badge task-badge--revision'
    case 'COMPLETED': return 'task-badge task-badge--completed'
    default: return 'task-badge'
  }
}

function statusLabel(status) {
  if (!status) return ''
  return status.replace('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
}

export default function TasksPage() {
  const [params] = useSearchParams()
  const scopedId = params.get('mentorshipId')
  const navigate = useNavigate()
  const { role } = useAuth()
  const isMentor = role === 'MENTOR'

  // mentorships keyed by id so the cross-view can label rows by counterpart
  const [mentorships, setMentorships] = useState([])
  // task-summary list, each enriched with `mentorshipId` so cross-view works
  const [tasks, setTasks] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Modal state
  const [createOpen, setCreateOpen] = useState(false)
  const [submitTarget, setSubmitTarget] = useState(null)   // task summary
  const [reviewTarget, setReviewTarget] = useState(null)   // task summary

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
            const taskList = await listMentorshipTasks(mId)
            return (taskList || []).map(t => ({ ...t, mentorshipId: mId }))
          } catch {
            return []
          }
        })
      )
      const flat = perMentorship.flat()
      setTasks(flat)
    } catch (err) {
      setError(err?.message || 'Failed to load tasks')
      setTasks([])
    } finally {
      setLoading(false)
    }
  }, [scopedId])

  useEffect(() => { reload() }, [reload])

  const buckets = useMemo(() => {
    const out = { pending: [], awaiting: [], completed: [] }
    for (const t of tasks) out[bucketOf(t.status)].push(t)
    // Pending: due-soonest first; Awaiting/Completed: most recent activity first
    out.pending.sort((a, b) => new Date(a.dueDate || 0) - new Date(b.dueDate || 0))
    out.awaiting.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
    out.completed.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
    return out
  }, [tasks])

  const counterpartFor = useCallback((mentorshipId) => {
    const m = mentorships.find(x => String(x.id) === String(mentorshipId))
    if (!m) return null
    return isMentor ? m.menteeFirstName : m.mentorFirstName
  }, [mentorships, isMentor])

  const scopedMentorship = scopedId
    ? mentorships.find(m => String(m.id) === String(scopedId))
    : null

  // Mutations refresh the canonical list so bucket counts stay correct.
  function handleCreated() { setCreateOpen(false); reload() }
  function handleSubmitted() { setSubmitTarget(null); reload() }
  function handleReviewed() { setReviewTarget(null); reload() }
  async function handleDelete(task) {
    if (!window.confirm(`Delete task "${task.title}"? This can't be undone.`)) return
    try {
      await deleteTask(task.id)
      reload()
    } catch (err) {
      window.alert(err?.message || 'Failed to delete task')
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
          <div className="page-title">Tasks</div>
          <div className="page-sub">
            {scopedMentorship
              ? `${counterpartFor(scopedId) ? 'With ' + counterpartFor(scopedId) : 'Mentorship tasks'}`
              : 'Across all your active mentorships'}
          </div>
        </div>
        {isMentor && scopedId && (
          <button className="action-btn" onClick={() => setCreateOpen(true)}>
            + New Task
          </button>
        )}
      </div>

      {loading ? (
        <div className="md-loading">Loading tasks…</div>
      ) : error ? (
        <div className="md-error-card">
          <div className="md-error-title">Couldn’t load tasks</div>
          <div className="md-error-sub">{error}</div>
        </div>
      ) : tasks.length === 0 ? (
        <div className="empty-state" data-testid="tasks-empty">
          {isMentor && scopedId
            ? 'No tasks assigned yet. Use “New Task” to create one.'
            : 'No tasks yet.'}
        </div>
      ) : (
        <div className="task-buckets">
          <TaskBucket
            title="Pending"
            tasks={buckets.pending}
            isMentor={isMentor}
            counterpartFor={counterpartFor}
            scoped={!!scopedId}
            onSubmit={t => setSubmitTarget(t)}
            onDelete={handleDelete}
          />
          <TaskBucket
            title="Awaiting Feedback"
            tasks={buckets.awaiting}
            isMentor={isMentor}
            counterpartFor={counterpartFor}
            scoped={!!scopedId}
            onReview={t => setReviewTarget(t)}
          />
          <TaskBucket
            title="Completed"
            tasks={buckets.completed}
            isMentor={isMentor}
            counterpartFor={counterpartFor}
            scoped={!!scopedId}
          />
        </div>
      )}

      {createOpen && scopedId && (
        <CreateTaskModal
          mentorshipId={scopedId}
          onClose={() => setCreateOpen(false)}
          onCreated={handleCreated}
        />
      )}
      {submitTarget && (
        <SubmitTaskModal
          task={submitTarget}
          onClose={() => setSubmitTarget(null)}
          onSubmitted={handleSubmitted}
        />
      )}
      {reviewTarget && (
        <ReviewTaskModal
          task={reviewTarget}
          onClose={() => setReviewTarget(null)}
          onReviewed={handleReviewed}
        />
      )}
    </MainLayout>
  )
}

// ── Bucket section ──────────────────────────────────────────────────────────

function TaskBucket({ title, tasks, isMentor, counterpartFor, scoped, onSubmit, onReview, onDelete }) {
  return (
    <section className="task-bucket">
      <div className="task-bucket-header">
        <h2 className="task-bucket-title">{title}</h2>
        <span className="task-bucket-count">{tasks.length}</span>
      </div>
      {tasks.length === 0 ? (
        <div className="task-bucket-empty">Nothing here.</div>
      ) : (
        <div className="task-list">
          {tasks.map(t => (
            <TaskCard
              key={t.id}
              task={t}
              isMentor={isMentor}
              counterpart={counterpartFor(t.mentorshipId)}
              showCounterpart={!scoped}
              onSubmit={onSubmit}
              onReview={onReview}
              onDelete={onDelete}
            />
          ))}
        </div>
      )}
    </section>
  )
}

// ── Task card (clickable to expand + show submission history) ─────────────

function TaskCard({ task, isMentor, counterpart, showCounterpart, onSubmit, onReview, onDelete }) {
  const [expanded, setExpanded] = useState(false)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const overdue = task.isOverdue && task.status !== 'COMPLETED'

  async function toggleExpand() {
    if (expanded) { setExpanded(false); return }
    setExpanded(true)
    if (detail || detailLoading) return
    setDetailLoading(true)
    try {
      const d = await getTaskDetail(task.id)
      setDetail(d)
    } catch {
      // Leave expanded with empty detail; parent shows the summary anyway
    } finally {
      setDetailLoading(false)
    }
  }

  return (
    <article className={`task-card${overdue ? ' task-card--overdue' : ''}`}>
      <button type="button" className="task-card-summary" onClick={toggleExpand} aria-expanded={expanded}>
        <div className="task-card-main">
          <div className="task-card-title">{task.title}</div>
          <div className="task-card-meta">
            {task.dueDate && <span>Due {formatDate(task.dueDate)}</span>}
            {showCounterpart && counterpart && <span> · with {counterpart}</span>}
            {overdue && <span className="task-card-overdue">Overdue</span>}
          </div>
        </div>
        <span className={statusBadgeClass(task.status)}>{statusLabel(task.status)}</span>
      </button>

      {expanded && (
        <div className="task-card-detail">
          {detailLoading && <div className="task-detail-loading">Loading details…</div>}
          {detail && (
            <>
              {detail.description && (
                <div className="task-detail-row">
                  <div className="section-label">Description</div>
                  <div className="task-detail-body">{detail.description}</div>
                </div>
              )}
              {Array.isArray(detail.assignmentAttachments) && detail.assignmentAttachments.length > 0 && (
                <div className="task-detail-row">
                  <div className="section-label">Attachments from mentor</div>
                  <ul className="task-attachment-list">
                    {detail.assignmentAttachments.map(a => (
                      <li key={a.id}>{a.filename}</li>
                    ))}
                  </ul>
                </div>
              )}
              {Array.isArray(detail.submissions) && detail.submissions.length > 0 ? (
                <div className="task-detail-row">
                  <div className="section-label">Submission history (newest first)</div>
                  <ol className="task-submission-list">
                    {detail.submissions.map(s => (
                      <li key={s.id} className="task-submission-item">
                        <div className="task-submission-meta">
                          Submitted {formatDateTime(s.submittedAt)}
                          {s.reviewedAt && <> · Reviewed {formatDateTime(s.reviewedAt)}</>}
                        </div>
                        <div className="task-submission-text">{s.submissionText}</div>
                        {Array.isArray(s.attachments) && s.attachments.length > 0 && (
                          <ul className="task-attachment-list">
                            {s.attachments.map(a => (
                              <li key={a.id}>{a.filename}</li>
                            ))}
                          </ul>
                        )}
                        {s.feedback && (
                          <div className="task-submission-feedback">
                            <strong>Mentor feedback:</strong> {s.feedback}
                          </div>
                        )}
                      </li>
                    ))}
                  </ol>
                </div>
              ) : (
                <div className="task-detail-row task-detail-empty">No submissions yet.</div>
              )}
            </>
          )}
          {!detailLoading && !detail && <div className="task-detail-empty">Couldn’t load full details.</div>}
        </div>
      )}

      <div className="task-card-actions">
        {!isMentor && (task.status === 'PENDING' || task.status === 'REVISION_REQUESTED') && (
          <button className="task-action-primary" onClick={() => onSubmit?.(task)}>
            {task.status === 'REVISION_REQUESTED' ? 'Submit revision' : 'Submit work'}
          </button>
        )}
        {isMentor && task.status === 'SUBMITTED' && (
          <button className="task-action-primary" onClick={() => onReview?.(task)}>
            Review submission
          </button>
        )}
        {isMentor && task.status === 'PENDING' && (
          <button className="task-action-danger" onClick={() => onDelete?.(task)}>
            Delete
          </button>
        )}
      </div>
    </article>
  )
}

// ── Modals ──────────────────────────────────────────────────────────────────

function ModalShell({ titleId, title, subtitle, onClose, busy, children }) {
  const overlayRef = useRef(null)
  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape' && !busy) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])
  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current && !busy) onClose() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <div className="modal-header">
          <div>
            <h2 id={titleId}>{title}</h2>
            {subtitle && <p className="modal-subtitle">{subtitle}</p>}
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>
        {children}
      </div>
    </div>
  )
}

function CreateTaskModal({ mentorshipId, onClose, onCreated }) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState(null)

  async function handleSubmit() {
    if (!title.trim()) { setErr('Title is required.'); return }
    setBusy(true); setErr(null)
    try {
      // dueDate from <input type="datetime-local"> is naive; backend wants
      // an OffsetDateTime, so append the local zone offset.
      const due = dueDate ? new Date(dueDate).toISOString() : undefined
      await createTask(mentorshipId, { title: title.trim(), description: description.trim() || undefined, dueDate: due })
      onCreated()
    } catch (e) {
      setErr(e?.message || 'Failed to create task')
    } finally {
      setBusy(false)
    }
  }

  return (
    <ModalShell
      titleId="newTaskTitle"
      title="New task"
      subtitle="Assign work to your mentee. They get a notification."
      onClose={onClose}
      busy={busy}
    >
      <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Title (required)</label>
      <input
        className="modal-textarea"
        style={{ minHeight: 'auto', height: '40px' }}
        value={title}
        onChange={e => setTitle(e.target.value)}
        maxLength={200}
        disabled={busy}
        placeholder="e.g. Submit project plan v1"
      />
      <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Description</label>
      <textarea
        className="modal-textarea"
        rows={4}
        value={description}
        onChange={e => setDescription(e.target.value)}
        disabled={busy}
        placeholder="What should the mentee deliver?"
      />
      <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Due date (optional)</label>
      <input
        type="datetime-local"
        className="modal-textarea"
        style={{ minHeight: 'auto', height: '40px' }}
        value={dueDate}
        onChange={e => setDueDate(e.target.value)}
        disabled={busy}
      />
      {err && <div className="md-composer-error" style={{ marginTop: '8px' }}>{err}</div>}
      <div className="modal-actions" style={{ marginTop: '16px' }}>
        <button className="modal-btn-secondary" onClick={onClose} disabled={busy}>Cancel</button>
        <button className="modal-btn-primary" onClick={handleSubmit} disabled={busy || !title.trim()}>
          {busy ? 'Creating…' : 'Create task'}
        </button>
      </div>
    </ModalShell>
  )
}

function SubmitTaskModal({ task, onClose, onSubmitted }) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState(null)

  async function handleSubmit() {
    if (!text.trim()) { setErr('Submission text is required.'); return }
    setBusy(true); setErr(null)
    try {
      await submitTask(task.id, { submissionText: text.trim() })
      onSubmitted()
    } catch (e) {
      setErr(e?.message || 'Failed to submit')
    } finally {
      setBusy(false)
    }
  }

  return (
    <ModalShell
      titleId="submitTaskTitle"
      title={task.status === 'REVISION_REQUESTED' ? 'Submit revision' : 'Submit work'}
      subtitle={`Task: ${task.title}`}
      onClose={onClose}
      busy={busy}
    >
      <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Your submission (required)</label>
      <textarea
        className="modal-textarea"
        rows={6}
        value={text}
        onChange={e => setText(e.target.value)}
        disabled={busy}
        placeholder="Describe what you did, link to your work, paste relevant excerpts…"
      />
      {err && <div className="md-composer-error" style={{ marginTop: '8px' }}>{err}</div>}
      <div className="modal-actions" style={{ marginTop: '16px' }}>
        <button className="modal-btn-secondary" onClick={onClose} disabled={busy}>Cancel</button>
        <button className="modal-btn-primary" onClick={handleSubmit} disabled={busy || !text.trim()}>
          {busy ? 'Submitting…' : 'Submit'}
        </button>
      </div>
    </ModalShell>
  )
}

function ReviewTaskModal({ task, onClose, onReviewed }) {
  const [feedback, setFeedback] = useState('')
  const [decision, setDecision] = useState('COMPLETED') // COMPLETED or REVISION_REQUESTED
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState(null)

  async function handleReview() {
    if (!feedback.trim()) { setErr('Feedback is required.'); return }
    setBusy(true); setErr(null)
    try {
      await reviewTask(task.id, { feedback: feedback.trim(), status: decision })
      onReviewed()
    } catch (e) {
      setErr(e?.message || 'Failed to record review')
    } finally {
      setBusy(false)
    }
  }

  return (
    <ModalShell
      titleId="reviewTaskTitle"
      title="Review submission"
      subtitle={`Task: ${task.title}`}
      onClose={onClose}
      busy={busy}
    >
      <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Feedback (required)</label>
      <textarea
        className="modal-textarea"
        rows={5}
        value={feedback}
        onChange={e => setFeedback(e.target.value)}
        disabled={busy}
        placeholder="What was done well? What needs revision?"
        maxLength={4000}
      />
      <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'right' }}>
        {feedback.length}/4000
      </div>

      <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Decision</label>
      <div className="task-review-decision">
        <label>
          <input
            type="radio"
            name="decision"
            value="COMPLETED"
            checked={decision === 'COMPLETED'}
            onChange={() => setDecision('COMPLETED')}
            disabled={busy}
          />
          <span>Mark as completed</span>
        </label>
        <label>
          <input
            type="radio"
            name="decision"
            value="REVISION_REQUESTED"
            checked={decision === 'REVISION_REQUESTED'}
            onChange={() => setDecision('REVISION_REQUESTED')}
            disabled={busy}
          />
          <span>Request revision</span>
        </label>
      </div>

      {err && <div className="md-composer-error" style={{ marginTop: '8px' }}>{err}</div>}
      <div className="modal-actions" style={{ marginTop: '16px' }}>
        <button className="modal-btn-secondary" onClick={onClose} disabled={busy}>Cancel</button>
        <button className="modal-btn-primary" onClick={handleReview} disabled={busy || !feedback.trim()}>
          {busy ? 'Saving…' : 'Save review'}
        </button>
      </div>
    </ModalShell>
  )
}
