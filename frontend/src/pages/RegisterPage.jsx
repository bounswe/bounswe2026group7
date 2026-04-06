import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { registerUser } from '../services/api'
import PageTransition from '../components/PageTransition'
import '../styles/main.css'

function validate(fields) {
  const errors = {}
  if (!fields.firstName.trim()) errors.firstName = 'First name is required.'
  if (!fields.lastName.trim()) errors.lastName = 'Last name is required.'
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
    <PageTransition>
    <div className="auth-screen">
      <div className="auth-circle auth-circle-top" />
      <div className="auth-circle auth-circle-bottom-left" />
      <div className="auth-circle auth-circle-bottom-right" />

      <motion.div
        className="auth-card"
        initial={{ opacity: 0, y: 28, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.38, ease: [0.25, 0.46, 0.45, 0.94], delay: 0.05 }}
      >
        <div className="auth-brand">
          <button className="auth-back" onClick={() => navigate('/')} aria-label="Back">←</button>
          <span className="auth-brand-name">MentorNet</span>
        </div>

        <div className="auth-dots">
          <span className="splash-dot" />
          <span className="splash-dot active" />
          <span className="splash-dot" />
        </div>

        <div className="auth-title">Create Account</div>
        <div className="auth-sub">Join the mentorship community</div>

        {serverError && <div className="auth-error">{serverError}</div>}

        <form onSubmit={handleSubmit} noValidate>
          <label className="field-label">First Name</label>
          <input
            type="text"
            className="auth-input"
            value={fields.firstName}
            onChange={e => handleChange('firstName', e.target.value)}
            placeholder="First name"
            aria-invalid={!!errors.firstName}
          />
          {errors.firstName && <div className="field-error-msg">{errors.firstName}</div>}

          <label className="field-label">Last Name</label>
          <input
            type="text"
            className="auth-input"
            value={fields.lastName}
            onChange={e => handleChange('lastName', e.target.value)}
            placeholder="Last name"
            aria-invalid={!!errors.lastName}
          />
          {errors.lastName && <div className="field-error-msg">{errors.lastName}</div>}

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

          <label className="field-label">I am a</label>
          <div className="role-row">
            <button
              type="button"
              className={`role-btn${!fields.isMentor ? ' active' : ''}`}
              onClick={() => handleChange('isMentor', false)}
            >
              Mentee
            </button>
            <button
              type="button"
              className={`role-btn${fields.isMentor ? ' active' : ''}`}
              onClick={() => handleChange('isMentor', true)}
            >
              Mentor
            </button>
          </div>

          <button type="submit" className="auth-btn" disabled={isLoading}>
            {isLoading ? 'Creating account...' : 'Create Account'}
          </button>
        </form>

        <div className="auth-footer">
          Already have an account? <Link to="/login">Sign in</Link>
        </div>
      </motion.div>
    </div>
    </PageTransition>
  )
}
