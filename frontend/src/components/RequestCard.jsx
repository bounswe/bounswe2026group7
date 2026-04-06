import { Calendar } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

export default function RequestCard({ request, onView }) {
  const navigate = useNavigate()
  // CRITICAL PRIVACY RULE: Using ONLY first name
  const menteeName = request.menteeFirstName || 'Mentee'
  const dateFormatted = new Date(request.createdAt).toLocaleDateString(undefined, {
    month: 'short', day: 'numeric', year: 'numeric'
  })

  return (
    <div className="request-card" style={{
      border: '1px solid var(--border)',
      borderRadius: '12px',
      padding: '16px',
      backgroundColor: 'var(--card-bg)',
      display: 'flex',
      flexDirection: 'column',
      gap: '12px'
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        {/* Masked avatar, no photo */}
        <div style={{
          width: '40px',
          height: '40px',
          borderRadius: '50%',
          backgroundColor: 'var(--accent)',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontWeight: 'bold',
          fontSize: '18px'
        }}>
          {menteeName.charAt(0).toUpperCase()}
        </div>
        <div style={{ flex: 1 }}>
          <h3 style={{ margin: 0, fontSize: '18px', fontWeight: '600', color: 'var(--text)' }}>
            {menteeName}
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-light)', fontSize: '14px', marginTop: '4px' }}>
            <Calendar size={14} />
            <span>Received {dateFormatted}</span>
          </div>
        </div>
      </div>

      <p style={{ margin: 0, color: 'var(--text-light)', fontSize: '14px', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
        {request.message}
      </p>

      <div style={{ borderTop: '1px solid var(--border)', paddingTop: '12px', display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>

        <button
          onClick={() => navigate(`/profile/${request.menteeId}`)}
          className="auth-btn"
          style={{
            width: 'auto',
            padding: '8px 16px',
            fontSize: '14px',
            margin: 0,
            background: 'var(--accent)',
            color: '#ffffff',
            border: 'none',
            borderRadius: '8px',
            cursor: 'pointer'
          }}
        >
          Go Profile
        </button>

        <button

          onClick={() => onView(request)}

          className="auth-btn"

          style={{ width: 'auto', padding: '8px 16px', fontSize: '14px', margin: 0 }}

        >
          View Details
        </button>

      </div>
    </div>
  )
}