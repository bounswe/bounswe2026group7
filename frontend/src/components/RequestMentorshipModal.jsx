import { useEffect, useRef, useState } from 'react'

export default function RequestMentorshipModal({ visible, onClose, onSubmit, loading, error, mentorName, defaultMessage }) {
  const [message, setMessage] = useState(defaultMessage || '')
  const [touched, setTouched] = useState(false)
  const overlayRef = useRef(null)
  const maxChars = 500

  useEffect(() => {
    if (!visible) return

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [visible, onClose])

  useEffect(() => {
    if (visible) {
      setMessage(defaultMessage || '')
      setTouched(false)
    }
  }, [visible, defaultMessage])

  const isValid = message.trim().length > 0 && message.trim().length <= maxChars
  const errorMessage = touched && message.trim().length === 0 ? 'Message is required.' : ''

  const handleOverlayClick = (event) => {
    if (event.target === overlayRef.current) {
      onClose()
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    setTouched(true)
    if (!isValid) return
    onSubmit(message.trim())
  }

  if (!visible) return null

  return (
    <div className="modal-overlay" ref={overlayRef} onMouseDown={handleOverlayClick}>
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="requestMentorshipTitle">
        <div className="modal-header">
          <div>
            <h2 id="requestMentorshipTitle">Send Mentorship Request</h2>
            <p className="modal-subtitle">Write a short message to {mentorName || 'the mentor'} explaining your goals.</p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        <form onSubmit={handleSubmit}>
          <label className="modal-label" htmlFor="mentorshipMessage">Introductory Message</label>
          <textarea
            id="mentorshipMessage"
            className="modal-textarea"
            maxLength={maxChars}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            onBlur={() => setTouched(true)}
            placeholder="Tell the mentor why you'd like to work with them..."
            rows={8}
          />
          <div className="modal-footer-row">
            <span className="char-count">{message.trim().length}/{maxChars}</span>
            {errorMessage && <span className="field-error">{errorMessage}</span>}
          </div>

          {error && <div className="modal-api-error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={loading || !isValid}>
              {loading ? 'Sending...' : 'Send Request'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
