import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { registerUser } from '../services/api'
import '../styles/auth.css'

function validate(fields) {
  const errors = {}

  if (!fields.firstName.trim()) {
    errors.firstName = 'First name is required.'
  }
  if (!fields.lastName.trim()) {
    errors.lastName = 'Last name is required.'
  }
  if (!fields.email.trim()) {
    errors.email = 'Email is required.'
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email)) {
    errors.email = 'Enter a valid email address.'
  }

  if (!fields.password) {
    errors.password = 'Password is required.'
  } else if (fields.password.length < 8) {
    errors.password = 'Password must be at least 8 characters.'
  } else if (!/[A-Z]/.test(fields.password)) {
    errors.password = 'Password must contain at least one uppercase letter.'
  } else if (!/[a-z]/.test(fields.password)) {
    errors.password = 'Password must contain at least one lowercase letter.'
  } else if (!/[0-9]/.test(fields.password)) {
    errors.password = 'Password must contain at least one number.'
  }

  return errors
}

export default function RegisterPage() {
  const navigate = useNavigate()
  const [fields, setFields] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    isMentor: false,
  })
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

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
      await registerUser(fields)
      navigate('/login', { state: { registered: true } })
    } catch (err) {
      setServerError(err.message || 'Registration failed. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Create account</h1>

        {serverError && <div className="server-error">{serverError}</div>}

        <form onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label htmlFor="firstName">First name</label>
            <input
              id="firstName"
              type="text"
              value={fields.firstName}
              onChange={e => handleChange('firstName', e.target.value)}
              aria-invalid={!!errors.firstName}
              aria-describedby={errors.firstName ? 'firstName-error' : undefined}
            />
            {errors.firstName && (
              <span id="firstName-error" className="field-error" role="alert">
                {errors.firstName}
              </span>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="lastName">Last name</label>
            <input
              id="lastName"
              type="text"
              value={fields.lastName}
              onChange={e => handleChange('lastName', e.target.value)}
              aria-invalid={!!errors.lastName}
              aria-describedby={errors.lastName ? 'lastName-error' : undefined}
            />
            {errors.lastName && (
              <span id="lastName-error" className="field-error" role="alert">
                {errors.lastName}
              </span>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={fields.email}
              onChange={e => handleChange('email', e.target.value)}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined}
            />
            {errors.email && (
              <span id="email-error" className="field-error" role="alert">
                {errors.email}
              </span>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={fields.password}
              onChange={e => handleChange('password', e.target.value)}
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? 'password-error' : undefined}
            />
            {errors.password && (
              <span id="password-error" className="field-error" role="alert">
                {errors.password}
              </span>
            )}
          </div>

          <div className="form-group">
            <label>I am a</label>
            <div className="role-toggle">
              <button
                type="button"
                className={!fields.isMentor ? 'active' : ''}
                onClick={() => handleChange('isMentor', false)}
              >
                Mentee
              </button>
              <button
                type="button"
                className={fields.isMentor ? 'active' : ''}
                onClick={() => handleChange('isMentor', true)}
              >
                Mentor
              </button>
            </div>
          </div>

          <button type="submit" className="auth-submit" disabled={isLoading}>
            {isLoading ? 'Creating account...' : 'Create account'}
          </button>
        </form>

        <p className="auth-link">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
