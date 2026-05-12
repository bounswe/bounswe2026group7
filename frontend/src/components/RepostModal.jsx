import { useEffect, useRef, useState } from 'react'

const COMMENTARY_MAX = 2000

/**
 * Repost / quote-share modal (#542 / backend #484). Submits an empty body
 * for a bare repost (button labelled "Repost without commentary") or the
 * trimmed text for a quote-share. The original post is shown in a compact
 * embed inside the modal so the user remembers what they're resharing.
 *
 * Props:
 *   open       — visibility
 *   post       — the original FeedPostListItem / FeedPostResponse
 *   onClose()  — close handler
 *   onConfirm(body)  — async; body is "" for bare repost, trimmed string
 *                     for quote-share. Caller is responsible for surfacing
 *                     errors and closing the modal on success.
 *   loading    — disables submit while in flight
 */
export default function RepostModal({ open, post, onClose, onConfirm, loading }) {
  const overlayRef = useRef(null)
  const [body, setBody] = useState('')

  useEffect(() => {
    if (open) setBody('')
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const onKey = e => { if (e.key === 'Escape' && !loading) onClose?.() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, loading])

  if (!open || !post) return null

  const trimmed = body.trim()
  const remaining = COMMENTARY_MAX - body.length

  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current && !loading) onClose?.() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="repost-title">
        <div className="modal-header">
          <div>
            <h2 id="repost-title">Repost</h2>
            <p className="modal-subtitle">
              Reshare this post into your followers' feeds. Add optional commentary to
              quote-share, or leave it empty for a bare repost.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        <label className="section-label" style={{ marginTop: '12px', display: 'block' }} htmlFor="repost-body">
          Your commentary (optional)
        </label>
        <textarea
          id="repost-body"
          className="modal-textarea"
          rows={4}
          maxLength={COMMENTARY_MAX}
          value={body}
          onChange={e => setBody(e.target.value)}
          placeholder="What's your take on this?"
          disabled={loading}
        />
        <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'right' }}>
          {remaining} characters left
        </div>

        <div className="repost-embed" aria-label="Original post">
          <div className="repost-embed-head">
            <span className="repost-embed-author">{post.authorFirstName || 'User'}</span>
          </div>
          <div className="repost-embed-body">{post.body}</div>
        </div>

        <div className="modal-actions" style={{ marginTop: '16px' }}>
          <button type="button" className="modal-btn-secondary" onClick={onClose} disabled={loading}>
            Cancel
          </button>
          <button
            type="button"
            className="modal-btn-primary"
            onClick={() => onConfirm?.(trimmed)}
            disabled={loading}
            title={trimmed ? 'Quote-share with commentary' : 'Bare repost without commentary'}
          >
            {loading
              ? 'Reposting…'
              : trimmed
                ? 'Quote-share'
                : 'Repost without commentary'}
          </button>
        </div>
      </div>
    </div>
  )
}
