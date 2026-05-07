import { useState, useEffect } from 'react'
import MainLayout from '../components/MainLayout'
import { useAuth } from '../context/AuthContext'
import {
  getMentorAvailability,
  saveMentorAvailability,
  getMenteeAvailability,
  saveMenteeAvailability,
} from '../services/api'
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

const DAY_TO_BACKEND = {
  Mon: 'MONDAY', Tue: 'TUESDAY', Wed: 'WEDNESDAY', Thu: 'THURSDAY',
  Fri: 'FRIDAY', Sat: 'SATURDAY', Sun: 'SUNDAY',
}
const DAY_FROM_BACKEND = Object.fromEntries(
  Object.entries(DAY_TO_BACKEND).map(([k, v]) => [v, k])
)

// Backend LocalTime arrives as { hour, minute, second, nano } or "HH:mm:ss".
function timeObjToStr(t) {
  if (!t) return null
  if (typeof t === 'string') return t.slice(0, 5)
  return `${String(t.hour).padStart(2, '0')}:${String(t.minute).padStart(2, '0')}`
}

const DURATIONS = [
  { label: '30 min', value: 30 },
  { label: '45 min', value: 45 },
  { label: '60 min', value: 60 },
  { label: '90 min', value: 90 },
]

const USER_TIMEZONE = Intl.DateTimeFormat().resolvedOptions().timeZone

function DayRow({ day, onToggle, onTimeChange }) {
  return (
    <div className={`day-row${day.on ? '' : ' off'}`}>
      <div className={`day-chip${day.on ? '' : ' off'}`}>{day.label}</div>

      <div className="time-range">
        {day.on ? (
          <>
            <input
              type="time"
              className="time-input"
              value={day.start}
              onChange={e => onTimeChange(day.key, 'start', e.target.value)}
              title="Start time"
            />
            <span className="time-sep">–</span>
            <input
              type="time"
              className="time-input"
              value={day.end}
              onChange={e => onTimeChange(day.key, 'end', e.target.value)}
              title="End time"
            />
          </>
        ) : (
          <span className="time-pill off">Not available</span>
        )}
      </div>

      <button
        className={`toggle${day.on ? '' : ' off'}`}
        onClick={() => onToggle(day.key)}
        aria-label={`${day.on ? 'Disable' : 'Enable'} ${day.label}`}
      />
    </div>
  )
}

export default function AvailabilityPage() {
  const { role, userId } = useAuth()
  const isMentor = role === 'MENTOR'

  const [days, setDays] = useState(INITIAL_DAYS)
  const [duration, setDuration] = useState(60)
  const [status, setStatus] = useState(null) // null | 'success' | string (error)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    setLoading(true)
    const request = isMentor
      ? getMentorAvailability(userId)
      : getMenteeAvailability()
    request
      .then(data => {
        if (cancelled) return
        const slots = Array.isArray(data) ? data : data?.slots ?? []
        if (slots.length === 0) return
        const byDay = {}
        for (const s of slots) {
          const uiKey = DAY_FROM_BACKEND[s.dayOfWeek] || s.dayOfWeek
          if (!byDay[uiKey]) byDay[uiKey] = s
        }
        setDays(INITIAL_DAYS.map(d => {
          const s = byDay[d.key]
          if (!s) return { ...d, on: false }
          return {
            ...d,
            start: timeObjToStr(s.startTime) || d.start,
            end: timeObjToStr(s.endTime) || d.end,
            on: true,
          }
        }))
      })
      .catch(() => {}) // keep defaults on error
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [isMentor, userId])

  function toggleDay(key) {
    setDays(prev => prev.map(d => d.key === key ? { ...d, on: !d.on } : d))
    setStatus(null)
  }

  function updateTime(key, field, value) {
    setDays(prev => prev.map(d => d.key === key ? { ...d, [field]: value } : d))
    setStatus(null)
  }

  function validate() {
    for (const d of days) {
      if (!d.on) continue
      if (!d.start || !d.end) return `${d.label}: start and end time are required.`
      if (d.start >= d.end) return `${d.label}: end time must be after start time.`
    }
    if (!days.some(d => d.on)) return 'Please enable at least one day.'
    return null
  }

  async function handleSave() {
    const error = validate()
    if (error) {
      setStatus(error)
      return
    }

    const payload = {
      slots: days
        .filter(d => d.on)
        .map(({ key, start, end }) => ({
          dayOfWeek: DAY_TO_BACKEND[key],
          startTime: start,
          endTime: end,
          recurring: true,
        })),
    }

    setSaving(true)
    setStatus(null)
    try {
      if (isMentor) {
        await saveMentorAvailability(payload)
      } else {
        await saveMenteeAvailability(payload)
      }
      setStatus('success')
    } catch (err) {
      const msg = err?.message || ''
      if (msg.includes('409') || msg.toLowerCase().includes('overlap')) {
        setStatus('Overlapping slots or invalid time range.')
      } else {
        setStatus(msg || 'Failed to save availability.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <div className="page-title">Edit Availability</div>
          <div className="page-sub">Timezone: {USER_TIMEZONE}</div>
        </div>
        <button className="action-btn" onClick={handleSave} disabled={saving || loading}>
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>

      {status === 'success' && (
        <div style={{
          background: 'var(--green-pale)', color: 'var(--green-dark)',
          border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)',
          padding: '12px 16px', marginBottom: '24px', fontSize: '14px', fontWeight: 500,
        }}>
          ✓ Availability saved successfully.
        </div>
      )}
      {status && status !== 'success' && (
        <div style={{
          background: 'var(--red-soft)', color: 'var(--red-text)',
          border: '1px solid #f0d0d0', borderRadius: 'var(--radius-sm)',
          padding: '12px 16px', marginBottom: '24px', fontSize: '14px',
        }}>
          {status}
        </div>
      )}

      {loading && (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
          Loading availability…
        </div>
      )}

      {!loading && <div className="avail-layout">
        <div className="card">
          <div className="section-label">Weekly Schedule</div>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px' }}>
            Toggle days on or off, then click the time fields to adjust your hours.
          </p>
          {days.map(d => (
            <DayRow
              key={d.key}
              day={d}
              onToggle={toggleDay}
              onTimeChange={updateTime}
            />
          ))}
        </div>

        <div>
          <div className="card">
            <div className="section-label">Session Duration</div>
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '16px' }}>
              Default length per mentoring session.
            </p>
            <div className="duration-grid">
              {DURATIONS.map(d => (
                <div
                  key={d.value}
                  className={`dur-btn${duration === d.value ? ' active' : ''}`}
                  onClick={() => { setDuration(d.value); setStatus(null) }}
                >
                  {d.label}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>}
    </MainLayout>
  )
}
