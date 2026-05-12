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
    case 'FEED_LIKE':
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
        </svg>
      )
    case 'FEED_COMMENT':
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
      )
    case 'FEED_SHARE':
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <circle cx="18" cy="5" r="3" />
          <circle cx="6" cy="12" r="3" />
          <circle cx="18" cy="19" r="3" />
          <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
          <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
        </svg>
      )
    case 'NEW_FOLLOWER':
      return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="8.5" cy="7" r="4" />
          <line x1="20" y1="8" x2="20" y2="14" />
          <line x1="23" y1="11" x2="17" y2="11" />
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
    case 'FEED_LIKE': return 'notif-icon--red'
    case 'FEED_COMMENT': return 'notif-icon--blue'
    case 'FEED_SHARE': return 'notif-icon--green'
    case 'NEW_FOLLOWER': return 'notif-icon--purple'
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
                  switch (n.type) {
                    case 'FEED_LIKE':
                    case 'FEED_COMMENT':
                    case 'FEED_SHARE':
                      navigate(rid ? `/feed/${rid}` : '/feed')
                      return
                    case 'NEW_FOLLOWER':
                      navigate(rid ? `/users/${rid}` : '/explore')
                      return
                  }
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
