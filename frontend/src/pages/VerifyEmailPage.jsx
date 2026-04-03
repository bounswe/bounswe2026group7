import { useState, useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { verifyEmail } from '../services/api'
import '../styles/main.css'

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || ''

  const [status, setStatus] = useState('loading')

  useEffect(() => {
    if (!token) {
      setStatus('error')
      return
    }
    verifyEmail({ token })
      .then(() => setStatus('success'))
      .catch(() => setStatus('error'))
  }, [token])

  if (status === 'loading') {
    return (
      <div className="auth-screen">
        <div className="auth-card">
          <div className="auth-sub">Verifying your email...</div>
        </div>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="auth-screen">
        <div className="auth-card">
          <div className="auth-title">Verification<br /><em>failed.</em></div>
          <div className="auth-error">This verification link is invalid or has expired.</div>
          <div className="auth-footer">
            <Link to="/login">Back to Sign In</Link>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-title">Email<br /><em>verified!</em></div>
        <div className="auth-success">Your email has been verified. You can now sign in.</div>
        <div className="auth-footer">
          <Link to="/login">Sign In</Link>
        </div>
      </div>
    </div>
  )
}
