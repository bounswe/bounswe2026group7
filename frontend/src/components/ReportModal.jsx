import { useEffect, useRef, useState } from 'react'
import { submitReport } from '../services/api'

/**
 * Generic polymorphic report modal (#128 / #358 / #411). Backend
 * (#135) accepts {targetType, targetId, problemType, description}
 * and routes everything to the same admin queue.
 *
 * Props:
 *   open
 *   targetType    'POST' | 'MENTORSHIP' | 'USER'
 *   targetId      numeric id of the reported entity
 *   targetLabel   display label used in the modal header ("post", "mentor", "mentorship")
 *   onClose()
 *   onSubmitted(report)  — fires on 201 success
 *
 * Backend problem-type enum:
 *   INAPPROPRIATE_BEHAVIOR | HARASSMENT | SPAM | MISLEADING_PROFILE | OTHER
 * We expose all five categories regardless of targetType — the spec
 * doesn't carve the list per target, and admin filters by problemType
 * downstream.
 */
const PROBLEM_TYPES = [
  { value: 'INAPPROPRIATE_BEHAVIOR', label: 'Inappropriate behavior' },
  { value: 'HARASSMENT', label: 'Harassment' },
  { value: 'SPAM', label: 'Spam' },
  { value: 'MISLEADING_PROFILE', label: 'Misleading profile' },
  { value: 'OTHER', label: 'Other' },
]

const DESCRIPTION_MAX = 1000

export default function ReportModal({ open, targetType, targetId, targetLabel = 'this', onClose, onSubmitted }) {
  const overlayRef = useRef(null)
  const [problemType, setProblemType] = useState(PROBLEM_TYPES[0].value)
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) {
      setProblemType(PROBLEM_TYPES[0].value)
      setDescription('')
      setError(null)
    }
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const onKey = e => { if (e.key === 'Escape' && !submitting) onClose?.() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, submitting])

  if (!open || !targetType || targetId == null) return null

  const trimmed = description.trim()
  const remaining = DESCRIPTION_MAX - description.length
  const canSubmit = trimmed.length > 0 && !submitting

  async function handleSubmit() {
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      const body = await submitReport({
        targetType,
        targetId: Number(targetId),
        problemType,
        description: trimmed,
      })
      onSubmitted?.(body)
      onClose?.()
    } catch (err) {
      const msg = err?.message || ''
      if (msg.includes('409') || /duplicate|already/i.test(msg)) {
        setError('You already have an open report for this. An admin will review it.')
      } else if (msg.includes('429') || /rate.*limit/i.test(msg)) {
        setError('Too many reports submitted. Please wait a bit before filing another.')
      } else if (msg.includes('400') || /self/i.test(msg)) {
        setError('That target cannot be reported.')
      } else {
        setError(msg || 'Failed to submit report.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current && !submitting) onClose?.() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="report-modal-title">
        <div className="modal-header">
          <div>
            <h2 id="report-modal-title">Report {targetLabel}</h2>
            <p className="modal-subtitle">
              Reports are sent to platform admins for review. Misuse may result in restrictions
              on your account.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }} htmlFor="report-problem-type">
          Problem category
        </label>
        <select
          id="report-problem-type"
          className="form-input"
          value={problemType}
          onChange={e => setProblemType(e.target.value)}
          disabled={submitting}
        >
          {PROBLEM_TYPES.map(p => (
            <option key={p.value} value={p.value}>{p.label}</option>
          ))}
        </select>

        <label className="section-label" style={{ marginTop: '14px', display: 'block' }} htmlFor="report-description">
          Description (required)
        </label>
        <textarea
          id="report-description"
          className="modal-textarea"
          rows={5}
          maxLength={DESCRIPTION_MAX}
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder="Tell admins what happened. Include any context that helps with the review."
          disabled={submitting}
        />
        <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'right' }}>
          {remaining} characters left
        </div>

        {error && (
          <div className="md-error-card" style={{ marginTop: '8px' }}>
            <div className="md-error-sub">{error}</div>
          </div>
        )}

        <div className="modal-actions" style={{ marginTop: '16px' }}>
          <button type="button" className="modal-btn-secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button
            type="button"
            className="modal-btn-primary md-danger-btn"
            onClick={handleSubmit}
            disabled={!canSubmit}
          >
            {submitting ? 'Submitting…' : 'Submit report'}
          </button>
        </div>
      </div>
    </div>
  )
}
