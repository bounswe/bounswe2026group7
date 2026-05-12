import { useState } from 'react'
import MainLayout from '../components/MainLayout'
import { sendAdminDirectMessage } from '../services/api'
import '../styles/main.css'

// AT-15 surface B. Minimal admin console — just the direct-message form for now.
// Toast pattern mirrors HomePage / ExplorePage: an absolutely-positioned DOM
// node injected into body for ~3.5s, then removed. Keeps us off a toast lib.

function showToast(message, type = 'success') {
  const el = document.createElement('div')
  el.className = `toast toast-${type}`
  el.textContent = message
  document.body.appendChild(el)
  setTimeout(() => el.remove(), 3500)
}

export default function AdminConsolePage() {
  const [userId, setUserId] = useState('')
  const [content, setContent] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')

  async function handleSend(e) {
    e.preventDefault()
    setError('')
    const numericId = Number(userId)
    if (!Number.isFinite(numericId) || numericId <= 0 || !Number.isInteger(numericId)) {
      setError('Enter a valid positive numeric user id.')
      return
    }
    if (!content.trim()) {
      setError('Message body cannot be empty.')
      return
    }
    setSending(true)
    try {
      await sendAdminDirectMessage(numericId, content)
      setContent('')
      setUserId('')
      showToast('Direct message delivered.', 'success')
    } catch (err) {
      const msg = err?.message || 'Failed to send direct message.'
      setError(msg)
      showToast('Unable to send message.', 'error')
    } finally {
      setSending(false)
    }
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <div className="page-title">Admin Console</div>
          <div className="page-sub">Send a direct message to any user as an administrator.</div>
        </div>
      </div>

      <form
        className="admin-console-form"
        onSubmit={handleSend}
        data-testid="admin-console-form"
        style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxWidth: '540px' }}
      >
        <label htmlFor="admin-console-recipient" style={{ fontWeight: 600 }}>Recipient user id</label>
        <input
          id="admin-console-recipient"
          type="number"
          min="1"
          step="1"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          placeholder="e.g. 42"
          data-testid="admin-console-recipient"
          disabled={sending}
          required
        />

        <label htmlFor="admin-console-body" style={{ fontWeight: 600 }}>Message</label>
        <textarea
          id="admin-console-body"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Write your message…"
          rows={6}
          data-testid="admin-console-body"
          disabled={sending}
          required
        />

        {error && (
          <div className="md-error-card" data-testid="admin-console-error">
            <div className="md-error-title">Send failed</div>
            <div className="md-error-sub">{error}</div>
          </div>
        )}

        <div>
          <button
            type="submit"
            className="action-btn"
            disabled={sending}
            data-testid="admin-console-send"
          >
            {sending ? 'Sending…' : 'Send message'}
          </button>
        </div>
      </form>
    </MainLayout>
  )
}
