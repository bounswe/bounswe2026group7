import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getActiveMentorships, getMentorshipProgress } from '../services/api'

/**
 * #126 — Mentor cross-mentee comparison panel.
 *
 * Lists each of the mentor's active mentees with their overall progress
 * percent and last-activity timestamp. Click a row → that mentorship's
 * detail page. The currently-viewed mentorship is excluded so the panel
 * only shows the *other* mentees the mentor has.
 *
 * Renders nothing when:
 *   - viewer is not a mentor
 *   - the mentor has no other active mentees besides the current one
 *   - all calls fail (silent — comparison is a nice-to-have, not load-bearing)
 *
 * One GET /api/mentorships followed by one GET /progress per mentorship.
 * Acceptable since the cap on mentor capacity is small (single digits).
 */
export default function MentorMenteesProgress({ currentMentorshipId }) {
  const navigate = useNavigate()
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    ;(async () => {
      try {
        const list = await getActiveMentorships()
        const others = (list || []).filter(m => String(m.id) !== String(currentMentorshipId))
        if (others.length === 0) { if (!cancelled) setRows([]); return }
        const enriched = await Promise.all(
          others.map(async m => {
            try {
              const p = await getMentorshipProgress(m.id)
              return {
                id: m.id,
                name: m.menteeFirstName || '—',
                pct: Math.round((p.progressRatio || 0) * 100),
                lastActivityAt: p.lastActivityAt || null,
              }
            } catch {
              return { id: m.id, name: m.menteeFirstName || '—', pct: 0, lastActivityAt: null }
            }
          })
        )
        // Most-progressed first; ties broken by most-recent activity
        enriched.sort((a, b) => {
          if (b.pct !== a.pct) return b.pct - a.pct
          return new Date(b.lastActivityAt || 0) - new Date(a.lastActivityAt || 0)
        })
        if (!cancelled) setRows(enriched)
      } catch {
        if (!cancelled) setRows([])
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [currentMentorshipId])

  if (loading) return null
  if (rows.length === 0) return null

  return (
    <section className="card md-comparison">
      <div className="section-label">Across your other active mentees</div>
      <div className="md-comparison-list">
        {rows.map(r => (
          <button
            key={r.id}
            type="button"
            className="md-comparison-row"
            onClick={() => navigate(`/mentorships/${r.id}`)}
            title={`Open mentorship with ${r.name}`}
          >
            <span className="md-comparison-name">{r.name}</span>
            <span className="md-comparison-bar" aria-hidden="true">
              <span className="md-comparison-bar-fill" style={{ width: `${r.pct}%` }} />
            </span>
            <span className="md-comparison-pct">{r.pct}%</span>
            <span className="md-comparison-activity">
              {r.lastActivityAt ? `Last ${timeAgo(r.lastActivityAt)}` : 'No activity yet'}
            </span>
          </button>
        ))}
      </div>
    </section>
  )
}

function timeAgo(iso) {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  if (!Number.isFinite(t)) return ''
  const diff = Date.now() - t
  const m = Math.floor(diff / 60000)
  if (m < 1) return 'just now'
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.floor(h / 24)
  if (d < 7) return `${d}d ago`
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}
