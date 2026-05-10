import { useCallback, useEffect, useState } from 'react'
import MainLayout from '../components/MainLayout'
import {
  getNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead,
} from '../services/api'
import { timeAgo } from '../utils/timeAgo'
import '../styles/main.css'

const PAGE_SIZE = 20

const TYPE_ICON = {
  REQUEST_ACCEPTED: '✅',
  REQUEST_REJECTED: '❌',
  REQUEST_RECEIVED: '🔔',
  MATCH_FOUND: '🤝',
  NEW_MESSAGE: '💬',
  MEETING_REMINDER: '📅',
}

function iconFor(type) {
  return TYPE_ICON[type] || '🔔'
}

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE)
  const [markingAll, setMarkingAll] = useState(false)

  const fetchNotifications = useCallback(() => {
    setLoading(true)
    setError(null)
    getNotifications()
      .then(data => {
        setNotifications(Array.isArray(data) ? data : [])
        setVisibleCount(PAGE_SIZE)
      })
      .catch(err => setError(err.message || 'Failed to load notifications'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    fetchNotifications()
  }, [fetchNotifications])

  const unreadCount = notifications.filter(n => !n.read).length
  const visible = notifications.slice(0, visibleCount)
  const hasMore = visibleCount < notifications.length

  function handleMarkRead(id) {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n))
    markNotificationAsRead(id).catch(() => {
      setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: false } : n))
    })
  }

  function handleMarkAllRead() {
    if (unreadCount === 0 || markingAll) return
    setMarkingAll(true)
    setNotifications(prev => prev.map(n => ({ ...n, read: true })))
    markAllNotificationsAsRead()
      .catch(() => fetchNotifications())
      .finally(() => setMarkingAll(false))
  }

  return (
    <MainLayout>
      <section className="notif-page">
        <header className="notif-page-header">
          <div>
            <h1 className="notif-page-title">Notifications</h1>
            <p className="notif-page-subtitle">
              {unreadCount > 0
                ? `${unreadCount} unread`
                : notifications.length > 0
                  ? "You're all caught up"
                  : ' '}
            </p>
          </div>
          {unreadCount > 0 && (
            <button
              className="notif-page-mark-all"
              onClick={handleMarkAllRead}
              disabled={markingAll}
            >
              Mark all as read
            </button>
          )}
        </header>

        {loading && notifications.length === 0 ? (
          <div className="notif-page-state">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="1.5" style={{ opacity: 0.3, animation: 'spin 0.8s linear infinite' }}>
              <polyline points="23 4 23 10 17 10" />
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
            </svg>
            <p>Loading…</p>
          </div>
        ) : error ? (
          <div className="notif-page-state">
            <p>{error}</p>
            <button className="notif-load-more" onClick={fetchNotifications}>Try again</button>
          </div>
        ) : notifications.length === 0 ? (
          <div className="notif-page-state" data-testid="notifications-empty">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="1.5" style={{ opacity: 0.25 }}>
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>
            <p>No notifications yet</p>
          </div>
        ) : (
          <>
            <ul className="notif-page-list" data-testid="notifications-list">
              {visible.map(n => (
                <li
                  key={n.id}
                  className={`notif-page-item${!n.read ? ' notif-page-item--unread' : ''}`}
                  onClick={() => !n.read && handleMarkRead(n.id)}
                  role={!n.read ? 'button' : undefined}
                  tabIndex={!n.read ? 0 : undefined}
                  onKeyDown={e => {
                    if (!n.read && (e.key === 'Enter' || e.key === ' ')) {
                      e.preventDefault()
                      handleMarkRead(n.id)
                    }
                  }}
                  data-testid={`notifications-item-${n.id}`}
                  data-notification-type={n.type}
                  data-notification-read={n.read ? 'true' : 'false'}
                >
                  <span className="notif-page-emoji" aria-hidden="true">{iconFor(n.type)}</span>
                  <div className="notif-page-body">
                    <div className="notif-page-item-head">
                      <p className="notif-page-item-title" data-testid="notifications-item-title">{n.title}</p>
                      {!n.read && <span className="notif-unread-dot" aria-label="Unread" />}
                    </div>
                    <p className="notif-page-item-text" data-testid="notifications-item-body">{n.body}</p>
                    <span className="notif-page-item-time">{timeAgo(n.createdAt)}</span>
                  </div>
                </li>
              ))}
            </ul>

            {hasMore && (
              <button
                className="notif-load-more"
                onClick={() => setVisibleCount(c => c + PAGE_SIZE)}
              >
                Load more
              </button>
            )}
          </>
        )}
      </section>
    </MainLayout>
  )
}
