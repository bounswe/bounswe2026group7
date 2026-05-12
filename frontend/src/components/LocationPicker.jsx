import { useEffect, useRef, useState } from 'react'

/**
 * Location entry widget (#461). Backend stores `city`, `latitude`,
 * `longitude` on the user profile (PATCH /api/users/me/{role}).
 *
 * Two parallel input paths for the user:
 *  1. "Use my current location" — calls navigator.geolocation, reverse-
 *     geocodes the result via Nominatim (OpenStreetMap), and fills the
 *     city + rounded coordinates.
 *  2. Manual search — typeahead against Nominatim's free `/search`
 *     endpoint. Selecting a suggestion fills city + coords from the
 *     geocoding result.
 *
 * Privacy: coordinates are rounded to 2 decimal places (~1 km precision)
 * before being handed back to the parent form. Backend Bean Validation
 * already caps lat ∈ [-90, 90] and lon ∈ [-180, 180].
 *
 * Props:
 *   city / latitude / longitude — current values from parent
 *   onChange({ city, latitude, longitude }) — fires on selection
 *   disabled — disables interaction while the parent is saving
 */

const SEARCH_URL = 'https://nominatim.openstreetmap.org/search'
const REVERSE_URL = 'https://nominatim.openstreetmap.org/reverse'

function roundCoord(n) {
  if (n == null || Number.isNaN(n)) return null
  return Math.round(n * 100) / 100
}

function fmtCoords(lat, lon) {
  if (lat == null || lon == null) return ''
  return `${roundCoord(lat)}, ${roundCoord(lon)}`
}

function pickCityFromAddress(item) {
  if (item.address) {
    const addr = item.address
    return addr.city || addr.town || addr.village || addr.hamlet || addr.county || ''
  }
  // Fallback: first segment of the display_name
  return String(item.display_name || '').split(',')[0]?.trim() || ''
}

export default function LocationPicker({ city, latitude, longitude, onChange, disabled = false }) {
  const [query, setQuery] = useState(city || '')
  const [results, setResults] = useState([])
  const [searchBusy, setSearchBusy] = useState(false)
  const [searchError, setSearchError] = useState(null)
  const [geoBusy, setGeoBusy] = useState(false)
  const [geoError, setGeoError] = useState(null)
  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)
  const debounceRef = useRef(null)

  useEffect(() => {
    setQuery(city || '')
  }, [city])

  useEffect(() => {
    function handleClickOutside(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  function searchNominatim(q) {
    if (q.length < 3) {
      setResults([])
      setOpen(false)
      return
    }
    setSearchBusy(true)
    setSearchError(null)
    fetch(`${SEARCH_URL}?format=json&limit=6&addressdetails=1&q=${encodeURIComponent(q)}`)
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then(list => {
        setResults(Array.isArray(list) ? list : [])
        setOpen((list?.length || 0) > 0)
      })
      .catch(() => {
        setResults([])
        setSearchError('Search unavailable. Try again or enter coordinates manually.')
      })
      .finally(() => setSearchBusy(false))
  }

  function handleQueryChange(e) {
    const v = e.target.value
    setQuery(v)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => searchNominatim(v.trim()), 250)
  }

  function selectResult(item) {
    const cityName = pickCityFromAddress(item)
    const lat = roundCoord(parseFloat(item.lat))
    const lon = roundCoord(parseFloat(item.lon))
    setQuery(cityName || item.display_name?.split(',')[0] || '')
    setResults([])
    setOpen(false)
    onChange?.({ city: cityName, latitude: lat, longitude: lon })
  }

  function useCurrentLocation() {
    if (geoBusy || disabled) return
    if (!navigator.geolocation) {
      setGeoError('Your browser does not support geolocation.')
      return
    }
    setGeoBusy(true)
    setGeoError(null)
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lat = roundCoord(pos.coords.latitude)
        const lon = roundCoord(pos.coords.longitude)
        try {
          const res = await fetch(
            `${REVERSE_URL}?format=json&zoom=10&lat=${lat}&lon=${lon}`,
          )
          const body = res.ok ? await res.json() : null
          const cityName = body ? pickCityFromAddress(body) : ''
          onChange?.({ city: cityName || '', latitude: lat, longitude: lon })
          setQuery(cityName || '')
        } catch {
          // Reverse failed — still keep the rounded coords; city stays empty.
          onChange?.({ city: '', latitude: lat, longitude: lon })
          setQuery('')
        } finally {
          setGeoBusy(false)
        }
      },
      (err) => {
        setGeoBusy(false)
        if (err.code === err.PERMISSION_DENIED) {
          setGeoError('Permission denied. Enter your city manually.')
        } else if (err.code === err.POSITION_UNAVAILABLE) {
          setGeoError('Your location could not be determined.')
        } else {
          setGeoError('Couldn’t fetch your location. Enter it manually.')
        }
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 },
    )
  }

  function clearLocation() {
    if (disabled) return
    setQuery('')
    setResults([])
    setOpen(false)
    onChange?.({ city: null, latitude: null, longitude: null })
  }

  const hasLocation = (city && city.length > 0) || latitude != null || longitude != null

  return (
    <div className="loc-picker" ref={wrapRef}>
      <div className="loc-picker-row">
        <input
          type="text"
          className="form-input loc-picker-input"
          value={query}
          onChange={handleQueryChange}
          onFocus={() => results.length > 0 && setOpen(true)}
          placeholder="Search a city (e.g. Istanbul)"
          disabled={disabled}
          maxLength={100}
        />
        <button
          type="button"
          className="action-btn"
          onClick={useCurrentLocation}
          disabled={disabled || geoBusy}
        >
          {geoBusy ? 'Locating…' : 'Use current location'}
        </button>
      </div>

      {open && results.length > 0 && (
        <ul className="loc-picker-results" role="listbox">
          {results.map(r => (
            <li key={`${r.place_id}-${r.lat}-${r.lon}`}>
              <button
                type="button"
                className="loc-picker-result-btn"
                onClick={() => selectResult(r)}
              >
                <span className="loc-picker-result-name">
                  {pickCityFromAddress(r) || r.display_name}
                </span>
                <span className="loc-picker-result-sub">{r.display_name}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {(searchError || geoError) && (
        <div className="loc-picker-error">{searchError || geoError}</div>
      )}

      {(hasLocation || searchBusy) && (
        <div className="loc-picker-status">
          {searchBusy && <span>Searching…</span>}
          {!searchBusy && hasLocation && (
            <>
              <span>
                {city || 'Coordinates set'}
                {(latitude != null || longitude != null) && (
                  <span className="loc-picker-coords"> · {fmtCoords(latitude, longitude)}</span>
                )}
              </span>
              <button
                type="button"
                className="loc-picker-clear"
                onClick={clearLocation}
                disabled={disabled}
              >
                Clear
              </button>
            </>
          )}
        </div>
      )}

      <p className="loc-picker-privacy">
        We use your location to recommend nearby mentors. Coordinates are rounded
        to ~1 km precision before being sent to the server.
      </p>
    </div>
  )
}
