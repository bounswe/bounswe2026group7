import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import { listMentorships } from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

// AT-15 surface A. Two-tab "My Mentorships" view: Active vs Past. Both tabs
// share a single fetch (status=ALL, size=50) and split client-side so
// switching tabs is instant and the spinner only shows on first load. Privacy
// (req 1.1.2.5) is enforced upstream — the backend already redacts to
// counterparty's first name only — so this view simply renders what arrives.

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

export default function MyMentorshipsPage() {
  const navigate = useNavigate()
  const { userId } = useAuth()
  const [tab, setTab] = useState('ACTIVE')
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await listMentorships({ status: 'ALL', page: 0, size: 50 })
      // Server returns Spring Page<>; tolerate a bare array for older mocks.
      const content = Array.isArray(res) ? res : (res?.content || [])
      setRows(content)
    } catch (err) {
      setError(err?.message || 'Failed to load mentorships.')
      setRows([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const active = rows.filter(m => m.status === 'ACTIVE')
  const past = rows.filter(m => PAST_STATUSES.has(m.status))
  const visible = tab === 'ACTIVE' ? active : past

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <div className="page-title">My Mentorships</div>
          <div className="page-sub">Switch between your active and past mentorships.</div>
        </div>
      </div>

      <div className="feed-tabs" role="tablist" aria-label="Mentorship tabs">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'ACTIVE'}
          className={`feed-tab${tab === 'ACTIVE' ? ' feed-tab--active' : ''}`}
          onClick={() => setTab('ACTIVE')}
          data-testid="my-mentorships-tab-active"
        >
          Active
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'PAST'}
          className={`feed-tab${tab === 'PAST' ? ' feed-tab--active' : ''}`}
          onClick={() => setTab('PAST')}
          data-testid="my-mentorships-tab-past"
        >
          Past
        </button>
      </div>

      {error && (
        <div className="md-error-card" style={{ marginBottom: '12px' }} data-testid="my-mentorships-error">
          <div className="md-error-title">Couldn't load mentorships</div>
          <div className="md-error-sub">{error}</div>
        </div>
      )}

      {loading ? (
        <div className="md-loading" data-testid="my-mentorships-loading">Loading mentorships…</div>
      ) : visible.length === 0 ? (
        <div className="empty-state" data-testid={`my-mentorships-empty-${tab.toLowerCase()}`}>
          {tab === 'ACTIVE'
            ? 'You have no active mentorships.'
            : 'No past mentorships yet — they will appear here once your active ones end.'}
        </div>
      ) : (
        <ul className="ml-list" data-testid="my-mentorships-list">
          {visible.map(m => {
            const viewerIsMentor = String(userId) === String(m.mentorId)
            // Privacy: backend already redacts to a first name only; we just render it.
            const counterpartName = viewerIsMentor ? m.menteeFirstName : m.mentorFirstName
            const initials = (counterpartName?.[0] || '?').toUpperCase()
            return (
              <li key={m.id} className="ml-card">
                <button
                  type="button"
                  className="ml-card-btn"
                  onClick={() => navigate(`/mentorships/${m.id}`)}
                  aria-label={`Open mentorship with ${counterpartName || 'unknown user'}`}
                  data-testid={`my-mentorships-row-${m.id}`}
                >
                  <Avatar initials={initials} size="md" />
                  <div className="ml-card-main">
                    <div className="ml-card-row">
                      <span className="ml-card-name">{counterpartName || 'Unknown'}</span>
                      <span className={statusBadgeClass(m.status)}>{statusLabel(m.status)}</span>
                    </div>
                    <div className="ml-card-meta">
                      <span>{viewerIsMentor ? 'Mentee' : 'Mentor'}</span>
                      <span>·</span>
                      <span>Started {fmtDate(m.startDate)}</span>
                    </div>
                  </div>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </MainLayout>
  )
}
