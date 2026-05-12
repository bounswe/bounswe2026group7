import { useState, useEffect, useRef } from 'react'

/**
 * LocationPicker Component
 * Allows users to search for a city or use current geolocation.
 * Includes rounding for privacy and validation.
 */
export default function LocationPicker({ city, latitude, longitude, onChange, error }) {
  const [search, setSearch] = useState(city || '')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [geoLoading, setGeoLoading] = useState(false)
  const [showDropdown, setShowDropdown] = useState(false)
  const dropdownRef = useRef(null)

  useEffect(() => {
    setSearch(city || '')
  }, [city])

  // Handle clicking outside to close dropdown
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setShowDropdown(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const roundToTwo = (num) => {
    if (num == null) return null
    return Math.round((num + Number.EPSILON) * 100) / 100
  }

  const handleSearch = async (query) => {
    setSearch(query)
    if (query.length < 3) {
      setResults([])
      setShowDropdown(false)
      return
    }

    setLoading(true)
    try {
      // Nominatim API search
      const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=5&addressdetails=1&featuretype=city`, {
        headers: {
          'User-Agent': 'Bounswe2026Group7-App'
        }
      })
      const data = await res.json()
      setResults(data)
      setShowDropdown(data.length > 0)
    } catch (err) {
      console.error('Search failed:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleSelect = (item) => {
    // Extract city name from display_name (usually the first part)
    const cityName = item.address.city || item.address.town || item.address.village || item.display_name.split(',')[0]
    const lat = roundToTwo(parseFloat(item.lat))
    const lon = roundToTwo(parseFloat(item.lon))
    
    setSearch(cityName)
    setShowDropdown(false)
    onChange({ city: cityName, latitude: lat, longitude: lon })
  }

  const handleUseCurrentLocation = () => {
    if (!navigator.geolocation) {
      alert('Geolocation is not supported by your browser.')
      return
    }

    setGeoLoading(true)
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const rawLat = position.coords.latitude
        const rawLon = position.coords.longitude
        
        // Round for privacy
        const lat = roundToTwo(rawLat)
        const lon = roundToTwo(rawLon)
        
        try {
          // Reverse geocoding to get city name
          const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${rawLat}&lon=${rawLon}&addressdetails=1`, {
            headers: {
              'User-Agent': 'Bounswe2026Group7-App'
            }
          })
          const data = await res.json()
          const cityName = data.address.city || data.address.town || data.address.village || data.address.suburb || 'Selected Location'
          
          setSearch(cityName)
          onChange({ city: cityName, latitude: lat, longitude: lon })
        } catch (err) {
          console.error('Reverse geocoding failed:', err)
          // Fallback to coordinates if city name fetch fails
          setSearch(`Location (${lat}, ${lon})`)
          onChange({ city: 'Current Location', latitude: lat, longitude: lon })
        } finally {
          setGeoLoading(false)
        }
      },
      (err) => {
        setGeoLoading(false)
        let msg = 'Failed to get location.'
        if (err.code === 1) msg = 'Location permission denied. Please search manually.'
        else if (err.code === 2) msg = 'Location unavailable.'
        else if (err.code === 3) msg = 'Location request timed out.'
        console.warn('Geolocation error:', msg)
        // Show a non-blocking alert or message
        alert(msg)
      },
      { timeout: 10000 }
    )
  }

  return (
    <div className="form-field" style={{ position: 'relative' }}>
      <label className="form-label">Location</label>
      <div style={{ display: 'flex', gap: '8px' }}>
        <div style={{ flex: 1, position: 'relative' }}>
          <input
            className="form-input"
            type="text"
            value={search}
            onChange={(e) => handleSearch(e.target.value)}
            onFocus={() => { if (results.length > 0) setShowDropdown(true) }}
            placeholder="Search for a city..."
            autoComplete="off"
            style={{ paddingRight: loading ? '35px' : '14px' }}
          />
          {loading && (
             <div style={{ 
               position: 'absolute', right: '12px', top: '50%', transform: 'translateY(-50%)',
               width: '16px', height: '16px', border: '2px solid #ccc', borderTopColor: 'var(--green-dark)',
               borderRadius: '50%', animation: 'spin 0.6s linear infinite'
             }} />
          )}
        </div>
        <button
          type="button"
          className="btn-secondary"
          style={{ 
            width: '42px', height: '42px', padding: 0, 
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: 'var(--green-bg)', borderColor: 'var(--border)',
            color: 'var(--green-dark)', borderRadius: '12px'
          }}
          onClick={handleUseCurrentLocation}
          disabled={geoLoading}
          title="Use current location"
        >
          {geoLoading ? (
            <div style={{ 
              width: '18px', height: '18px', border: '2px solid #ccc', borderTopColor: 'var(--green-dark)',
              borderRadius: '50%', animation: 'spin 0.6s linear infinite'
            }} />
          ) : (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
              <circle cx="12" cy="10" r="3" />
            </svg>
          )}
        </button>
      </div>

      {showDropdown && (
        <div 
          ref={dropdownRef}
          style={{
            position: 'absolute',
            top: '74px',
            left: 0,
            right: '50px',
            background: 'white',
            border: '1px solid var(--border)',
            borderRadius: '12px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
            zIndex: 1000,
            maxHeight: '240px',
            overflowY: 'auto'
          }}
        >
          {results.map((item) => (
            <div
              key={item.place_id}
              style={{
                padding: '12px 16px',
                cursor: 'pointer',
                borderBottom: '1px solid var(--green-bg)',
                fontSize: '14px',
                transition: 'background 0.2s',
                color: 'var(--text-dark)'
              }}
              onMouseEnter={(e) => e.target.style.background = 'var(--green-bg)'}
              onMouseLeave={(e) => e.target.style.background = 'transparent'}
              onClick={() => handleSelect(item)}
            >
              {item.display_name}
            </div>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginTop: '8px' }}>
        <div style={{ 
          fontSize: '11px', color: 'var(--text-muted)', lineHeight: '1.4', 
          display: 'flex', alignItems: 'center', gap: '4px' 
        }}>
          <span role="img" aria-label="privacy">🔒</span>
          <span>Privacy Note: Your location is approximate (rounded to ~1km).</span>
        </div>
        {(latitude != null && longitude != null) && (
          <div style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 600 }}>
            {latitude}, {longitude}
          </div>
        )}
      </div>
      {error && <div style={{ color: 'var(--red-text)', fontSize: '13px', marginTop: '6px', fontWeight: 500 }}>{error}</div>}
      
      <style>{`
        @keyframes spin { to { transform: translateY(-50%) rotate(360deg); } }
      `}</style>
    </div>
  )
}
