import { useEffect, useState, useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import ChatComposer from '../components/ChatComposer'
import MessageAttachment from '../components/MessageAttachment'
import { linkify } from '../utils/linkify'
import {
  getActiveMentorships,
  getMentorshipMessages,
  sendMentorshipMessage,
  markMentorshipMessagesRead,
  getMentorPairInbox,
  getMentorPairMessages,
  sendMentorPairMessage,
  markMentorPairMessagesRead,
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

function mentorshipCounterpart(m, role) {
  return role === 'MENTOR' ? m.menteeFirstName : m.mentorFirstName
}

function renderMessageText(content) {
  return linkify(content).map((part, i) => {
    if (part && typeof part === 'object' && part.kind === 'url') {
      return (
        <a
          key={i}
          href={part.url}
          target="_blank"
          rel="noopener noreferrer"
          className="md-message-link"
        >
          {part.url}
        </a>
      )
    }
    return <span key={i}>{part}</span>
  })
}

// Backend pages messages newest-first; UI renders oldest-first so we reverse.
function pageToOldestFirst(page) {
  const content = page?.content || []
  return [...content].reverse()
}

export default function MessagesPage() {
  const [params] = useSearchParams()
  const mentorshipId = params.get('mentorshipId')
  const peerId = params.get('peerId')
  const navigate = useNavigate()
  const { role, userId } = useAuth()
  const isMentor = role === 'MENTOR'

  // ── Polymorphic chat-route descriptor ────────────────────────────────────
  // Either ?mentorshipId= (mentee↔mentor) or ?peerId= (mentor↔mentor) selects
  // the chat. The descriptor encapsulates which API to call so the rest of
  // the page is endpoint-agnostic.
  const chatRoute = useMemo(() => {
    if (mentorshipId) {
      return {
        kind: 'MENTORSHIP',
        key: mentorshipId,
        loadMessages: (k, page, size) => getMentorshipMessages(k, page, size),
        send: (k, body) => sendMentorshipMessage(k, body),
        markRead: (k) => markMentorshipMessagesRead(k),
        backHref: `/mentorships/${mentorshipId}`,
        backLabel: '← Back to mentorship',
      }
    }
    if (peerId) {
      return {
        kind: 'MENTOR_PAIR',
        key: peerId,
        loadMessages: (k, page, size) => getMentorPairMessages(k, page, size),
        send: (k, body) => sendMentorPairMessage(k, body),
        markRead: (k) => markMentorPairMessagesRead(k),
        backHref: `/users/${peerId}`,
        backLabel: '← Back to profile',
      }
    }
    return null
  }, [mentorshipId, peerId])

  // Thread state
  const [messages, setMessages] = useState([])
  const [conversationId, setConversationId] = useState(null)
  const [threadLoading, setThreadLoading] = useState(false)
  const [threadError, setThreadError] = useState(null)

  // Conversation list state
  const [conversations, setConversations] = useState([])
  const [listLoading, setListLoading] = useState(false)

  // ── Thread loader: real API ───────────────────────────────────────────────
  useEffect(() => {
    if (!chatRoute) return undefined
    let cancelled = false
    setMessages([])
    setConversationId(null)
    setThreadLoading(true)
    setThreadError(null)
    chatRoute.loadMessages(chatRoute.key, 0, 50)
      .then(page => {
        if (cancelled) return
        const ordered = pageToOldestFirst(page)
        setMessages(ordered)
        if (ordered.length > 0 && ordered[0].conversationId != null) {
          setConversationId(ordered[0].conversationId)
        }
      })
      .catch(err => { if (!cancelled) setThreadError(err.message || 'Failed to load messages') })
      .finally(() => { if (!cancelled) setThreadLoading(false) })
    return () => { cancelled = true }
  }, [chatRoute])

  // ── Mark messages as read once a thread is open ───────────────────────────
  useEffect(() => {
    if (!chatRoute) return
    chatRoute.markRead(chatRoute.key).catch(() => { /* ignore */ })
  }, [chatRoute, messages.length])

  // ── Live updates via STOMP ────────────────────────────────────────────────
  useConversationSubscription(conversationId, (incoming) => {
    setMessages(prev => {
      if (prev.some(m => m.id === incoming.id)) return prev
      return [...prev, incoming]
    })
  })

  // ── Conversation list loader: merge mentorship + mentor-pair inboxes ──────
  useEffect(() => {
    if (chatRoute) return undefined
    let cancelled = false
    setListLoading(true)

    async function loadMentorshipConvs() {
      try {
        const list = await getActiveMentorships()
        return await Promise.all(
          (list || []).map(async m => {
            try {
              const page = await getMentorshipMessages(m.id, 0, 1)
              const last = page?.content?.[0] || null
              return {
                kind: 'MENTORSHIP',
                id: `mentorship-${m.id}`,
                href: `/messages?mentorshipId=${m.id}`,
                name: mentorshipCounterpart(m, role) || '—',
                preview: last?.content || '',
                lastAt: last?.sentAt || null,
              }
            } catch {
              return {
                kind: 'MENTORSHIP',
                id: `mentorship-${m.id}`,
                href: `/messages?mentorshipId=${m.id}`,
                name: mentorshipCounterpart(m, role) || '—',
                preview: '',
                lastAt: null,
              }
            }
          })
        )
      } catch {
        return []
      }
    }

    async function loadMentorPairConvs() {
      // Only mentors have a mentor-pair inbox (backend gates with hasRole('MENTOR'))
      if (!isMentor) return []
      try {
        const inbox = await getMentorPairInbox(0, 50)
        return (inbox?.content || []).map(item => ({
          kind: 'MENTOR_PAIR',
          id: `pair-${item.peerId}`,
          href: `/messages?peerId=${item.peerId}`,
          name: item.peerFirstName || '—',
          preview: item.lastMessageContent || '',
          lastAt: item.lastMessageSentAt || null,
        }))
      } catch {
        return []
      }
    }

    Promise.all([loadMentorshipConvs(), loadMentorPairConvs()])
      .then(([mentorships, pairs]) => {
        const merged = [...mentorships, ...pairs]
          .sort((a, b) => new Date(b.lastAt || 0) - new Date(a.lastAt || 0))
        if (!cancelled) setConversations(merged)
      })
      .catch(() => { if (!cancelled) setConversations([]) })
      .finally(() => { if (!cancelled) setListLoading(false) })

    return () => { cancelled = true }
  }, [chatRoute, role, isMentor])

  // ── Send handler ──────────────────────────────────────────────────────────
  // Backend requires non-empty content even when an attachment is present, so
  // we substitute a single space when the user only sends a file.
  async function handleSend(content, attachment) {
    if (!chatRoute) return
    const safeContent = content && content.length > 0 ? content : ' '
    const payload = { content: safeContent }
    if (attachment?.id) payload.attachmentId = attachment.id
    const created = await chatRoute.send(chatRoute.key, payload)
    setMessages(prev => {
      if (prev.some(m => m.id === created.id)) return prev
      return [...prev, created]
    })
    if (created.conversationId != null && conversationId == null) {
      setConversationId(created.conversationId)
    }
  }

  // ── Conversation list view ────────────────────────────────────────────────
  if (!chatRoute) {
    return (
      <MainLayout>
        <div className="page-header">
          <div>
            <div className="page-title">Messages</div>
            <div className="page-sub">Your conversations</div>
          </div>
        </div>

        {listLoading ? (
          <div className="md-loading">Loading conversations…</div>
        ) : conversations.length === 0 ? (
          <div className="empty-state">No conversations yet.</div>
        ) : (
          <div className="md-conv-list">
            {conversations.map(c => {
              const initials = (c.name?.[0] || '?').toUpperCase()
              return (
                <button
                  key={c.id}
                  className="md-conv-item"
                  onClick={() => navigate(c.href)}
                  data-testid={`messages-conversation-${c.id}`}
                >
                  <Avatar initials={initials} size="md" />
                  <div className="md-conv-info">
                    <div className="md-conv-top">
                      <span className="md-conv-name">{c.name}</span>
                      {c.kind === 'MENTOR_PAIR' && (
                        <span className="md-conv-badge">Mentor</span>
                      )}
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
            onClick={() => navigate(chatRoute.backHref)}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px', padding: 0, marginBottom: '8px' }}
          >
            {chatRoute.backLabel}
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
          <div className="md-message-thread" data-testid="messages-thread">
            {messages.length === 0 ? (
              <div className="empty-state" data-testid="messages-empty">No messages yet. Start the conversation below.</div>
            ) : messages.map(m => {
              // userId from auth context is stringified; senderId from API is a number
              const mine = String(m.senderId) === String(userId)
              return (
                <div
                  key={m.id}
                  className={`md-message-bubble${mine ? ' md-message-mine' : ''}`}
                  data-testid={`messages-bubble-${m.id}`}
                  data-message-mine={mine ? 'true' : 'false'}
                >
                  <div className="md-message-text" data-testid="messages-bubble-text">{renderMessageText(m.content)}</div>
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
