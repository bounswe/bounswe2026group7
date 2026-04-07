import { useState, useRef, useEffect } from 'react'
import { getNotifications, markNotificationAsRead, markAllNotificationsAsRead } from '../services/api'

function timeAgo(iso) {
  const diff = Math.floor((Date.now() - new Date(iso)) / 1000)
  if (diff < 60) return 'just now'
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`
  return `${Math.floor(diff / 86400)}d ago`
}

function TypeIcon({ type }) {
  if (type === 'REQUEST_ACCEPTED') {
    return (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
        <polyline points="20 6 9 17 4 12" />
      </svg>
    )
  }
  if (type === 'REQUEST_REJECTED') {
    return (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
        <line x1="18" y1="6" x2="6" y2="18" />
        <line x1="6" y1="6" x2="18" y2="18" />
      </svg>
    )
  }
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
    </svg>
  )
}

function typeColorClass(type) {
  if (type === 'REQUEST_ACCEPTED') return 'notif-icon--green'
  if (type === 'REQUEST_REJECTED') return 'notif-icon--red'
  return 'notif-icon--blue'
}

export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [notifications, setNotifications] = useState([])
  const [filter, setFilter] = useState('all')
  const bellRef = useRef(null)

  useEffect(() => {
    getNotifications().then(setNotifications).catch(() => {})
  }, [])

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
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n))
    markNotificationAsRead(id).catch(() => {})
  }

  function handleMarkAllRead() {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })))
    markAllNotificationsAsRead().catch(() => {})
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
        <div className="notif-header">
          <div className="notif-header-left">
            <span className="notif-title">Notifications</span>
            {unreadCount > 0 && <span className="notif-count">{unreadCount} new</span>}
          </div>
          {unreadCount > 0 && (
            <button className="notif-mark-all" onClick={handleMarkAllRead}>
              Mark all read
            </button>
          )}
        </div>

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

        <div className="notif-list">
          {displayed.length === 0 ? (
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
                onClick={() => !n.read && handleMarkRead(n.id)}
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
      </div>
    </div>
  )
}
