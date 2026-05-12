import { useEffect, useRef, useState } from 'react'

/**
 * Single-value taxonomy combobox (#448). Used for mentor.field (ISCED-F),
 * mentee.major (ISCED-F), and mentee.careerInterest (ESCO).
 *
 * Stores both label and URI. Selecting a suggestion fills both; typing a
 * label not in the suggestion list keeps it as free-text (URI stays
 * empty / null), matching the backend's "URI optional, validated when
 * present" contract.
 *
 * Props:
 *   label / uri              — current single value
 *   onChange({ label, uri }) — fires on every commit (suggestion or blur)
 *   search                   — async fn (query, opts) → [{label, identifierUri}]
 *   placeholder
 *   disabled
 */
const DEBOUNCE_MS = 250
const MIN_QUERY = 2

export default function TaxonomyAutocomplete({
  label = '', uri = '', onChange, search, placeholder = '', disabled = false, dataTestId,
}) {
  const [text, setText] = useState(label || '')
  const [storedUri, setStoredUri] = useState(uri || '')
  const [results, setResults] = useState([])
  const [busy, setBusy] = useState(false)
  const [open, setOpen] = useState(false)
  const [error, setError] = useState(null)
  const wrapRef = useRef(null)
  const debounceRef = useRef(null)
  const lang = typeof navigator !== 'undefined' ? (navigator.language || 'en').split('-')[0] : 'en'

  // Sync from parent when the controlled value changes externally (e.g. on first load).
  useEffect(() => { setText(label || ''); setStoredUri(uri || '') }, [label, uri])

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
        setResults(safe)
        setOpen(safe.length > 0)
      })
      .catch(() => {
        setResults([])
        setError('Suggestions unavailable. Your typed value will save as free text.')
      })
      .finally(() => setBusy(false))
  }

  function handleTextChange(e) {
    const v = e.target.value
    setText(v)
    // Typing a different value drops the linked URI — re-pick a suggestion to set it.
    if (storedUri) {
      setStoredUri('')
      onChange?.({ label: v, uri: '' })
    } else {
      onChange?.({ label: v, uri: '' })
    }
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => runSearch(v.trim()), DEBOUNCE_MS)
  }

  function selectHit(hit) {
    setText(hit.label)
    setStoredUri(hit.identifierUri || '')
    setOpen(false)
    onChange?.({ label: hit.label, uri: hit.identifierUri || '' })
  }

  return (
    <div className="tax-autocomplete-wrap" ref={wrapRef}>
      <input
        type="text"
        className={`form-input${storedUri ? ' tax-autocomplete-linked' : ''}`}
        value={text}
        onChange={handleTextChange}
        onFocus={() => results.length > 0 && setOpen(true)}
        placeholder={placeholder}
        disabled={disabled}
        data-testid={dataTestId}
        autoComplete="off"
        title={storedUri || undefined}
      />
      {open && results.length > 0 && (
        <ul className="tax-autocomplete-results" role="listbox">
          {results.map(hit => (
            <li key={`${hit.identifierUri}-${hit.label}`}>
              <button type="button" className="tax-autocomplete-result-btn" onClick={() => selectHit(hit)}>
                {hit.label}
              </button>
            </li>
          ))}
        </ul>
      )}
      {busy && <div className="tax-autocomplete-hint">Searching…</div>}
      {error && <div className="tax-autocomplete-error">{error}</div>}
    </div>
  )
}
