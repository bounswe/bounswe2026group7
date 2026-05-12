import { useEffect, useRef, useState } from 'react'
import { getFeedPostHistory } from '../services/api'

/**
 * Feed post edit-history viewer (#544 / backend #487). Opens when the
 * author clicks the "edited" badge on a post they own. Lists historical
 * bodies newest-first; each entry shows the body + hashtag set + timestamp
 * as they were *before* the corresponding edit was applied.
 *
 * Author-only on the backend (403 for others), so the badge is rendered
 * non-clickable for non-author viewers by FeedPostCard.
 */
export default function EditHistoryModal({ open, postId, onClose }) {
  const overlayRef = useRef(null)
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!open || !postId) return undefined
    let cancelled = false
    setLoading(true)
    setError(null)
    setEntries([])
    getFeedPostHistory(postId)
      .then(list => { if (!cancelled) setEntries(Array.isArray(list) ? list : []) })
      .catch(err => { if (!cancelled) setError(err?.message || 'Failed to load edit history.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [open, postId])

  useEffect(() => {
    if (!open) return undefined
    const onKey = e => { if (e.key === 'Escape') onClose?.() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="modal-overlay"
      ref={overlayRef}
      onMouseDown={e => { if (e.target === overlayRef.current) onClose?.() }}
    >
      <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="edit-history-title">
        <div className="modal-header">
          <div>
            <h2 id="edit-history-title">Edit history</h2>
            <p className="modal-subtitle">
              Older revisions of this post, newest first. Each entry shows the post as it was
              before that edit was applied.
            </p>
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        {loading ? (
          <div className="md-loading" style={{ marginTop: '12px' }}>Loading history…</div>
        ) : error ? (
          <div className="md-error-card" style={{ marginTop: '12px' }}>
            <div className="md-error-title">Couldn’t load history</div>
            <div className="md-error-sub">{error}</div>
          </div>
        ) : entries.length === 0 ? (
          <div className="empty-state" style={{ marginTop: '12px' }}>
            No earlier revisions on file.
          </div>
        ) : (
          <ul className="history-list">
            {entries.map(e => (
              <li key={e.id} className="history-entry">
                <div className="history-entry-meta">
                  <span>{formatHistoryDate(e.editedAt)}</span>
                </div>
                <div className="history-entry-body">{e.previousBody}</div>
                {Array.isArray(e.previousHashtags) && e.previousHashtags.length > 0 && (
                  <div className="history-entry-tags">
                    {e.previousHashtags.map(t => (
                      <span key={t} className="feed-card-tag">#{t}</span>
                    ))}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function formatHistoryDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}
