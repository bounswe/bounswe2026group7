import { useEffect, useMemo, useState, useRef, useCallback } from 'react'
import {
  listMilestones,
  getMilestoneDetail,
  createMilestone,
  updateMilestone,
  deleteMilestone,
  createMilestoneActionItem,
  updateMilestoneActionItem,
  deleteMilestoneActionItem,
} from '../services/api'

/**
 * #288 — Milestones panel embedded in MentorshipDetailPage.
 *
 * Mentor:
 *   - create / edit / delete milestones
 *   - move status PENDING → IN_PROGRESS → COMPLETED
 *   - add / edit / delete action items
 * Mentee:
 *   - read-only view of milestones
 *   - may toggle action-item completion (backend allows both parties on the
 *     `completed` field; everything else on the action item is mentor-only)
 *
 * Status badges + an overall progress bar (percent of milestones COMPLETED).
 *
 * Mutating endpoints are gated by `isMentor`; the backend rejects with 403 if
 * a mentee tries the mentor-only paths regardless, but hiding them in the UI
 * keeps the experience clean.
 */
export default function MentorshipMilestones({ mentorshipId, isMentor, isActive, hasSharedGoal }) {
  const [list, setList] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState(null)        // milestone summary
  const [expanded, setExpanded] = useState(null)      // milestoneId currently expanded

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listMilestones(mentorshipId)
      const ordered = (data || []).slice().sort((a, b) => {
        const ai = a.orderIndex ?? 0, bi = b.orderIndex ?? 0
        if (ai !== bi) return ai - bi
        return new Date(a.targetDate || 0) - new Date(b.targetDate || 0)
      })
      setList(ordered)
    } catch (err) {
      setError(err?.message || 'Failed to load milestones')
      setList([])
    } finally {
      setLoading(false)
    }
  }, [mentorshipId])

  useEffect(() => {
    if (hasSharedGoal) {
      reload()
    } else {
      setLoading(false)
      setList([])
    }
  }, [reload, hasSharedGoal])

  const overallProgress = useMemo(() => {
    if (list.length === 0) return null
    const done = list.filter(m => m.status === 'COMPLETED').length
    return { done, total: list.length, pct: Math.round((done / list.length) * 100) }
  }, [list])

  async function handleStatusChange(milestone, nextStatus) {
    if (milestone.status === nextStatus) return
    try {
      await updateMilestone(milestone.id, { status: nextStatus })
      reload()
    } catch (err) {
      window.alert(err?.message || 'Failed to update milestone status')
    }
  }

  async function handleDelete(milestone) {
    if (!window.confirm(`Delete milestone "${milestone.title}"? Action items inside it will be removed too.`)) return
    try {
      await deleteMilestone(milestone.id)
      if (expanded === milestone.id) setExpanded(null)
      reload()
    } catch (err) {
      window.alert(err?.message || 'Failed to delete milestone')
    }
  }

  if (!hasSharedGoal) {
    return (
      <section className="card md-milestones">
        <div className="md-section-header">
          <div className="section-label" style={{ marginBottom: 0 }}>Milestones</div>
        </div>
        <div className="md-goal-empty">
          Please define a shared goal first to unlock milestones and progress tracking.
        </div>
      </section>
    )
  }

  return (
    <section className="card md-milestones">
      <div className="md-section-header">
        <div className="section-label" style={{ marginBottom: 0 }}>Milestones</div>
        {isMentor && isActive && (
          <button className="md-link-btn" onClick={() => setCreateOpen(true)}>
            + Add milestone
          </button>
        )}
      </div>

      {overallProgress && (
        <div className="milestone-progress">
          <div className="milestone-progress-meta">
            {overallProgress.done} of {overallProgress.total} completed · {overallProgress.pct}%
          </div>
          <div className="milestone-progress-bar">
            <div className="milestone-progress-fill" style={{ width: `${overallProgress.pct}%` }} />
          </div>
        </div>
      )}

      {loading ? (
        <div className="md-goal-empty">Loading milestones…</div>
      ) : error ? (
        <div className="md-error-card">
          <div className="md-error-title">Couldn’t load milestones</div>
          <div className="md-error-sub">{error}</div>
        </div>
      ) : list.length === 0 ? (
        <div className="md-goal-empty">
          {isMentor
            ? 'No milestones yet. Add one to break the goal into trackable stages.'
            : 'No milestones yet — your mentor will define stages here.'}
        </div>
      ) : (
        <ol className="milestone-list">
          {list.map(m => (
            <MilestoneCard
              key={m.id}
              milestone={m}
              isMentor={isMentor}
              isActive={isActive}
              expanded={expanded === m.id}
              onToggle={() => setExpanded(prev => prev === m.id ? null : m.id)}
              onStatus={nextStatus => handleStatusChange(m, nextStatus)}
              onEdit={() => setEditing(m)}
              onDelete={() => handleDelete(m)}
              onActionItemChanged={reload}
            />
          ))}
        </ol>
      )}

      {createOpen && (
        <MilestoneFormModal
          mode="create"
          onClose={() => setCreateOpen(false)}
          onSubmit={async (payload) => {
            await createMilestone(mentorshipId, payload)
            setCreateOpen(false)
            reload()
          }}
        />
      )}

      {editing && (
        <MilestoneFormModal
          mode="edit"
          initial={editing}
          onClose={() => setEditing(null)}
          onSubmit={async (payload) => {
            await updateMilestone(editing.id, payload)
            setEditing(null)
            reload()
          }}
        />
      )}
    </section>
  )
}

// ── Milestone card (expandable to action items) ────────────────────────────

function MilestoneCard({
  milestone, isMentor, isActive, expanded, onToggle, onStatus, onEdit, onDelete, onActionItemChanged,
}) {
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState(null)

  useEffect(() => {
    if (!expanded) return undefined
    let cancelled = false
    setDetailLoading(true)
    setDetailError(null)
    getMilestoneDetail(milestone.id)
      .then(d => { if (!cancelled) setDetail(d) })
      .catch(err => { if (!cancelled) setDetailError(err?.message || 'Failed to load milestone') })
      .finally(() => { if (!cancelled) setDetailLoading(false) })
    return () => { cancelled = true }
  }, [expanded, milestone.id])

  const overdue = milestone.targetDate
    && milestone.status !== 'COMPLETED'
    && new Date(milestone.targetDate) < new Date()

  return (
    <li className={`milestone-card${expanded ? ' milestone-card--open' : ''}`}>
      <button type="button" className="milestone-card-summary" onClick={onToggle} aria-expanded={expanded}>
        <div className="milestone-card-main">
          <div className="milestone-card-title">{milestone.title}</div>
          <div className="milestone-card-meta">
            {milestone.targetDate && <span>Target {formatDate(milestone.targetDate)}</span>}
            {overdue && <span className="milestone-overdue"> · Overdue</span>}
          </div>
        </div>
        <span className={statusBadgeClass(milestone.status)}>{statusLabel(milestone.status)}</span>
      </button>

      {expanded && (
        <div className="milestone-card-body">
          {detailLoading && <div className="md-goal-empty">Loading…</div>}
          {detailError && (
            <div className="md-error-sub" style={{ color: '#c53030' }}>{detailError}</div>
          )}
          {detail && (
            <>
              {detail.description && (
                <div className="milestone-detail-row">
                  <div className="section-label">Description</div>
                  <div className="milestone-description">{detail.description}</div>
                </div>
              )}

              <ActionItemsList
                milestoneId={detail.id}
                items={detail.actionItems || []}
                isMentor={isMentor}
                isActive={isActive}
                onChanged={onActionItemChanged}
              />

              {isMentor && isActive && (
                <div className="milestone-card-actions">
                  <StatusSelector status={milestone.status} onChange={onStatus} />
                  <button className="task-action-primary" onClick={onEdit}>Edit milestone</button>
                  <button className="task-action-danger" onClick={onDelete}>Delete</button>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </li>
  )
}

function StatusSelector({ status, onChange }) {
  const options = [
    { value: 'PENDING', label: 'Pending' },
    { value: 'IN_PROGRESS', label: 'In progress' },
    { value: 'COMPLETED', label: 'Completed' },
  ]
  return (
    <select
      className="milestone-status-select"
      value={status}
      onChange={e => onChange(e.target.value)}
      aria-label="Milestone status"
    >
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  )
}

// ── Action items section (within a milestone card) ─────────────────────────

function ActionItemsList({ milestoneId, items, isMentor, isActive, onChanged }) {
  const [busyId, setBusyId] = useState(null)
  const [adding, setAdding] = useState(false)
  const [newText, setNewText] = useState('')
  const [editingId, setEditingId] = useState(null)
  const [editText, setEditText] = useState('')

  async function toggle(item) {
    setBusyId(item.id)
    try {
      await updateMilestoneActionItem(item.id, { completed: !item.isCompleted })
      onChanged?.()
    } catch (err) {
      window.alert(err?.message || 'Failed to update action item')
    } finally {
      setBusyId(null)
    }
  }

  async function submitNew() {
    const trimmed = newText.trim()
    if (!trimmed) return
    setBusyId('new')
    try {
      await createMilestoneActionItem(milestoneId, { text: trimmed })
      setNewText('')
      setAdding(false)
      onChanged?.()
    } catch (err) {
      window.alert(err?.message || 'Failed to add action item')
    } finally {
      setBusyId(null)
    }
  }

  async function submitEdit(item) {
    const trimmed = editText.trim()
    if (!trimmed) { setEditingId(null); return }
    setBusyId(item.id)
    try {
      await updateMilestoneActionItem(item.id, { text: trimmed })
      setEditingId(null)
      onChanged?.()
    } catch (err) {
      window.alert(err?.message || 'Failed to update action item')
    } finally {
      setBusyId(null)
    }
  }

  async function remove(item) {
    if (!window.confirm('Delete this action item?')) return
    setBusyId(item.id)
    try {
      await deleteMilestoneActionItem(item.id)
      onChanged?.()
    } catch (err) {
      window.alert(err?.message || 'Failed to delete action item')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="milestone-detail-row">
      <div className="section-label">Action items</div>
      {items.length === 0 ? (
        <div className="md-goal-empty">
          {isMentor ? 'No items yet. Add one below.' : 'No action items.'}
        </div>
      ) : (
        <ul className="action-item-list">
          {items.map(it => (
            <li key={it.id} className={`action-item${it.isCompleted ? ' action-item--done' : ''}`}>
              <label className="action-item-check">
                <input
                  type="checkbox"
                  checked={it.isCompleted}
                  onChange={() => toggle(it)}
                  disabled={busyId === it.id || !isActive}
                />
                {editingId === it.id ? (
                  <input
                    type="text"
                    className="action-item-edit"
                    value={editText}
                    autoFocus
                    onChange={e => setEditText(e.target.value)}
                    onBlur={() => submitEdit(it)}
                    onKeyDown={e => {
                      if (e.key === 'Enter') { e.preventDefault(); submitEdit(it) }
                      if (e.key === 'Escape') setEditingId(null)
                    }}
                    disabled={busyId === it.id}
                  />
                ) : (
                  <span className="action-item-text">{it.text}</span>
                )}
              </label>
              {isMentor && isActive && editingId !== it.id && (
                <div className="action-item-actions">
                  <button
                    className="md-link-btn"
                    onClick={() => { setEditingId(it.id); setEditText(it.text) }}
                    disabled={busyId === it.id}
                  >
                    Edit
                  </button>
                  <button
                    className="md-link-btn md-link-btn--danger"
                    onClick={() => remove(it)}
                    disabled={busyId === it.id}
                  >
                    Delete
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {isMentor && isActive && (
        adding ? (
          <div className="action-item-add">
            <input
              type="text"
              className="action-item-edit"
              placeholder="What needs to be done?"
              value={newText}
              autoFocus
              onChange={e => setNewText(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter') { e.preventDefault(); submitNew() }
                if (e.key === 'Escape') { setAdding(false); setNewText('') }
              }}
              disabled={busyId === 'new'}
            />
            <button className="task-action-primary" onClick={submitNew} disabled={busyId === 'new' || !newText.trim()}>
              Add
            </button>
            <button className="md-link-btn" onClick={() => { setAdding(false); setNewText('') }}>Cancel</button>
          </div>
        ) : (
          <button className="md-link-btn" onClick={() => setAdding(true)} style={{ marginTop: '8px' }}>
            + Add action item
          </button>
        )
      )}
    </div>
  )
}

// ── Create / edit milestone modal ──────────────────────────────────────────

function MilestoneFormModal({ mode, initial, onClose, onSubmit }) {
  const overlayRef = useRef(null)
  const [title, setTitle] = useState(initial?.title || '')
  const [description, setDescription] = useState(initial?.description || '')
  const [targetDate, setTargetDate] = useState(toLocalDateTimeInput(initial?.targetDate))
  const [status, setStatus] = useState(initial?.status || 'PENDING')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState(null)

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape' && !busy) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  async function handleSubmit() {
    if (!title.trim()) { setErr('Title is required.'); return }
    setBusy(true); setErr(null)
    try {
      const payload = { title: title.trim(), description: description.trim() || null }
      if (targetDate) payload.targetDate = new Date(targetDate).toISOString()
      else payload.targetDate = null
      if (mode === 'edit') payload.status = status
      await onSubmit(payload)
    } catch (e) {
      setErr(e?.message || 'Failed to save milestone')
      setBusy(false)
    }
  }

  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current && !busy) onClose() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true">
        <div className="modal-header">
          <div>
            <h2>{mode === 'edit' ? 'Edit milestone' : 'New milestone'}</h2>
            <p className="modal-subtitle">
              Milestones break the shared goal into trackable stages. Add action items inside each.
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
        />

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Description</label>
        <textarea
          className="modal-textarea"
          rows={4}
          value={description}
          onChange={e => setDescription(e.target.value)}
          disabled={busy}
        />

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Target date</label>
        <input
          type="datetime-local"
          className="modal-textarea"
          style={{ minHeight: 'auto', height: '40px' }}
          value={targetDate}
          onChange={e => setTargetDate(e.target.value)}
          disabled={busy}
        />

        {mode === 'edit' && (
          <>
            <label className="section-label" style={{ marginTop: '12px', display: 'block' }}>Status</label>
            <StatusSelector status={status} onChange={setStatus} />
          </>
        )}

        {err && <div className="md-composer-error" style={{ marginTop: '8px' }}>{err}</div>}

        <div className="modal-actions" style={{ marginTop: '16px' }}>
          <button className="modal-btn-secondary" onClick={onClose} disabled={busy}>Cancel</button>
          <button className="modal-btn-primary" onClick={handleSubmit} disabled={busy || !title.trim()}>
            {busy ? 'Saving…' : (mode === 'edit' ? 'Save' : 'Create milestone')}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Helpers ────────────────────────────────────────────────────────────────

function statusBadgeClass(status) {
  switch (status) {
    case 'PENDING': return 'task-badge task-badge--pending'
    case 'IN_PROGRESS': return 'task-badge task-badge--submitted'
    case 'COMPLETED': return 'task-badge task-badge--completed'
    default: return 'task-badge'
  }
}

function statusLabel(status) {
  if (!status) return ''
  return status.replace('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
}

function formatDate(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (isNaN(d.getTime())) return '—'
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

// Convert an ISO timestamp into the value shape <input type="datetime-local"> expects
// (`YYYY-MM-DDTHH:mm`), in the user's local timezone.
function toLocalDateTimeInput(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
