import { useEffect, useRef } from 'react'

/**
 * Side-by-side mentor comparison view (#139). Up to 3 mentors. Closes
 * via overlay click, the × button, or Escape.
 *
 * Backend's MentorMatchResponse carries everything we display: firstName,
 * expertise, affiliation, city, distanceKm, matchScore, factors,
 * mentorshipDuration, maxMenteeCapacity, currentMenteeCount, averageRating,
 * ratingCount. We render whatever's available and skip missing fields
 * gracefully — a mentor with no rating just shows "—" in that row.
 */

const ROWS = [
  { key: 'expertise', label: 'Expertise' },
  { key: 'affiliation', label: 'Affiliation' },
  { key: 'city', label: 'City' },
  { key: 'distanceKm', label: 'Distance', format: v => v != null ? `${Number(v).toFixed(1)} km` : '' },
  { key: 'matchScore', label: 'Match score', format: v => v != null ? `${v}` : '' },
  { key: 'mentorshipDuration', label: 'Duration', format: v => v != null ? `${v} month${v !== 1 ? 's' : ''}` : '' },
  {
    key: 'capacity',
    label: 'Capacity',
    format: (_, m) => m.maxMenteeCapacity != null
      ? `${m.currentMenteeCount ?? 0} / ${m.maxMenteeCapacity}`
      : '',
  },
  {
    key: 'rating',
    label: 'Rating',
    format: (_, m) => m.averageRating != null
      ? `★ ${Number(m.averageRating).toFixed(1)} (${m.ratingCount ?? 0})`
      : '',
  },
]

export default function MentorCompareModal({ open, mentors = [], onClose, onRemove }) {
  const overlayRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const onKey = e => { if (e.key === 'Escape') onClose?.() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open || mentors.length === 0) return null

  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current) onClose?.() }}
    >
      <div className="modal-card mentor-compare-card" role="dialog" aria-modal="true" aria-labelledby="mentor-compare-title">
        <div className="modal-header">
          <div>
            <h2 id="mentor-compare-title">Compare mentors</h2>
            <p className="modal-subtitle">
              Up to 3 mentors side by side. Remove from the selection with the × on a column.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close comparison">×</button>
        </div>

        <div className="mentor-compare-grid" style={{ gridTemplateColumns: `160px repeat(${mentors.length}, minmax(0, 1fr))` }}>
          <div /> {/* row-label column header is empty */}
          {mentors.map(m => (
            <div key={m.id} className="mentor-compare-col-head">
              <div className="mentor-compare-name">{m.firstName || `Mentor #${m.id}`}</div>
              <button
                type="button"
                className="mentor-compare-remove"
                onClick={() => onRemove?.(m.id)}
                aria-label={`Remove ${m.firstName || 'mentor'} from comparison`}
              >×</button>
            </div>
          ))}

          {ROWS.map(row => (
            <CompareRow key={row.key} row={row} mentors={mentors} />
          ))}

          {/* Factors are arrays, so we render them as a separate row of chips */}
          <div className="mentor-compare-row-label">Top factors</div>
          {mentors.map(m => (
            <div key={`${m.id}-factors`} className="mentor-compare-cell">
              {Array.isArray(m.factors) && m.factors.length > 0 ? (
                <div className="mentor-compare-factors">
                  {m.factors.slice(0, 6).map((f, i) => (
                    <span key={i} className="mentor-compare-factor">{f}</span>
                  ))}
                </div>
              ) : (
                <span className="mentor-compare-empty">—</span>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function CompareRow({ row, mentors }) {
  return (
    <>
      <div className="mentor-compare-row-label">{row.label}</div>
      {mentors.map(m => {
        const raw = row.key === 'capacity' || row.key === 'rating' ? null : m[row.key]
        const value = row.format ? row.format(raw, m) : (raw ?? '')
        return (
          <div key={`${m.id}-${row.key}`} className="mentor-compare-cell">
            {value !== '' && value != null
              ? <span>{value}</span>
              : <span className="mentor-compare-empty">—</span>}
          </div>
        )
      })}
    </>
  )
}
