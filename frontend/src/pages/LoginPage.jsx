import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { loginUser } from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

function validate(fields) {
  const errors = {}
  if (!fields.email.trim()) {
    errors.email = 'Email is required.'
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email)) {
    errors.email = 'Enter a valid email address.'
  }
  if (!fields.password) {
    errors.password = 'Password is required.'
  }
  return errors
}

export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()
  const [fields, setFields] = useState({ email: '', password: '' })
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const registered = location.state?.registered

  function handleChange(name, value) {
    setFields(prev => ({ ...prev, [name]: value }))
    setErrors(prev => ({ ...prev, [name]: '' }))
    setServerError('')
  }

  async function handleSubmit(e) {
    e.preventDefault()
    const validationErrors = validate(fields)
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }
    setIsLoading(true)
    try {
      const data = await loginUser(fields)
      login(data.sessionToken, data.role, data.userId)
      navigate('/home', { replace: true })
    } catch (err) {
      setServerError(err.message || 'Invalid email or password.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-title">Welcome<br /><em>back.</em></div>
        <div className="auth-sub">Sign in to continue your journey</div>

        {registered && !serverError && (
          <div className="auth-success">Account created! Please sign in.</div>
        )}
        {serverError && (
          <div className="auth-error">{serverError}</div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <label className="field-label">Email</label>
          <input
            type="email"
            className="auth-input"
            value={fields.email}
            onChange={e => handleChange('email', e.target.value)}
            placeholder="you@example.com"
            aria-invalid={!!errors.email}
          />
          {errors.email && <div className="field-error-msg">{errors.email}</div>}

          <label className="field-label">Password</label>
          <input
            type="password"
            className="auth-input"
            value={fields.password}
            onChange={e => handleChange('password', e.target.value)}
            placeholder="••••••••"
            aria-invalid={!!errors.password}
          />
          {errors.password && <div className="field-error-msg">{errors.password}</div>}

          <Link to="/forgot-password" className="forgot-link">Forgot password?</Link>

          <button type="submit" className="auth-btn" disabled={isLoading}>
            {isLoading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <div className="auth-footer">
          Don&apos;t have an account? <Link to="/register">Sign up</Link>
        </div>
      </div>
    </div>
  )
}
