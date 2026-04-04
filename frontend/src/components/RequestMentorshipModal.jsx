import { useEffect, useRef, useState } from 'react'
import '../styles/modal.css'

export default function RequestMentorshipModal({ visible, onClose, onSubmit, loading, error, mentorName, defaultMessage }) {
  const [message, setMessage] = useState(defaultMessage || '')
  const overlayRef = useRef(null)
  const textareaRef = useRef(null)
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
      // Focus textarea when modal opens
      setTimeout(() => textareaRef.current?.focus(), 50)
    }
  }, [visible, defaultMessage])

  const isValid = message.trim().length <= maxChars

  const handleOverlayClick = (event) => {
    if (event.target === overlayRef.current) {
      onClose()
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!isValid) return
    onSubmit(message.trim())
  }

  if (!visible) return null

  const charCount = message.trim().length

  return (
    <div className="modal-overlay" ref={overlayRef} onMouseDown={handleOverlayClick}>
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="requestMentorshipTitle">
        <div className="modal-header">
          <div>
            <h2 id="requestMentorshipTitle">Send Mentorship Request</h2>
            <p className="modal-subtitle">Optionally write a message to {mentorName || 'the mentor'} explaining your goals.</p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        <form onSubmit={handleSubmit}>
          <label className="modal-label" htmlFor="mentorshipMessage">Introductory Message <span style={{ fontWeight: 400, color: '#64748b' }}>(optional)</span></label>
          <textarea
            id="mentorshipMessage"
            ref={textareaRef}
            className="modal-textarea"
            maxLength={maxChars}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="Tell the mentor why you'd like to work with them..."
            rows={8}
          />
          <div className="modal-footer-row">
            <span className="char-count" style={{ color: charCount > maxChars ? '#b91c1c' : '#64748b' }}>
              {charCount}/{maxChars}
            </span>
          </div>

          {error && <div className="modal-api-error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="modal-btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="modal-btn-primary" disabled={loading || !isValid}>
              {loading ? 'Sending...' : 'Send Request'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
