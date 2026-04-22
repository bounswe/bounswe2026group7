import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import { getMeetings } from '../services/mentorshipMocks'
import '../styles/main.css'

export default function MentorshipMeetingsPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [meetings, setMeetings] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getMeetings(id).then(data => {
      setMeetings(data)
      setLoading(false)
    })
  }, [id])

  const sorted = [...meetings].sort((a, b) => new Date(a.date) - new Date(b.date))

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <button
            onClick={() => navigate(`/mentorships/${id}`)}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px', padding: 0, marginBottom: '8px' }}
          >
            ← Back to mentorship
          </button>
          <div className="page-title">Meetings</div>
          <div className="page-sub">All scheduled meetings for this mentorship</div>
        </div>
      </div>

      {loading ? (
        <div className="md-loading">Loading meetings…</div>
      ) : sorted.length === 0 ? (
        <div className="empty-state">No meetings yet.</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {sorted.map(m => {
            const date = new Date(m.date)
            return (
              <div key={m.id} className="md-meeting-card">
                <div className="md-meeting-day">
                  <div className="md-meeting-day-num">{date.getDate()}</div>
                  <div className="md-meeting-day-mo">{date.toLocaleDateString('en-GB', { month: 'short' })}</div>
                </div>
                <div className="md-meeting-info">
                  <div className="md-meeting-title">{m.title}</div>
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
      )}
    </MainLayout>
  )
}
