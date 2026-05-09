import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import { getActiveMentorships } from '../services/api'
import { getMeetings, getMeetingsAcrossMentorships } from '../services/mentorshipMocks'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

function labelFor(mentorship, role) {
  if (!mentorship) return ''
  return role === 'MENTOR' ? mentorship.menteeFirstName : mentorship.mentorFirstName
}

export default function SchedulePage() {
  const [params] = useSearchParams()
  const scopedId = params.get('mentorshipId')
  const navigate = useNavigate()
  const { role } = useAuth()

  const [meetings, setMeetings] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)

    if (scopedId) {
      Promise.all([getMeetings(scopedId), getActiveMentorships().catch(() => [])])
        .then(([list, mentList]) => {
          if (cancelled) return
          const m = (mentList || []).find(x => String(x.id) === String(scopedId))
          setMeetings(list.map(meeting => ({
            ...meeting,
            mentorship: m ? { id: m.id, mentorFirstName: m.mentorFirstName, menteeFirstName: m.menteeFirstName } : null,
          })))
          setLoading(false)
        })
        .catch(() => setLoading(false))
    } else {
      getActiveMentorships()
        .then(list => getMeetingsAcrossMentorships(list || []))
        .then(m => { if (!cancelled) { setMeetings(m); setLoading(false) } })
        .catch(() => setLoading(false))
    }

    return () => { cancelled = true }
  }, [scopedId])

  const sorted = [...meetings].sort((a, b) => new Date(a.date) - new Date(b.date))
  const now = Date.now()
  const upcoming = sorted.filter(m => new Date(m.date).getTime() >= now)
  const past = sorted.filter(m => new Date(m.date).getTime() < now).reverse()

  function Section({ title, items }) {
    if (items.length === 0) return null
    return (
      <div style={{ marginBottom: '28px' }}>
        <div className="section-label" style={{ marginBottom: '12px' }}>{title}</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map(m => {
            const date = new Date(m.date)
            const mentLabel = !scopedId ? labelFor(m.mentorship, role) : null
            return (
              <div key={m.id} className="md-meeting-card" data-testid={`schedule-meeting-${m.id}`}>
                <div className="md-meeting-day">
                  <div className="md-meeting-day-num">{date.getDate()}</div>
                  <div className="md-meeting-day-mo">{date.toLocaleDateString('en-GB', { month: 'short' })}</div>
                </div>
                <div className="md-meeting-info">
                  <div className="md-meeting-title">
                    {m.title}
                    {mentLabel && <span className="md-task-ment"> · with {mentLabel}</span>}
                  </div>
                  <div className="md-meeting-time">
                    {date.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'short', year: 'numeric' })}
                    {' · '}
                    {date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}
                    {' · '}
                    {m.durationMin} min
                  </div>
                </div>
                <span className={`md-meeting-status md-meeting-status-${m.status.toLowerCase()}`}>
                  {m.status.charAt(0) + m.status.slice(1).toLowerCase()}
                </span>
              </div>
            )
          })}
        </div>
      </div>
    )
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
            {scopedId ? 'Meetings for this mentorship' : 'All your upcoming meetings'}
          </div>
        </div>
      </div>

      {loading ? (
        <div className="md-loading">Loading meetings…</div>
      ) : sorted.length === 0 ? (
        <div className="empty-state" data-testid="schedule-empty">No meetings scheduled.</div>
      ) : (
        <div data-testid="schedule-list">
          <Section title="Upcoming" items={upcoming} />
          <Section title="Past" items={past} />
        </div>
      )}
    </MainLayout>
  )
}
