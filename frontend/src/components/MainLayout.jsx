import { useState, useRef, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import Avatar from './Avatar'
import usePresence from '../hooks/usePresence'
import {
  Home, Compass, MessageCircle, CheckSquare, CalendarDays,
  Clock, User,
} from 'lucide-react'
import '../styles/main.css'

const SOON = new Set(['/messages', '/tasks', '/schedule'])

const NAV_TABS = [
  { label: 'Home', path: '/home' },
  { label: 'Explore', path: '/explore' },
  { label: 'Messages', path: '/messages' },
  { label: 'Tasks', path: '/tasks' },
  { label: 'Schedule', path: '/schedule' },
  { label: 'Profile', path: '/profile' },
]

const SIDEBAR_LINKS = [
  { label: 'Home', path: '/home', icon: Home },
  { label: 'Explore', path: '/explore', icon: Compass },
  { label: 'Messages', path: '/messages', icon: MessageCircle },
  { label: 'My Tasks', path: '/tasks', icon: CheckSquare },
  { label: 'Schedule', path: '/schedule', icon: CalendarDays },
  { label: 'Availability', path: '/availability', icon: Clock },
  { label: 'Profile', path: '/profile', icon: User },
]

export default function MainLayout({ children }) {
  const navigate = useNavigate()
  const location = useLocation()


  const { role, logout, firstName, lastName, profilePhoto } = useAuth()
  const presence = usePresence()
  const currentPath = location.pathname
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef(null)

  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [])

  function handleLogout() {
    setDropdownOpen(false)
    logout()
    navigate('/login', { replace: true })
  }

  const displayName = [firstName, lastName].filter(Boolean).join(' ') || 'User'
  const initials = [firstName?.[0], lastName?.[0]].filter(Boolean).join('').toUpperCase() || '?'
  const roleLabel = role === 'MENTOR' ? 'Mentor' : 'Mentee'

  return (
    <>
      <nav className="topnav">
        <div className="logo" onClick={() => navigate('/home')}>
          <span>Mentor</span>Net
        </div>
        <div className="nav-tabs">
          {NAV_TABS.map(tab => {
            const soon = SOON.has(tab.path)
            return (
              <button
                key={tab.path}
                className={`nav-tab${currentPath === tab.path ? ' active' : ''}${soon ? ' soon' : ''}`}
                onClick={soon ? undefined : () => navigate(tab.path)}
                disabled={soon}
                title={soon ? 'Coming soon' : undefined}
              >
                {tab.label}
                {soon && <span className="soon-badge">Soon</span>}
              </button>
            )
          })}
        </div>
        <div className="nav-right">
          <div className="ud-wrap" ref={dropdownRef}>
            <button
              className={`ud-trigger${dropdownOpen ? ' open' : ''}`}
              onClick={() => setDropdownOpen(v => !v)}
              aria-expanded={dropdownOpen}
              aria-haspopup="true"
            >
              <Avatar src={profilePhoto} initials={initials} size="sm" status={presence} />
            </button>

            <div className={`ud-panel${dropdownOpen ? ' ud-panel--open' : ''}`} role="menu">
              {/* Identity */}
              <div className="ud-identity">
                <Avatar src={profilePhoto} initials={initials} size="md" status={presence} />
                <div>
                  <p className="ud-name">{displayName}</p>
                  <p className="ud-sub">
                    <span className="ud-sub-dot" />
                    {roleLabel} · active
                  </p>
                </div>
              </div>

              {/* Stats */}
              <div className="ud-stats">
                {(role === 'MENTOR' ? [
                  { num: 2, label: 'Mentees' },
                  { num: 5, label: 'Sessions' },
                  { num: 4, label: 'Requests' },
                ] : [
                  { num: 5, label: 'Tasks' },
                  { num: 3, label: 'Sessions' },
                  { num: 2, label: 'Requests' },
                ]).map((s, i) => (
                  <div key={s.label} className={`ud-stat${i > 0 ? ' ud-stat--sep' : ''}`}>
                    <span className="ud-stat-num">{s.num}</span>
                    <span className="ud-stat-lbl">{s.label}</span>
                  </div>
                ))}
              </div>

              <div className="ud-divider" />

              {/* Menu */}
              <div className="ud-menu">
                <button className="ud-item" role="menuitem" onClick={() => { setDropdownOpen(false); navigate('/profile') }}>
                  <span className="ud-item-icon">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="12" cy="8" r="4" /><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
                    </svg>
                  </span>
                  View profile
                </button>

                <div className="ud-divider" />

                <button className="ud-item ud-item--danger" role="menuitem" onClick={handleLogout}>
                  <span className="ud-item-icon">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                      <polyline points="16 17 21 12 16 7" />
                      <line x1="21" y1="12" x2="9" y2="12" />
                    </svg>
                  </span>
                  Log out
                </button>
              </div>
            </div>
          </div>
        </div>
      </nav>

      <div className="main-layout">
        <aside className="sidebar">
          <div className="sidebar-user">
            <Avatar src={profilePhoto} initials={initials} size="md" status={presence} className="sidebar-avatar" />
            <div className="sidebar-name">{displayName}</div>
            <div className="sidebar-role">{roleLabel}</div>
            <div className="sidebar-badge">Active Mentorship: 1</div>
          </div>
          <nav className="sidebar-nav">
            {SIDEBAR_LINKS.map(link => {
              const soon = SOON.has(link.path)
              return (
                <button
                  key={link.path}
                  className={`sidebar-link${currentPath === link.path ? ' active' : ''}${soon ? ' soon' : ''}`}
                  onClick={soon ? undefined : () => navigate(link.path)}
                  disabled={soon}
                  title={soon ? 'Coming soon' : undefined}
                >
                  <link.icon size={16} strokeWidth={1.75} className="icon" /> {link.label}
                  {soon && <span className="soon-badge">Soon</span>}
                </button>
              )
            })}
          </nav>
        </aside>
        <main className="main-content">
          {children}
        </main>
      </div>
    </>
  )
}
