import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import { getActiveMentorships } from '../services/api'
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

function relativeTime(iso) {
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1) return 'now'
  if (m < 60) return `${m}m`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h`
  const dys = Math.floor(h / 24)
  return `${dys}d`
}

function counterpart(m, role) {
  return role === 'MENTOR' ? m.menteeFirstName : m.mentorFirstName
}

export default function MessagesPage() {
  const [params] = useSearchParams()
  const mentorshipId = params.get('mentorshipId')
  const navigate = useNavigate()
  const { role } = useAuth()

  // Thread (when mentorshipId is in URL)
  const [messages, setMessages] = useState([])
  const [threadLoading, setThreadLoading] = useState(false)

  // Conversation list (when no mentorshipId)
  const [conversations, setConversations] = useState([])
  const [listLoading, setListLoading] = useState(false)

  useEffect(() => {
    let cancelled = false
    if (mentorshipId) {
      setThreadLoading(true)
      getMessagesThread(mentorshipId).then(data => {
        if (!cancelled) { setMessages(data); setThreadLoading(false) }
      })
    } else {
      setListLoading(true)
      getActiveMentorships()
        .then(async list => {
          const enriched = await Promise.all(
            (list || []).map(async m => {
              const thread = await getMessagesThread(m.id)
              const last = thread[thread.length - 1]
              return {
                mentorship: m,
                preview: last?.text || '',
                lastAt: last?.createdAt || null,
              }
            })
          )
          return enriched.sort((a, b) => new Date(b.lastAt || 0) - new Date(a.lastAt || 0))
        })
        .then(c => { if (!cancelled) { setConversations(c); setListLoading(false) } })
        .catch(() => setListLoading(false))
    }
    return () => { cancelled = true }
  }, [mentorshipId])

  if (!mentorshipId) {
    return (
      <MainLayout>
        <div className="page-header">
          <div>
            <div className="page-title">Messages</div>
            <div className="page-sub">Your active mentorship conversations</div>
          </div>
        </div>

        {listLoading ? (
          <div className="md-loading">Loading conversations…</div>
        ) : conversations.length === 0 ? (
          <div className="empty-state">No active conversations yet.</div>
        ) : (
          <div className="md-conv-list">
            {conversations.map(c => {
              const name = counterpart(c.mentorship, role) || '—'
              const initials = (name?.[0] || '?').toUpperCase()
              return (
                <button
                  key={c.mentorship.id}
                  className="md-conv-item"
                  onClick={() => navigate(`/messages?mentorshipId=${c.mentorship.id}`)}
                >
                  <Avatar initials={initials} size="md" />
                  <div className="md-conv-info">
                    <div className="md-conv-top">
                      <span className="md-conv-name">{name}</span>
                      {c.lastAt && <span className="md-conv-time">{relativeTime(c.lastAt)}</span>}
                    </div>
                    <div className="md-conv-preview">{c.preview || 'No messages yet'}</div>
                  </div>
                </button>
              )
            })}
          </div>
        )}
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

      {threadLoading ? (
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
