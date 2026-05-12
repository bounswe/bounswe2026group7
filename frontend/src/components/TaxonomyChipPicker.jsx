import { useEffect, useRef, useState } from 'react'

/**
 * Multi-value taxonomy combobox (#448). Used for skills (ESCO), interests
 * / hobbies (Wikidata), and preferred-mentee-skills (ESCO). Renders an
 * input row that turns labels into chips on add — typing followed by
 * Enter / comma / suggestion-click commits the chip; clicking the X on a
 * chip removes it.
 *
 * Free-text fallback: any chip without a matching suggestion keeps a null
 * URI; the backend's parallel-array contract tolerates per-entry nulls so
 * the user is never blocked.
 *
 * Props:
 *   values         — [{ label, uri }]  current chips
 *   onChange(next) — fires with the new array on every add / remove
 *   search         — async fn (query, opts) → [{label, identifierUri}]
 *   placeholder
 *   disabled
 *   max            — optional ceiling (defaults to 20, matching the
 *                    backend @Size(max=20) on the URI lists)
 *   dataTestId
 */
const DEBOUNCE_MS = 250
const MIN_QUERY = 2
const DEFAULT_MAX = 20

export default function TaxonomyChipPicker({
  values = [], onChange, search, placeholder = '', disabled = false, max = DEFAULT_MAX, dataTestId,
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [busy, setBusy] = useState(false)
  const [open, setOpen] = useState(false)
  const [error, setError] = useState(null)
  const wrapRef = useRef(null)
  const debounceRef = useRef(null)
  const lang = typeof navigator !== 'undefined' ? (navigator.language || 'en').split('-')[0] : 'en'

  useEffect(() => {
    function handleClickOutside(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  function runSearch(q) {
    if (q.length < MIN_QUERY) {
      setResults([])
      setOpen(false)
      return
    }
    setBusy(true)
    setError(null)
    search(q, { lang })
      .then(list => {
        const safe = Array.isArray(list) ? list : []
        // Hide already-picked labels from the dropdown
        const picked = new Set(values.map(v => v.label.toLowerCase()))
        const filtered = safe.filter(h => !picked.has(h.label.toLowerCase()))
        setResults(filtered)
        setOpen(filtered.length > 0)
      })
      .catch(() => {
        setResults([])
        setError('Suggestions unavailable. You can still type a value.')
      })
      .finally(() => setBusy(false))
  }

  function handleQueryChange(e) {
    const v = e.target.value
    setQuery(v)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => runSearch(v.trim()), DEBOUNCE_MS)
  }

  function addChip({ label, uri = '' }) {
    const trimmed = label.trim()
    if (!trimmed) return
    if (values.length >= max) return
    // De-duplicate case-insensitively.
    if (values.some(v => v.label.toLowerCase() === trimmed.toLowerCase())) return
    onChange?.([...values, { label: trimmed, uri }])
    setQuery('')
    setResults([])
    setOpen(false)
  }

  function removeChip(idx) {
    if (disabled) return
    const next = values.slice(0, idx).concat(values.slice(idx + 1))
    onChange?.(next)
  }

  function handleKeyDown(e) {
    if ((e.key === 'Enter' || e.key === ',') && query.trim()) {
      e.preventDefault()
      addChip({ label: query.trim() })
    } else if (e.key === 'Backspace' && !query && values.length > 0) {
      removeChip(values.length - 1)
    }
  }

  return (
    <div className="tax-chips-wrap" ref={wrapRef}>
      <div className="tax-chips-input-row">
        {values.map((v, i) => (
          <span key={`${v.label}-${i}`} className={`tax-chip${v.uri ? ' tax-chip--linked' : ''}`} title={v.uri || 'Free-text (no canonical URI)'}>
            {v.label}
            <button
              type="button"
              className="tax-chip-remove"
              onClick={() => removeChip(i)}
              disabled={disabled}
              aria-label={`Remove ${v.label}`}
            >×</button>
          </span>
        ))}
        <input
          type="text"
          className="tax-chips-input"
          value={query}
          onChange={handleQueryChange}
          onKeyDown={handleKeyDown}
          onFocus={() => results.length > 0 && setOpen(true)}
          placeholder={values.length === 0 ? placeholder : ''}
          disabled={disabled || values.length >= max}
          data-testid={dataTestId}
          autoComplete="off"
        />
      </div>
      {open && results.length > 0 && (
        <ul className="tax-autocomplete-results" role="listbox">
          {results.map(hit => (
            <li key={`${hit.identifierUri}-${hit.label}`}>
              <button type="button" className="tax-autocomplete-result-btn" onClick={() => addChip({ label: hit.label, uri: hit.identifierUri || '' })}>
                {hit.label}
              </button>
            </li>
          ))}
        </ul>
      )}
      {busy && <div className="tax-autocomplete-hint">Searching…</div>}
      {error && <div className="tax-autocomplete-error">{error}</div>}
      {values.length >= max && (
        <div className="tax-autocomplete-hint">Max {max} entries reached.</div>
      )}
    </div>
  )
}
