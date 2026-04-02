import { useState } from 'react'
import MainLayout from '../components/MainLayout'
import '../styles/main.css'

const INITIAL_DAYS = [
  { key: 'Mon', label: 'Mon', start: '09:00', end: '18:00', on: true },
  { key: 'Tue', label: 'Tue', start: '09:00', end: '18:00', on: true },
  { key: 'Wed', label: 'Wed', start: '09:00', end: '18:00', on: true },
  { key: 'Thu', label: 'Thu', start: '09:00', end: '18:00', on: true },
  { key: 'Fri', label: 'Fri', start: '09:00', end: '18:00', on: true },
  { key: 'Sat', label: 'Sat', start: '', end: '', on: false },
  { key: 'Sun', label: 'Sun', start: '', end: '', on: false },
]

const DURATIONS = ['30 min', '45 min', '60 min', '90 min']

export default function AvailabilityPage() {
  const [days, setDays] = useState(INITIAL_DAYS)
  const [duration, setDuration] = useState('60 min')

  function toggleDay(key) {
    setDays(prev => prev.map(d =>
      d.key === key ? { ...d, on: !d.on } : d
    ))
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div><div className="page-title">Edit Availability</div></div>
        <button className="action-btn">Save</button>
      </div>

      <div className="avail-layout">
        <div className="card">
          <p style={{ fontSize: '14px', color: 'var(--text-mid)', marginBottom: '24px' }}>
            Set the days and hours you are available each week.
          </p>
          {days.map(d => (
            <div className="day-row" key={d.key}>
              <div className={`day-chip${d.on ? '' : ' off'}`}>{d.label}</div>
              <div className="time-range" style={{ flex: 1 }}>
                {d.on ? (
                  <>
                    <span className="time-pill">{d.start}</span>
                    <span style={{ color: 'var(--text-muted)' }}>–</span>
                    <span className="time-pill">{d.end}</span>
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
                  onClick={() => setDuration(dur)}
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
