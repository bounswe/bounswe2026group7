import { useAuth } from '../context/AuthContext'

// AT-15 surface C. Shows whenever the auth context carries a `banned` payload
// populated by the 403 BANNED_UNTIL interceptor in services/api.js. We avoid
// importing api.js or wiring fetch logic here — the banner is purely a
// presentation of context state, so the data path stays decoupled.
export default function BannedStateBanner() {
  const { banned } = useAuth()
  if (!banned) return null

  const reason = banned.reason || banned.message || 'Your account is banned.'
  let expiry = ''
  if (banned.expiresAt) {
    const d = new Date(banned.expiresAt)
    if (!isNaN(d.getTime())) {
      expiry = d.toLocaleString()
    }
  }

  return (
    <div
      role="alert"
      data-testid="banned-state-banner"
      style={{
        background: '#b91c1c',
        color: '#fff',
        padding: '10px 16px',
        fontSize: '14px',
        fontWeight: 600,
        borderBottom: '1px solid rgba(0,0,0,0.2)',
      }}
    >
      <span data-testid="banned-state-banner-reason">{reason}</span>
      {expiry && (
        <>
          {' · expires '}
          <span data-testid="banned-state-banner-expires">{expiry}</span>
        </>
      )}
    </div>
  )
}
