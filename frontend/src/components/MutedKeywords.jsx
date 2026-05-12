import { useEffect, useState } from 'react'
import { getMutedKeywords, addMutedKeyword, removeMutedKeyword } from '../services/api'

/**
 * Per-user keyword-mute settings (#543 / backend #486). Mutes are applied
 * server-side on every feed read, so this UI is just a CRUD list — no
 * client-side filter logic.
 *
 * Backend lowercases the keyword and rejects characters outside [a-z0-9 -]
 * after normalisation. We surface the server error message verbatim because
 * it's already user-friendly (e.g. "Keyword already muted").
 */
export default function MutedKeywords() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [draft, setDraft] = useState('')
  const [adding, setAdding] = useState(false)
  const [addError, setAddError] = useState(null)
  const [removingId, setRemovingId] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getMutedKeywords()
      .then(list => { if (!cancelled) setItems(Array.isArray(list) ? list : []) })
      .catch(err => { if (!cancelled) setError(err?.message || 'Failed to load muted keywords.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  async function handleAdd(e) {
    e.preventDefault()
    const trimmed = draft.trim()
    if (!trimmed || adding) return
    setAdding(true)
    setAddError(null)
    try {
      const saved = await addMutedKeyword(trimmed)
      setItems(prev => [saved, ...prev])
      setDraft('')
    } catch (err) {
      setAddError(err?.message || 'Failed to add keyword.')
    } finally {
      setAdding(false)
    }
  }

  async function handleRemove(id) {
    if (removingId) return
    setRemovingId(id)
    const previous = items
    setItems(prev => prev.filter(m => m.id !== id))
    try {
      await removeMutedKeyword(id)
    } catch (err) {
      setItems(previous)
      setError(err?.message || 'Failed to remove keyword.')
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <div className="card" style={{ marginTop: '24px' }} data-testid="muted-keywords">
      <div className="section-label">Muted Keywords</div>
      <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px', marginBottom: '12px' }}>
        Posts whose body contains any of these substrings are hidden from your feed everywhere.
        Server lowercases each keyword; only letters, numbers, spaces, and hyphens are allowed.
      </p>

      <form onSubmit={handleAdd} style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
        <input
          className="form-input"
          type="text"
          placeholder="Add a keyword (e.g. crypto)"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          maxLength={120}
          disabled={adding}
          aria-label="New muted keyword"
        />
        <button
          type="submit"
          className="action-btn"
          disabled={adding || !draft.trim()}
        >
          {adding ? 'Adding…' : 'Add'}
        </button>
      </form>

      {addError && (
        <p style={{ fontSize: '12px', color: 'var(--red-text)', marginBottom: '8px' }}>{addError}</p>
      )}

      {loading ? (
        <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>Loading…</p>
      ) : error ? (
        <p style={{ fontSize: '13px', color: 'var(--red-text)' }}>{error}</p>
      ) : items.length === 0 ? (
        <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>No muted keywords yet.</p>
      ) : (
        <ul className="muted-kw-list">
          {items.map(m => (
            <li key={m.id} className="muted-kw-chip">
              <span className="muted-kw-label">{m.keyword}</span>
              <button
                type="button"
                className="muted-kw-remove"
                onClick={() => handleRemove(m.id)}
                disabled={removingId === m.id}
                aria-label={`Remove muted keyword ${m.keyword}`}
                title="Remove"
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
