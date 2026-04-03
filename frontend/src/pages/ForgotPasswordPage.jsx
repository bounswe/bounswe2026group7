import { useState } from 'react'
import { Link } from 'react-router-dom'
import { forgotPassword } from '../services/api'
import '../styles/main.css'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState('')
  const [serverError, setServerError] = useState('')
  const [success, setSuccess] = useState(false)
  const [isLoading, setIsLoading] = useState(false)

  function handleChange(value) {
    setEmail(value)
    setEmailError('')
    setServerError('')
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!email.trim()) {
      setEmailError('Email is required.')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailError('Enter a valid email address.')
      return
    }
    setIsLoading(true)
    try {
      await forgotPassword({ email })
      setSuccess(true)
    } catch (err) {
      setServerError(err.message || 'Something went wrong. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-title">Forgot<br /><em>password?</em></div>
        <div className="auth-sub">Enter your email to receive a reset link</div>

        {serverError && <div className="auth-error">{serverError}</div>}
        {success && (
          <div className="auth-success">
            Reset link sent! Check your inbox.
          </div>
        )}

        {!success && (
          <form onSubmit={handleSubmit} noValidate>
            <label className="field-label">Email</label>
            <input
              type="email"
              className="auth-input"
              value={email}
              onChange={e => handleChange(e.target.value)}
              placeholder="you@example.com"
              aria-invalid={!!emailError}
            />
            {emailError && <div className="field-error-msg">{emailError}</div>}

            <button type="submit" className="auth-btn" disabled={isLoading}>
              {isLoading ? 'Sending...' : 'Send Reset Link'}
            </button>
          </form>
        )}

        <div className="auth-footer">
          <Link to="/login">Back to Sign In</Link>
        </div>
      </div>
    </div>
  )
}
