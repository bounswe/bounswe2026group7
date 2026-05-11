import { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getNotifications, markNotificationAsRead, markAllNotificationsAsRead } from '../services/api'
import { timeAgo } from '../utils/timeAgo'

function TypeIcon({ type }) {
  switch (type) {
    case 'REQUEST_ACCEPTED':
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <polyline points="20 6 9 17 4 12" />
        </svg>
      )
    case 'REQUEST_REJECTED':
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      )
    case 'REQUEST_SUBMITTED':
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <path d="M22 2L11 13" />
          <polygon points="22 2 15 22 11 13 2 9 22 2" />
        </svg>
      )
    case 'TASK_DEADLINE_REMINDER':
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <circle cx="12" cy="12" r="10" />
          <polyline points="12 6 12 12 16 14" />
        </svg>
      )
    case 'MILESTONE_REMINDER':
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
          <line x1="4" y1="22" x2="4" y2="15" />
        </svg>
      )
    default:
      // Default bell icon for unknown types
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
      )
  }
}

function typeColorClass(type) {
  switch (type) {
    case 'REQUEST_ACCEPTED': return 'notif-icon--green'
    case 'REQUEST_REJECTED': return 'notif-icon--red'
    case 'TASK_DEADLINE_REMINDER': return 'notif-icon--orange'
    case 'MILESTONE_REMINDER': return 'notif-icon--purple'
    default: return 'notif-icon--blue'
  }
}

export default function NotificationBell() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [notifications, setNotifications] = useState([])
  const [filter, setFilter] = useState('all')
  const [loading, setLoading] = useState(false)
  const bellRef = useRef(null)

  const fetchNotifications = useCallback(() => {
    setLoading(true)
    getNotifications()
      .then(data => setNotifications(Array.isArray(data) ? data : []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    fetchNotifications()
  }, [fetchNotifications])

  // Re-fetch when panel opens
  useEffect(() => {
    if (open) fetchNotifications()
  }, [open, fetchNotifications])

  useEffect(() => {
    function handleClickOutside(e) {
      if (bellRef.current && !bellRef.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const unreadCount = notifications.filter(n => !n.read).length
  const displayed = filter === 'unread' ? notifications.filter(n => !n.read) : notifications

  function handleMarkRead(id) {
    // Optimistic update
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n))
    markNotificationAsRead(id).catch(() => {
      // Revert on failure
      setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: false } : n))
    })
  }

  function handleMarkAllRead() {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })))
    markAllNotificationsAsRead().catch(() => fetchNotifications())
  }

  return (
    <div className="bell-wrap" ref={bellRef}>
      <button
        className={`bell-btn${open ? ' bell-btn--open' : ''}`}
        onClick={() => setOpen(v => !v)}
        aria-label="Notifications"
        aria-expanded={open}
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        {unreadCount > 0 && (
          <span className="bell-badge">{unreadCount > 9 ? '9+' : unreadCount}</span>
        )}
      </button>

      <div className={`notif-panel${open ? ' notif-panel--open' : ''}`} role="dialog" aria-label="Notifications">
        {/* Header */}
        <div className="notif-header">
          <div className="notif-header-left">
            <span className="notif-title">Notifications</span>
            {unreadCount > 0 && <span className="notif-count">{unreadCount} new</span>}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            {unreadCount > 0 && (
              <button className="notif-mark-all" onClick={handleMarkAllRead}>
                Mark all read
              </button>
            )}
            <button
              className="notif-mark-all"
              onClick={fetchNotifications}
              title="Refresh"
              style={{ padding: '4px 6px' }}
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2"
                style={{ display: 'block', animation: loading ? 'spin 0.8s linear infinite' : 'none' }}>
                <polyline points="23 4 23 10 17 10" />
                <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
              </svg>
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div className="notif-tabs">
          <button
            className={`notif-tab${filter === 'all' ? ' notif-tab--active' : ''}`}
            onClick={() => setFilter('all')}
          >
            All
          </button>
          <button
            className={`notif-tab${filter === 'unread' ? ' notif-tab--active' : ''}`}
            onClick={() => setFilter('unread')}
          >
            Unread{unreadCount > 0 ? ` (${unreadCount})` : ''}
          </button>
        </div>

        {/* List */}
        <div className="notif-list">
          {loading && notifications.length === 0 ? (
            <div className="notif-empty">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"
                style={{ opacity: 0.3, animation: 'spin 0.8s linear infinite' }}>
                <polyline points="23 4 23 10 17 10" />
                <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
              </svg>
              <p>Loading…</p>
            </div>
          ) : displayed.length === 0 ? (
            <div className="notif-empty">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ opacity: 0.25 }}>
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
              <p>You're all caught up</p>
            </div>
          ) : (
            displayed.map(n => (
              <div
                key={n.id}
                className={`notif-item${!n.read ? ' notif-item--unread' : ''}`}
                onClick={() => {
                  if (!n.read) handleMarkRead(n.id)
                  setOpen(false)
                  const rid = n.relatedId || n.entityId
                  if (!rid) return
                  switch (n.type) {
                    case 'REQUEST_SUBMITTED':
                    case 'REQUEST_ACCEPTED':
                    case 'REQUEST_RECEIVED':
                      navigate(`/mentorships/${rid}`)
                      break
                    case 'TASK_DEADLINE_REMINDER':
                      navigate(`/tasks?mentorshipId=${rid}`)
                      break
                    case 'MILESTONE_REMINDER':
                      navigate(`/mentorships/${rid}#milestones`)
                      break
                    default:
                      // Fallback: No navigation for unknown types unless we want a default
                      break
                  }
                }}
              >
                <div className={`notif-icon ${typeColorClass(n.type)}`}>
                  <TypeIcon type={n.type} />
                </div>
                <div className="notif-body">
                  <p className="notif-item-title">{n.title}</p>
                  <p className="notif-item-body">{n.body}</p>
                  <span className="notif-time">{timeAgo(n.createdAt)}</span>
                </div>
                {!n.read && <span className="notif-unread-dot" />}
              </div>
            ))
          )}
        </div>

        <button
          className="notif-see-all"
          onClick={() => { setOpen(false); navigate('/notifications') }}
        >
          See all notifications →
        </button>
      </div>
    </div>
  )
}
