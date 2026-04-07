import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function DashboardPage() {
  const { role, userId, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div style={{ padding: '40px', textAlign: 'center' }}>
      <h1>Dashboard</h1>
      <p>Welcome! You are signed in as a <strong>{role}</strong> (ID: {userId}).</p>
      <button
        onClick={handleLogout}
        style={{
          marginTop: '24px',
          padding: '10px 24px',
          background: 'var(--accent)',
          color: '#fff',
          border: 'none',
          borderRadius: '6px',
          fontSize: '15px',
          cursor: 'pointer',
        }}
      >
        Log out
      </button>
    </div>
  )
}
