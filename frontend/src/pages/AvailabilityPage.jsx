import { useState } from 'react'
import MainLayout from '../components/MainLayout'
import '../styles/main.css'

const INITIAL_DAYS = [
  { key: 'Mon', label: 'Mon', start: '09:00', end: '18:00', on: true },
  { key: 'Tue', label: 'Tue', start: '09:00', end: '18:00', on: true },
  { key: 'Wed', label: 'Wed', start: '09:00', end: '18:00', on: true },
  { key: 'Thu', label: 'Thu', start: '09:00', end: '18:00', on: true },
  { key: 'Fri', label: 'Fri', start: '09:00', end: '18:00', on: true },
  { key: 'Sat', label: 'Sat', start: '09:00', end: '18:00', on: false },
  { key: 'Sun', label: 'Sun', start: '09:00', end: '18:00', on: false },
]

const DURATIONS = ['30 min', '45 min', '60 min', '90 min']

// TODO: apply user's local timezone when sending to backend
const USER_TIMEZONE = Intl.DateTimeFormat().resolvedOptions().timeZone

export default function AvailabilityPage() {
  const [days, setDays] = useState(INITIAL_DAYS)
  const [duration, setDuration] = useState('60 min')
  const [saved, setSaved] = useState(false)
  const [timeError, setTimeError] = useState('')

  function toggleDay(key) {
    setDays(prev => prev.map(d => d.key === key ? { ...d, on: !d.on } : d))
    setTimeError('')
    setSaved(false)
  }

  function updateTime(key, field, value) {
    setDays(prev => prev.map(d => d.key === key ? { ...d, [field]: value } : d))
    setTimeError('')
    setSaved(false)
  }

  function validate() {
    for (const d of days) {
      if (!d.on) continue
      if (!d.start || !d.end) return `${d.label}: start and end time are required.`
      if (d.start >= d.end) return `${d.label}: end time must be after start time.`
    }
    return ''
  }

  function handleSave() {
    const error = validate()
    if (error) {
      setTimeError(error)
      return
    }

    const durationMinutes = parseInt(duration)
    const availability = days
      .filter(d => d.on)
      .map(d => ({
        day: d.key,
        start: d.start,
        end: d.end,
      }))

    const payload = {
      timezone: USER_TIMEZONE,
      sessionDurationMinutes: durationMinutes,
      availability,
    }

    // TODO: send payload to backend API
    console.log('Availability payload:', JSON.stringify(payload, null, 2))
    setSaved(true)
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div><div className="page-title">Edit Availability</div></div>
        <button className="action-btn" onClick={handleSave}>Save</button>
      </div>

      {saved && (
        <div style={{
          background: 'var(--green-pale)', color: 'var(--green-dark)',
          border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)',
          padding: '12px 16px', marginBottom: '24px', fontSize: '14px', fontWeight: 500,
        }}>
          Availability saved successfully.
        </div>
      )}

      {timeError && (
        <div style={{
          background: 'var(--red-soft)', color: 'var(--red-text)',
          border: '1px solid #f0d0d0', borderRadius: 'var(--radius-sm)',
          padding: '12px 16px', marginBottom: '24px', fontSize: '14px',
        }}>
          {timeError}
        </div>
      )}

      <div className="avail-layout">
        <div className="card">
          <p style={{ fontSize: '14px', color: 'var(--text-mid)', marginBottom: '4px' }}>
            Set the days and hours you are available each week.
          </p>
          <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '24px' }}>
            Timezone: {USER_TIMEZONE}
          </p>

          {days.map(d => (
            <div className="day-row" key={d.key}>
              <div className={`day-chip${d.on ? '' : ' off'}`}>{d.label}</div>

              <div className="time-range" style={{ flex: 1 }}>
                {d.on ? (
                  <>
                    <input
                      type="time"
                      value={d.start}
                      onChange={e => updateTime(d.key, 'start', e.target.value)}
                      style={{
                        padding: '8px 12px',
                        background: 'var(--green-pale)', color: 'var(--green-dark)',
                        border: '1px solid var(--border)', borderRadius: '8px',
                        fontSize: '14px', fontWeight: 500,
                        fontFamily: 'DM Sans, sans-serif', cursor: 'pointer',
                        outline: 'none',
                      }}
                    />
                    <span style={{ color: 'var(--text-muted)' }}>–</span>
                    <input
                      type="time"
                      value={d.end}
                      onChange={e => updateTime(d.key, 'end', e.target.value)}
                      style={{
                        padding: '8px 12px',
                        background: 'var(--green-pale)', color: 'var(--green-dark)',
                        border: '1px solid var(--border)', borderRadius: '8px',
                        fontSize: '14px', fontWeight: 500,
                        fontFamily: 'DM Sans, sans-serif', cursor: 'pointer',
                        outline: 'none',
                      }}
                    />
                  </>
                ) : (
                  <span className="time-pill off">Not available</span>
                )}
              </div>

              <button
                className={`toggle${d.on ? '' : ' off'}`}
                onClick={() => toggleDay(d.key)}
                aria-label={`Toggle ${d.label}`}
              />
            </div>
          ))}
        </div>

        <div>
          <div className="card">
            <div className="section-label">Session Duration</div>
            <div className="duration-grid">
              {DURATIONS.map(dur => (
                <div
                  key={dur}
                  className={`dur-btn${duration === dur ? ' active' : ''}`}
                  onClick={() => { setDuration(dur); setSaved(false) }}
                >
                  {dur}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </MainLayout>
  )
}
