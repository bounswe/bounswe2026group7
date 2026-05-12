import { useEffect, useMemo, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getMentorshipProgress, getMentorshipTimeline } from '../services/api'

/**
 * #126 + #333 — Mentorship Progress Timeline.
 *
 * Bundles two backends into one visual:
 *   - GET /api/mentorships/{id}/progress  → numeric ratio + counts + lastActivityAt
 *   - GET /api/mentorships/{id}/timeline  → startDate/endDate/currentDate + items[]
 *
 * Items are positioned on a horizontal ribbon by:
 *   x = (item.occursAt - startDate) / (endDate - startDate),  clamped to [0, 1]
 * Likewise the "Today" marker uses the server-provided `currentDate`.
 *
 * Visuals (mirroring the design reference):
 *   - Anchor circles at the start and end with the date underneath
 *   - Milestones render as raised blue pills above the line with a connector
 *   - Meetings + Tasks render as small dots ON the line
 *   - A dashed orange "Today" vertical with a label
 *   - A header row with the percent-complete badge + program-duration text
 *
 * Clicking any item navigates to its `detailUrl` from the backend.
 */
export default function MentorshipProgressTimeline({ mentorshipId, refreshKey = 0 }) {
  const navigate = useNavigate()
  const [progress, setProgress] = useState(null)
  const [timeline, setTimeline] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // `refreshKey` lets a parent (MentorshipDetailPage) signal a refetch after
  // a milestone mutation without remounting the component — which would
  // wipe loading and scroll state. The previous progress/timeline values are
  // intentionally kept in state while the refetch runs (stale-while-revalidate)
  // so the ribbon doesn't flash empty between mutations.
  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [p, t] = await Promise.all([
        getMentorshipProgress(mentorshipId),
        getMentorshipTimeline(mentorshipId),
      ])
      setProgress(p)
      setTimeline(t)
    } catch (err) {
      setError(err?.message || 'Failed to load progress')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mentorshipId, refreshKey])

  useEffect(() => { reload() }, [reload])

  const positions = useMemo(() => {
    if (!timeline?.startDate || !timeline?.endDate) return null
    const start = new Date(timeline.startDate).getTime()
    const end = new Date(timeline.endDate).getTime()
    const span = end - start
    if (!Number.isFinite(span) || span <= 0) return null
    const todayRaw = new Date(timeline.currentDate || Date.now()).getTime()
    const today = clampPct((todayRaw - start) / span)
    const items = (timeline.items || [])
      .map(it => {
        const t = new Date(it.occursAt).getTime()
        if (!Number.isFinite(t)) return null
        return { ...it, x: clampPct((t - start) / span) }
      })
      .filter(Boolean)
    return { items, today, start, end, span }
  }, [timeline])

  const milestones = useMemo(
    () => positions ? positions.items.filter(i => i.type === 'MILESTONE') : [],
    [positions]
  )
  const events = useMemo(
    () => positions ? positions.items.filter(i => i.type !== 'MILESTONE') : [],
    [positions]
  )

  // Stagger milestone pills above the bar to avoid horizontal overlap when
  // two milestones are within ~8% of each other on the axis.
  const stackedMilestones = useMemo(() => {
    const sorted = milestones.slice().sort((a, b) => a.x - b.x)
    let prevX = -100
    let row = 0
    return sorted.map(m => {
      if (m.x - prevX < 8) row = (row + 1) % 2
      else row = 0
      prevX = m.x
      return { ...m, row }
    })
  }, [milestones])

  return (
    <section className="card md-progress">
      <div className="md-progress-header">
        <div>
          <div className="section-label" style={{ marginBottom: 4 }}>Mentorship Progress Timeline</div>
          <div className="md-progress-meta">
            {timeline?.startDate && timeline?.endDate && (
              <>Program duration: {humanDuration(timeline.startDate, timeline.endDate)}</>
            )}
            {progress?.lastActivityAt && (
              <> · Last activity: {timeAgo(progress.lastActivityAt)}</>
            )}
          </div>
        </div>
        {progress && (
          <div className="md-progress-percent" title="Overall progress (tasks + milestones)">
            <div className="md-progress-percent-num">{Math.round((progress.progressRatio || 0) * 100)}%</div>
            <div className="md-progress-percent-sub">complete</div>
          </div>
        )}
      </div>

      {loading ? (
        <div className="md-goal-empty">Loading progress…</div>
      ) : error ? (
        <div className="md-error-card">
          <div className="md-error-title">Couldn’t load progress</div>
          <div className="md-error-sub">{error}</div>
        </div>
      ) : !positions ? (
        <div className="md-goal-empty">
          {timeline?.items?.length === 0
            ? 'No tasks, meetings, or milestones yet.'
            : 'Mentorship duration is not set yet — timeline will appear once start and end dates are known.'}
        </div>
      ) : (
        <>
          {/* Timeline ribbon */}
          <div className="progress-ribbon">
            {/* Stacked milestone pills above the bar */}
            <div className="progress-ribbon-pills">
              {stackedMilestones.map(m => (
                <button
                  key={`m-${m.id}`}
                  type="button"
                  className={`progress-pill progress-pill--row${m.row}`}
                  style={{ left: `${m.x}%` }}
                  onClick={() => navigate(absolutePathFromDetailUrl(m.detailUrl) || '/home')}
                  title={`${m.title} · ${formatDate(m.occursAt)}`}
                  data-testid={`timeline-milestone-${m.id}`}
                >
                  {m.title}
                </button>
              ))}
            </div>

            {/* Bar with start/end anchors + event dots */}
            <div className="progress-ribbon-bar">
              <div className="progress-anchor progress-anchor--start" data-testid="timeline-start" />
              <div className="progress-anchor progress-anchor--end" data-testid="timeline-end" />

              {events.map(it => (
                <button
                  key={`${it.type}-${it.id}`}
                  type="button"
                  className={`progress-dot progress-dot--${it.type.toLowerCase()}`}
                  style={{ left: `${it.x}%` }}
                  onClick={() => navigate(absolutePathFromDetailUrl(it.detailUrl) || '/home')}
                  title={`${typeLabel(it.type)}: ${it.title} · ${formatDate(it.occursAt)}`}
                  aria-label={`${typeLabel(it.type)} ${it.title}`}
                  data-testid={`timeline-event-${it.id}`}
                />
              ))}

              {/* Today marker — only shown if it falls within the program window */}
              {positions.today >= 0 && positions.today <= 100 && (
                <div className="progress-today" style={{ left: `${positions.today}%` }} data-testid="timeline-today">
                  <span className="progress-today-label">Today</span>
                </div>
              )}
            </div>

            {/* Endpoint labels */}
            <div className="progress-ribbon-labels">
              <div className="progress-label progress-label--start">
                <div className="progress-label-name">Program Start</div>
                <div className="progress-label-date">{formatShortDate(timeline.startDate)}</div>
              </div>
              <div className="progress-label progress-label--end">
                <div className="progress-label-name">Program Completion</div>
                <div className="progress-label-date">{formatShortDate(timeline.endDate)}</div>
              </div>
            </div>
          </div>

          {/* Compact summary chips below the ribbon */}
          <div className="md-progress-stats">
            <ProgressStat
              label="Tasks"
              done={progress.taskCompleted}
              total={progress.taskTotal}
              extra={progress.taskSubmitted > 0 ? `${progress.taskSubmitted} awaiting review` : null}
            />
            <ProgressStat
              label="Milestones"
              done={progress.milestoneCompleted}
              total={progress.milestoneTotal}
            />
            <ProgressStat
              label="Meetings"
              count={(timeline.items || []).filter(i => i.type === 'MEETING').length}
            />
          </div>
        </>
      )}
    </section>
  )
}

function ProgressStat({ label, done, total, count, extra }) {
  const showRatio = done != null && total != null
  return (
    <div className="md-progress-stat">
      <div className="md-progress-stat-num">
        {showRatio ? `${done}/${total}` : (count ?? 0)}
      </div>
      <div className="md-progress-stat-lbl">{label}</div>
      {extra && <div className="md-progress-stat-extra">{extra}</div>}
    </div>
  )
}

// ── Helpers ────────────────────────────────────────────────────────────────

function clampPct(v) {
  if (!Number.isFinite(v)) return 0
  return Math.max(0, Math.min(1, v)) * 100
}

function formatDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

function formatShortDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

function humanDuration(startIso, endIso) {
  const start = new Date(startIso).getTime()
  const end = new Date(endIso).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end)) return ''
  const diffMs = end - start
  const days = Math.round(diffMs / 86400000)
  if (days < 14) return `${days} days`
  const months = Math.round(days / 30)
  return `${months} month${months === 1 ? '' : 's'}`
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
  return formatShortDate(iso)
}

function typeLabel(type) {
  switch (type) {
    case 'MEETING': return 'Meeting'
    case 'TASK': return 'Task'
    case 'MILESTONE': return 'Milestone'
    default: return type
  }
}

// Backend's `detailUrl` is `/api/meetings/{id}` etc. — strip the `/api` prefix
// to navigate within the SPA. Returns null when the URL doesn't match the pattern.
function absolutePathFromDetailUrl(detailUrl) {
  if (!detailUrl) return null
  const path = String(detailUrl).replace(/^\/api/, '')
  // Map the backend resource paths to the SPA routes
  if (path.startsWith('/meetings/')) return path  // will use existing /meetings/:id when wired by #337
  if (path.startsWith('/tasks/')) return '/tasks'  // tasks page; could deep-link in a follow-up
  if (path.startsWith('/milestones/')) return null // milestones live inside the mentorship card; no standalone route
  return path
}
