import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import ChatComposer from '../components/ChatComposer'
import MessageAttachment from '../components/MessageAttachment'
import {
  getActiveMentorships,
  getMentorshipMessages,
  sendMentorshipMessage,
  markMentorshipMessagesRead,
} from '../services/api'
import { uploadMessageAttachment } from '../services/attachmentService'
import useConversationSubscription from '../hooks/useConversationSubscription'
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

// Backend pages messages newest-first; UI renders oldest-first so we reverse.
function pageToOldestFirst(page) {
  const content = page?.content || []
  return [...content].reverse()
}

export default function MessagesPage() {
  const [params] = useSearchParams()
  const mentorshipId = params.get('mentorshipId')
  const navigate = useNavigate()
  const { role, userId } = useAuth()

  // Thread state (when mentorshipId is in URL)
  const [messages, setMessages] = useState([])
  const [conversationId, setConversationId] = useState(null)
  const [threadLoading, setThreadLoading] = useState(false)
  const [threadError, setThreadError] = useState(null)

  // Conversation list state (when no mentorshipId)
  const [conversations, setConversations] = useState([])
  const [listLoading, setListLoading] = useState(false)

  // ── Thread loader: real API ───────────────────────────────────────────────
  useEffect(() => {
    if (!mentorshipId) return undefined
    let cancelled = false
    setThreadLoading(true)
    setThreadError(null)
    getMentorshipMessages(mentorshipId, 0, 50)
      .then(page => {
        if (cancelled) return
        const ordered = pageToOldestFirst(page)
        setMessages(ordered)
        // Pull conversationId off the first message if any. For an empty
        // conversation it stays null until the first send response carries it.
        if (ordered.length > 0 && ordered[0].conversationId != null) {
          setConversationId(ordered[0].conversationId)
        }
      })
      .catch(err => { if (!cancelled) setThreadError(err.message || 'Failed to load messages') })
      .finally(() => { if (!cancelled) setThreadLoading(false) })
    return () => { cancelled = true }
  }, [mentorshipId])

  // ── Mark messages as read once a thread is open ───────────────────────────
  useEffect(() => {
    if (!mentorshipId) return
    // Best-effort, ignore failures — read receipts are non-critical for the chat baseline
    markMentorshipMessagesRead(mentorshipId).catch(() => { /* ignore */ })
  }, [mentorshipId, messages.length])

  // ── Live updates via STOMP ────────────────────────────────────────────────
  useConversationSubscription(conversationId, (incoming) => {
    setMessages(prev => {
      // Skip duplicates: backend re-broadcasts after AFTER_COMMIT, which races
      // with the optimistic append done in handleSend.
      if (prev.some(m => m.id === incoming.id)) return prev
      return [...prev, incoming]
    })
  })

  // ── Conversation list loader: real API previews ───────────────────────────
  useEffect(() => {
    if (mentorshipId) return undefined
    let cancelled = false
    setListLoading(true)
    getActiveMentorships()
      .then(async list => {
        const enriched = await Promise.all(
          (list || []).map(async m => {
            try {
              const page = await getMentorshipMessages(m.id, 0, 1)
              const last = page?.content?.[0] || null
              return {
                kind: 'MENTORSHIP',
                mentorship: m,
                preview: last?.content || '',
                lastAt: last?.sentAt || null,
              }
            } catch {
              return { kind: 'MENTORSHIP', mentorship: m, preview: '', lastAt: null }
            }
          })
        )
        return enriched.sort((a, b) => new Date(b.lastAt || 0) - new Date(a.lastAt || 0))
      })
      .then(c => { if (!cancelled) setConversations(c) })
      .catch(() => { if (!cancelled) setConversations([]) })
      .finally(() => { if (!cancelled) setListLoading(false) })
    return () => { cancelled = true }
  }, [mentorshipId])

  // ── Send handler ──────────────────────────────────────────────────────────
  // Backend requires non-empty content even when an attachment is present, so
  // we substitute a single space when the user only sends a file.
  async function handleSend(content, attachment) {
    const safeContent = content && content.length > 0 ? content : ' '
    const payload = { content: safeContent }
    if (attachment?.id) payload.attachmentId = attachment.id
    const created = await sendMentorshipMessage(mentorshipId, payload)
    setMessages(prev => {
      if (prev.some(m => m.id === created.id)) return prev
      return [...prev, created]
    })
    if (created.conversationId != null && conversationId == null) {
      setConversationId(created.conversationId)
    }
  }

  // ── Conversation list view ────────────────────────────────────────────────
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
                  key={`${c.kind}-${c.mentorship.id}`}
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

  // ── Thread view ────────────────────────────────────────────────────────────
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
      ) : threadError ? (
        <div className="md-error-card">
          <div className="md-error-title">Couldn’t load messages</div>
          <div className="md-error-sub">{threadError}</div>
        </div>
      ) : (
        <>
          <div className="md-message-thread">
            {messages.length === 0 ? (
              <div className="empty-state">No messages yet. Start the conversation below.</div>
            ) : messages.map(m => {
              // userId from auth context is stringified; senderId from API is a number
              const mine = String(m.senderId) === String(userId)
              return (
                <div
                  key={m.id}
                  className={`md-message-bubble${mine ? ' md-message-mine' : ''}`}
                >
                  <div className="md-message-text">{m.content}</div>
                  {m.attachment && (
                    <MessageAttachment attachment={m.attachment} mine={mine} />
                  )}
                  <div className="md-message-time">{formatTime(m.sentAt)}</div>
                </div>
              )
            })}
          </div>
          <ChatComposer
            onSend={handleSend}
            onUpload={uploadMessageAttachment}
            placeholder="Type a message…"
          />
        </>
      )}
    </MainLayout>
  )
}
