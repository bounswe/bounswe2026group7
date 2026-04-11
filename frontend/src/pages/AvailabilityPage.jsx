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

const DAYS = [
  { key: 'MONDAY',    label: 'Mon' },
  { key: 'TUESDAY',   label: 'Tue' },
  { key: 'WEDNESDAY', label: 'Wed' },
  { key: 'THURSDAY',  label: 'Thu' },
  { key: 'FRIDAY',    label: 'Fri' },
  { key: 'SATURDAY',  label: 'Sat' },
  { key: 'SUNDAY',    label: 'Sun' },
]

// API returns LocalTime as { hour, minute, second, nano } — convert to "HH:mm"
function timeObjToStr(t) {
  if (!t) return '09:00'
  if (typeof t === 'string') return t.slice(0, 5)
  return `${String(t.hour).padStart(2, '0')}:${String(t.minute).padStart(2, '0')}`
}

function makeDefaultSlots() {
  return DAYS.map(({ key, label }) => ({
    key, label,
    start: '09:00',
    end: '18:00',
    on: key !== 'SATURDAY' && key !== 'SUNDAY',
  }))
}

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
            />
            <span className="time-sep">–</span>
            <input
              type="time"
              className="time-input"
              value={day.end}
              onChange={e => onTimeChange(day.key, 'end', e.target.value)}
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

  const [slots, setSlots] = useState(makeDefaultSlots())
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [status, setStatus] = useState(null)

  useEffect(() => {
    if (!userId) return
    setLoading(true)
    const fetch = isMentor ? getMentorAvailability(userId) : getMenteeAvailability()
    fetch
      .then(data => {
        if (!data || data.length === 0) return
        // Build map: dayOfWeek → first slot (UI supports one slot per day)
        const map = {}
        for (const slot of data) {
          if (!map[slot.dayOfWeek]) map[slot.dayOfWeek] = slot
        }
        setSlots(DAYS.map(({ key, label }) => {
          const s = map[key]
          return {
            key, label,
            start: s ? timeObjToStr(s.startTime) : '09:00',
            end:   s ? timeObjToStr(s.endTime)   : '18:00',
            on: !!s,
          }
        }))
      })
      .catch(() => {}) // keep defaults on error
      .finally(() => setLoading(false))
  }, [isMentor, userId])

  function toggleDay(key) {
    setSlots(prev => prev.map(d => d.key === key ? { ...d, on: !d.on } : d))
    setStatus(null)
  }

  function updateTime(key, field, value) {
    setSlots(prev => prev.map(d => d.key === key ? { ...d, [field]: value } : d))
    setStatus(null)
  }

  function validate() {
    if (!slots.some(d => d.on)) return 'Please enable at least one day.'
    for (const d of slots) {
      if (!d.on) continue
      if (!d.start || !d.end) return `${d.label}: start and end time are required.`
      if (d.start >= d.end) return `${d.label}: end time must be after start time.`
    }
    return null
  }

  async function handleSave() {
    const error = validate()
    if (error) { setStatus(error); return }

    setSaving(true)
    setStatus(null)
    try {
      const payload = {
        slots: slots
          .filter(d => d.on)
          .map(({ key, start, end }) => ({
            dayOfWeek: key,
            startTime: start,
            endTime: end,
            recurring: true,
          })),
      }
      if (isMentor) {
        await saveMentorAvailability(payload)
      } else {
        await saveMenteeAvailability(payload)
      }
      setStatus('success')
      setTimeout(() => setStatus(null), 3000)
    } catch (err) {
      const msg = err.message || ''
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
          <div className="page-title">Availability</div>
          <div className="page-sub">Set your weekly recurring availability</div>
        </div>
        <button className="action-btn" onClick={handleSave} disabled={saving || loading}>
          {saving ? 'Saving...' : 'Save'}
        </button>
      </div>

      {status === 'success' && (
        <div style={{
          background: 'var(--green-pale)', color: 'var(--green-dark)',
          border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)',
          padding: '12px 16px', marginBottom: '24px', fontSize: '14px', fontWeight: 500,
        }}>
          Availability saved successfully.
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

      {loading ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
          Loading availability...
        </div>
      ) : (
        <div className="card" style={{ maxWidth: '560px' }}>
          <div className="section-label">Weekly Schedule</div>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px' }}>
            Toggle days on or off and set your available hours. All slots repeat weekly.
          </p>
          {slots.map(d => (
            <DayRow
              key={d.key}
              day={d}
              onToggle={toggleDay}
              onTimeChange={updateTime}
            />
          ))}
        </div>
      )}
    </MainLayout>
  )
}
