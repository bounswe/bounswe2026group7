import { useState, useRef, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import {
  Home, Compass, MessageCircle, CheckSquare, CalendarDays,
  Clock, User, Inbox
} from 'lucide-react'
import '../styles/main.css'

const SOON = new Set(['/messages', '/tasks', '/schedule'])

export default function MainLayout({ children }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { role, logout } = useAuth()
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

  const NAV_TABS = [
    { label: 'Home', path: '/home' },
    ...(role !== 'MENTOR' ? [{ label: 'Explore', path: '/explore' }] : []),
    ...(role === 'MENTOR' ? [{ label: 'Mentorship Requests', path: '/mentorship-requests' }] : []),
    { label: 'Messages', path: '/messages' },
    { label: 'Tasks', path: '/tasks' },
    { label: 'Schedule', path: '/schedule' },
    { label: 'Profile', path: '/profile' },
  ]

  const SIDEBAR_LINKS = [
    { label: 'Home', path: '/home', icon: Home },
    ...(role !== 'MENTOR' ? [{ label: 'Explore', path: '/explore', icon: Compass }] : []),
    ...(role === 'MENTOR' ? [{ label: 'Mentorship Requests', path: '/mentorship-requests', icon: Inbox }] : []),
    { label: 'Messages', path: '/messages', icon: MessageCircle },
    { label: 'My Tasks', path: '/tasks', icon: CheckSquare },
    { label: 'Schedule', path: '/schedule', icon: CalendarDays },
    { label: 'Availability', path: '/availability', icon: Clock },
    { label: 'Profile', path: '/profile', icon: User },
  ]

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
          <div className="user-menu-container" ref={dropdownRef} style={{ position: 'relative' }}>
            <div 
              className="avatar-sm" 
              onClick={() => setDropdownOpen(!dropdownOpen)}
              style={{ cursor: 'pointer' }}
            >
              {initials}
            </div>
            {dropdownOpen && (
              <div 
                className="profile-dropdown" 
                style={{
                  position: 'absolute',
                  right: 0,
                  top: '120%',
                  backgroundColor: 'var(--card-bg, #fff)',
                  border: '1px solid var(--border, #e5e7eb)',
                  borderRadius: '8px',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
                  padding: '8px 0',
                  zIndex: 50,
                  minWidth: '150px'
                }}
              >
                <button 
                  onClick={() => { setDropdownOpen(false); navigate('/profile'); }}
                  style={{
                    width: '100%',
                    textAlign: 'left',
                    padding: '8px 16px',
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    color: 'var(--text-main, #111827)'
                  }}
                  onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'var(--hover-bg, #f3f4f6)'}
                  onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  Profile
                </button>
                <div style={{ height: '1px', backgroundColor: 'var(--border, #e5e7eb)', margin: '4px 0' }} />
                <button 
                  onClick={handleLogout}
                  style={{
                    width: '100%',
                    textAlign: 'left',
                    padding: '8px 16px',
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    color: '#dc2626'
                  }}
                  onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'var(--hover-bg, #f3f4f6)'}
                  onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  Log Out
                </button>
              </div>
            )}
          </div>
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
