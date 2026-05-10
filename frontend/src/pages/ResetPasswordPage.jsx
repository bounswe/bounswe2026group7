import { useState, useEffect } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { validateResetToken, resetPassword } from '../services/api'
import '../styles/main.css'

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token') || ''

  const [tokenValid, setTokenValid] = useState(null)
  const [newPassword, setNewPassword] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const [serverError, setServerError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    if (!token) {
      setTokenValid(false)
      return
    }
    validateResetToken({ token })
      .then(() => setTokenValid(true))
      .catch(() => setTokenValid(false))
  }, [token])

  function handleChange(value) {
    setNewPassword(value)
    setPasswordError('')
    setServerError('')
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!newPassword) {
      setPasswordError('Password is required.')
      return
    }
    if (newPassword.length < 8 || !/[A-Z]/.test(newPassword) || !/[a-z]/.test(newPassword) || !/[0-9]/.test(newPassword)) {
      setPasswordError('Password must be at least 8 characters with uppercase, lowercase, and a number.')
      return
    }
    setIsLoading(true)
    try {
      await resetPassword({ token, newPassword })
      navigate('/login', { state: { passwordReset: true } })
    } catch (err) {
      setServerError(err.message || 'Something went wrong. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }

  if (tokenValid === null) {
    return (
      <div className="auth-screen">
        <div className="auth-card">
          <div className="auth-sub">Validating link...</div>
        </div>
      </div>
    )
  }

  if (tokenValid === false) {
    return (
      <div className="auth-screen">
        <div className="auth-card">
          <div className="auth-title">Invalid<br /><em>link.</em></div>
          <div className="auth-error">This reset link is invalid or has expired.</div>
          <div className="auth-footer">
            <Link to="/forgot-password">Request a new link</Link>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-title">Reset<br /><em>password.</em></div>
        <div className="auth-sub">Enter your new password below</div>

        {serverError && <div className="auth-error" data-testid="reset-password-error">{serverError}</div>}

        <form onSubmit={handleSubmit} noValidate data-testid="reset-password-form">
          <label className="field-label">New Password</label>
          <input
            type="password"
            className="auth-input"
            value={newPassword}
            onChange={e => handleChange(e.target.value)}
            placeholder="••••••••"
            aria-invalid={!!passwordError}
            data-testid="reset-password-new-password"
          />
          {passwordError && <div className="field-error-msg">{passwordError}</div>}

          <button type="submit" className="auth-btn" disabled={isLoading} data-testid="reset-password-submit">
            {isLoading ? 'Resetting...' : 'Reset Password'}
          </button>
        </form>

        <div className="auth-footer">
          <Link to="/login">Back to Sign In</Link>
        </div>
      </div>
    </div>
  )
}
