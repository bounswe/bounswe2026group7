import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
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
  const { role, firstName, lastName, profilePhoto } = useAuth()
  const currentPath = location.pathname

  const displayName = [firstName, lastName].filter(Boolean).join(' ') || 'User'
  const initials = [firstName?.[0], lastName?.[0]].filter(Boolean).join('').toUpperCase() || '?'
  const roleLabel = role === 'MENTOR' ? 'Mentor' : 'Mentee'

  const avatar = profilePhoto
    ? <img src={profilePhoto} alt={initials} style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 'inherit' }} />
    : initials

  const avatarSm = profilePhoto
    ? <img src={profilePhoto} alt={initials} style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 'inherit' }} />
    : initials

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
          <div className="avatar-sm">{avatarSm}</div>
        </div>
      </nav>

      <div className="main-layout">
        <aside className="sidebar">
          <div className="sidebar-user">
            <div className="sidebar-avatar">{avatar}</div>
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
