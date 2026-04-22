import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import { getMessagesThread } from '../services/mentorshipMocks'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

function formatTime(iso) {
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  return sameDay
    ? d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}

export default function MessagesPage() {
  const [params] = useSearchParams()
  const mentorshipId = params.get('mentorshipId')
  const navigate = useNavigate()
  const { role } = useAuth()

  const [messages, setMessages] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!mentorshipId) {
      setLoading(false)
      return
    }
    getMessagesThread(mentorshipId).then(data => {
      setMessages(data)
      setLoading(false)
    })
  }, [mentorshipId])

  if (!mentorshipId) {
    return (
      <MainLayout>
        <div className="page-header">
          <div>
            <div className="page-title">Messages</div>
            <div className="page-sub">Select a mentorship to open its conversation</div>
          </div>
        </div>
        <div className="md-error-card">
          <div className="md-error-title">No conversation selected</div>
          <div className="md-error-sub">Open this page from an active mentorship to see its messages.</div>
          <button className="action-btn" onClick={() => navigate('/home')}>Back to Home</button>
        </div>
      </MainLayout>
    )
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <button
            onClick={() => navigate(`/mentorships/${mentorshipId}`)}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px', padding: 0, marginBottom: '8px' }}
          >
            ← Back to mentorship
          </button>
          <div className="page-title">Messages</div>
        </div>
      </div>

      {loading ? (
        <div className="md-loading">Loading messages…</div>
      ) : (
        <div className="md-message-thread">
          {messages.map(m => {
            const mine = m.senderRole === role
            return (
              <div
                key={m.id}
                className={`md-message-bubble${mine ? ' md-message-mine' : ''}`}
              >
                <div className="md-message-text">{m.text}</div>
                <div className="md-message-time">{formatTime(m.createdAt)}</div>
              </div>
            )
          })}
        </div>
      )}
    </MainLayout>
  )
}
