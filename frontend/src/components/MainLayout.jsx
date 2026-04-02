import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

const NAV_TABS = [
  { label: 'Home', path: '/home' },
  { label: 'Explore', path: '/explore' },
  { label: 'Messages', path: '/messages' },
  { label: 'Tasks', path: '/tasks' },
  { label: 'Schedule', path: '/schedule' },
  { label: 'Profile', path: '/profile' },
]

const SIDEBAR_LINKS = [
  { label: 'Home', path: '/home', icon: '🏠' },
  { label: 'Explore', path: '/explore', icon: '🔍' },
  { label: 'Messages', path: '/messages', icon: '💬' },
  { label: 'My Tasks', path: '/tasks', icon: '✅' },
  { label: 'Schedule', path: '/schedule', icon: '📅' },
  { label: 'Availability', path: '/availability', icon: '🕐' },
  { label: 'Rate Mentor', path: '/rate', icon: '⭐' },
  { label: 'Profile', path: '/profile', icon: '👤' },
]

export default function MainLayout({ children }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { role } = useAuth()
  const currentPath = location.pathname

  const initials = 'ÖA'
  const displayName = 'Övgü Su Afşar'
  const department = 'Computer Engineering'
  const roleLabel = role === 'MENTOR' ? 'Mentor' : 'Mentee'

  return (
    <>
      <nav className="topnav">
        <div className="logo" onClick={() => navigate('/home')}>
          <span>Mentor</span>Net
        </div>
        <div className="nav-tabs">
          {NAV_TABS.map(tab => (
            <button
              key={tab.path}
              className={`nav-tab${currentPath === tab.path ? ' active' : ''}`}
              onClick={() => navigate(tab.path)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <div className="nav-right">
          <div className="avatar-sm">{initials}</div>
        </div>
      </nav>

      <div className="main-layout">
        <aside className="sidebar">
          <div className="sidebar-user">
            <div className="sidebar-avatar">{initials}</div>
            <div className="sidebar-name">{displayName}</div>
            <div className="sidebar-role">{roleLabel} · {department}</div>
            <div className="sidebar-badge">Active Mentorship: 1</div>
          </div>
          <nav className="sidebar-nav">
            {SIDEBAR_LINKS.map(link => (
              <button
                key={link.path}
                className={`sidebar-link${currentPath === link.path ? ' active' : ''}`}
                onClick={() => navigate(link.path)}
              >
                <span className="icon">{link.icon}</span> {link.label}
              </button>
            ))}
          </nav>
        </aside>
        <main className="main-content">
          {children}
        </main>
      </div>
    </>
  )
}
