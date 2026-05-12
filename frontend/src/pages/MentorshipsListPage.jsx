import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import { getMentorshipsByStatus } from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

/**
 * "My Mentorships" history page (#408). Backed by the paginated
 * GET /api/mentorships?status= endpoint that shipped in backend #529 / #521.
 *
 * Single tabbed view: All / Active / Past. Backend sorts by endDate DESC
 * NULLS LAST so active mentorships float to the top of the All tab. Each
 * row renders the counterpart's name + avatar, date range, status badge,
 * and clicks through to the detail page.
 */

const TABS = [
  { key: 'ALL', label: 'All' },
  { key: 'ACTIVE', label: 'Active' },
  { key: 'PAST', label: 'Past' }, // client-side fan-out — server has no PAST filter
]

// Past tab pulls every non-active state. Backend rejects unknown values, so we
// can't pass "PAST" through the wire; instead we fetch with status=ALL and
// filter client-side. With size=20 this is a single page in most cases.
const PAST_STATUSES = new Set(['COMPLETED', 'CANCELLED', 'TERMINATED'])

function fmtDate(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (isNaN(d.getTime())) return '—'
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

function statusBadgeClass(status) {
  switch (status) {
    case 'ACTIVE': return 'ml-status ml-status--active'
    case 'COMPLETED': return 'ml-status ml-status--completed'
    case 'CANCELLED': return 'ml-status ml-status--cancelled'
    case 'TERMINATED': return 'ml-status ml-status--terminated'
    default: return 'ml-status'
  }
}

function statusLabel(status) {
  switch (status) {
    case 'ACTIVE': return 'Active'
    case 'COMPLETED': return 'Completed'
    case 'CANCELLED': return 'Cancelled'
    case 'TERMINATED': return 'Terminated'
    default: return status || 'Unknown'
  }
}

export default function MentorshipsListPage() {
  const navigate = useNavigate()
  const { userId } = useAuth()
  const [tab, setTab] = useState('ALL')
  const [data, setData] = useState(null) // Page<MentorshipResponse>
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // Past tab uses ALL + client-side filter so we still get paginated payloads.
      const requestedStatus = tab === 'PAST' ? 'ALL' : tab
      const res = await getMentorshipsByStatus({ status: requestedStatus, page, size: 20 })
      setData(res || { content: [], totalPages: 1 })
    } catch (err) {
      setError(err?.message || 'Failed to load mentorships.')
      setData({ content: [], totalPages: 1 })
    } finally {
      setLoading(false)
    }
  }, [tab, page])

  useEffect(() => { load() }, [load])

  function changeTab(next) {
    if (next === tab) return
    setTab(next)
    setPage(0)
  }

  const visible = (() => {
    const all = data?.content || []
    if (tab === 'PAST') return all.filter(m => PAST_STATUSES.has(m.status))
    return all
  })()

  const totalPages = data?.totalPages ?? 1
  const isFirst = page === 0
  const isLast = page >= totalPages - 1

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <div className="page-title">My Mentorships</div>
          <div className="page-sub">Every mentorship you've participated in, active or past.</div>
        </div>
      </div>

      <div className="feed-tabs" role="tablist" aria-label="Mentorship status filter">
        {TABS.map(t => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={`feed-tab${tab === t.key ? ' feed-tab--active' : ''}`}
            onClick={() => changeTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && (
        <div className="md-error-card" style={{ marginBottom: '12px' }}>
          <div className="md-error-title">Couldn’t load mentorships</div>
          <div className="md-error-sub">{error}</div>
        </div>
      )}

      {loading ? (
        <div className="md-loading">Loading mentorships…</div>
      ) : visible.length === 0 ? (
        <div className="empty-state">
          {tab === 'ACTIVE'
            ? 'You have no active mentorships.'
            : tab === 'PAST'
              ? 'No past mentorships yet — they will appear here once your active ones end.'
              : "You haven't participated in any mentorships yet."}
        </div>
      ) : (
        <ul className="ml-list" data-testid="my-mentorships-list">
          {visible.map(m => {
            const viewerIsMentor = String(userId) === String(m.mentorId)
            const counterpartName = viewerIsMentor ? m.menteeFirstName : m.mentorFirstName
            const counterpartId = viewerIsMentor ? m.menteeId : m.mentorId
            const initials = (counterpartName?.[0] || '?').toUpperCase()
            return (
              <li key={m.id} className="ml-card">
                <button
                  type="button"
                  className="ml-card-btn"
                  onClick={() => navigate(`/mentorships/${m.id}`)}
                  aria-label={`Open mentorship with ${counterpartName || 'unknown user'}`}
                >
                  <Avatar initials={initials} size="md" />
                  <div className="ml-card-main">
                    <div className="ml-card-row">
                      <span className="ml-card-name">{counterpartName || 'Unknown'}</span>
                      <span className={statusBadgeClass(m.status)}>{statusLabel(m.status)}</span>
                    </div>
                    <div className="ml-card-meta">
                      <span>
                        {viewerIsMentor ? 'Mentee' : 'Mentor'}
                        {counterpartId ? ` · #${counterpartId}` : ''}
                      </span>
                      <span>·</span>
                      <span>
                        {fmtDate(m.startDate)} – {fmtDate(m.endDate)}
                      </span>
                      {m.duration != null && (
                        <>
                          <span>·</span>
                          <span>{m.duration} month{m.duration !== 1 ? 's' : ''}</span>
                        </>
                      )}
                    </div>
                  </div>
                </button>
              </li>
            )
          })}
        </ul>
      )}

      {!loading && !error && totalPages > 1 && (
        <div className="follow-pagination" style={{ marginTop: '16px' }}>
          <button
            type="button"
            className="action-btn"
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={isFirst}
          >
            Previous
          </button>
          <div className="follow-page-indicator">
            Page {page + 1} of {totalPages}
          </div>
          <button
            type="button"
            className="action-btn"
            onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
            disabled={isLast}
          >
            Next
          </button>
        </div>
      )}
    </MainLayout>
  )
}
