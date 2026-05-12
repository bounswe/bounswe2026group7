import { useEffect, useMemo, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import MainLayout from '../components/MainLayout'
import {
  getActiveMentorships,
  listMentorshipMeetings,
  getMentorAvailability,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

/**
 * Weekly calendar view (#123). Renders, for the current week:
 *  - Mentor's availability slots as background bands (mentor view only).
 *  - Confirmed and pending meetings as overlay blocks (both viewers).
 *
 * Click an empty hour cell on a day → /schedule with the date pre-filled in
 * the query string so the existing meeting-creation modal there can pick it
 * up. Click a meeting → /mentorships/:id with a `#meeting-{id}` anchor.
 *
 * Scope kept tight: weekly grid only, no month / day views, no drag-create.
 */

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const DAY_KEYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const HOUR_START = 8
const HOUR_END = 22
const HOUR_PX = 44

function startOfIsoWeek(date) {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  const dow = d.getDay() // 0=Sunday..6=Saturday
  const diff = (dow + 6) % 7 // distance back to Monday
  d.setDate(d.getDate() - diff)
  return d
}

function addDays(date, n) {
  const d = new Date(date)
  d.setDate(d.getDate() + n)
  return d
}

function isSameDay(a, b) {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate()
}

function fmtDayLabel(d) {
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

function fmtWeekRange(monday) {
  const sunday = addDays(monday, 6)
  const sameMonth = monday.getMonth() === sunday.getMonth()
  const left = monday.toLocaleDateString('en-GB', { day: 'numeric', month: sameMonth ? undefined : 'short' })
  const right = sunday.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
  return `${left} – ${right}`
}

function parseHHMM(t) {
  // Backend serialises LocalTime as "HH:mm:ss". Defensive against bad input.
  if (!t) return 0
  const parts = String(t).split(':')
  const h = Number(parts[0] || 0)
  const m = Number(parts[1] || 0)
  return h + m / 60
}

export default function CalendarPage() {
  const navigate = useNavigate()
  const { userId, role } = useAuth()
  const isMentor = role === 'MENTOR'

  const [weekStart, setWeekStart] = useState(() => startOfIsoWeek(new Date()))
  const [meetings, setMeetings] = useState([])
  const [slots, setSlots] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const days = useMemo(
    () => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)),
    [weekStart]
  )
  const today = useMemo(() => { const t = new Date(); t.setHours(0, 0, 0, 0); return t }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const mentorships = await getActiveMentorships().catch(() => [])
      const meetingArrays = await Promise.all(
        (mentorships || []).map(m =>
          listMentorshipMeetings(m.id)
            .then(list => (list || []).map(meeting => ({
              ...meeting,
              mentorshipId: m.id,
              counterpartName: isMentor ? m.menteeFirstName : m.mentorFirstName,
            })))
            .catch(() => [])
        )
      )
      setMeetings(meetingArrays.flat())

      if (isMentor && userId) {
        const av = await getMentorAvailability(userId).catch(() => null)
        setSlots(Array.isArray(av) ? av : (av?.slots || []))
      } else {
        setSlots([])
      }
    } catch (err) {
      setError(err?.message || 'Failed to load calendar.')
    } finally {
      setLoading(false)
    }
  }, [isMentor, userId])

  useEffect(() => { load() }, [load])

  // ── Slot positioning helpers ──────────────────────────────────────────
  function meetingsForDay(day) {
    return meetings.filter(m => {
      const start = new Date(m.startTime)
      return !isNaN(start.getTime()) && isSameDay(start, day)
    })
  }

  function slotsForDay(day) {
    if (!isMentor || !slots.length) return []
    const dayKey = DAY_KEYS[(day.getDay() + 6) % 7]
    return slots.filter(s => s.dayOfWeek === dayKey)
  }

  function topPxFromHours(h) {
    return (h - HOUR_START) * HOUR_PX
  }

  function meetingBlockStyle(meeting) {
    const start = new Date(meeting.startTime)
    const startHours = start.getHours() + start.getMinutes() / 60
    const durationH = Math.max(0.5, (Number(meeting.durationMin) || 30) / 60)
    return {
      top: `${topPxFromHours(startHours)}px`,
      height: `${durationH * HOUR_PX - 2}px`,
    }
  }

  function slotBandStyle(slot) {
    const startH = parseHHMM(slot.startTime)
    const endH = parseHHMM(slot.endTime)
    return {
      top: `${topPxFromHours(startH)}px`,
      height: `${(endH - startH) * HOUR_PX}px`,
    }
  }

  function meetingClassFor(status) {
    switch (status) {
      case 'CONFIRMED': return 'cal-meeting cal-meeting--confirmed'
      case 'PENDING_CONFIRMATION': return 'cal-meeting cal-meeting--pending'
      case 'COMPLETED': return 'cal-meeting cal-meeting--past'
      default: return 'cal-meeting cal-meeting--other'
    }
  }

  function handleEmptyCellClick(day, hour) {
    // Pre-fill date+hour into /schedule's query string so its existing
    // create-meeting modal can pick it up. ISO 8601 local-date w/ hour suffix.
    const yyyy = day.getFullYear()
    const mm = String(day.getMonth() + 1).padStart(2, '0')
    const dd = String(day.getDate()).padStart(2, '0')
    const hh = String(hour).padStart(2, '0')
    navigate(`/schedule?prefillDate=${yyyy}-${mm}-${dd}&prefillHour=${hh}`)
  }

  function handleMeetingClick(meeting) {
    navigate(`/mentorships/${meeting.mentorshipId}#meeting-${meeting.id}`)
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <div className="page-title">Calendar</div>
          <div className="page-sub">
            {isMentor
              ? 'Your availability and upcoming meetings.'
              : 'Your upcoming meetings, week by week.'}
          </div>
        </div>
        <div className="cal-nav">
          <button
            type="button"
            className="cal-nav-btn"
            onClick={() => setWeekStart(addDays(weekStart, -7))}
            aria-label="Previous week"
          >
            <ChevronLeft size={16} strokeWidth={2} />
          </button>
          <button
            type="button"
            className="cal-nav-btn cal-nav-today"
            onClick={() => setWeekStart(startOfIsoWeek(new Date()))}
          >
            Today
          </button>
          <button
            type="button"
            className="cal-nav-btn"
            onClick={() => setWeekStart(addDays(weekStart, 7))}
            aria-label="Next week"
          >
            <ChevronRight size={16} strokeWidth={2} />
          </button>
          <span className="cal-week-label">{fmtWeekRange(weekStart)}</span>
        </div>
      </div>

      {error && (
        <div className="md-error-card" style={{ marginBottom: '12px' }}>
          <div className="md-error-title">Couldn’t load calendar</div>
          <div className="md-error-sub">{error}</div>
        </div>
      )}

      <div className="cal-grid">
        <div className="cal-grid-header">
          <div className="cal-grid-corner" aria-hidden="true" />
          {days.map((d, i) => (
            <div
              key={i}
              className={`cal-day-header${isSameDay(d, today) ? ' cal-day-header--today' : ''}`}
            >
              <div className="cal-day-name">{DAY_NAMES[i]}</div>
              <div className="cal-day-date">{fmtDayLabel(d)}</div>
            </div>
          ))}
        </div>

        <div className="cal-grid-body">
          <div className="cal-hours-col">
            {Array.from({ length: HOUR_END - HOUR_START }, (_, i) => HOUR_START + i).map(h => (
              <div key={h} className="cal-hour-label" style={{ height: `${HOUR_PX}px` }}>
                {String(h).padStart(2, '0')}:00
              </div>
            ))}
          </div>

          {days.map((day, di) => {
            const dayMeetings = meetingsForDay(day)
            const daySlots = slotsForDay(day)
            const isToday = isSameDay(day, today)
            return (
              <div
                key={di}
                className={`cal-day-col${isToday ? ' cal-day-col--today' : ''}`}
                style={{ height: `${(HOUR_END - HOUR_START) * HOUR_PX}px` }}
              >
                {daySlots.map(slot => (
                  <div
                    key={`s-${slot.id}`}
                    className="cal-availability-band"
                    style={slotBandStyle(slot)}
                    title={`Available ${slot.startTime?.slice(0, 5)} – ${slot.endTime?.slice(0, 5)}`}
                  />
                ))}

                {Array.from({ length: HOUR_END - HOUR_START }, (_, i) => HOUR_START + i).map(h => (
                  <button
                    key={`h-${h}`}
                    type="button"
                    className="cal-hour-cell"
                    style={{ top: `${topPxFromHours(h)}px`, height: `${HOUR_PX}px` }}
                    onClick={() => handleEmptyCellClick(day, h)}
                    aria-label={`Create meeting on ${fmtDayLabel(day)} at ${h}:00`}
                  />
                ))}

                {dayMeetings.map(m => (
                  <button
                    key={`m-${m.id}`}
                    type="button"
                    className={meetingClassFor(m.status)}
                    style={meetingBlockStyle(m)}
                    onClick={() => handleMeetingClick(m)}
                    title={`${m.title || 'Meeting'} · ${m.status?.replace(/_/g, ' ').toLowerCase()}`}
                  >
                    <div className="cal-meeting-title">{m.title || 'Meeting'}</div>
                    <div className="cal-meeting-meta">
                      {new Date(m.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      {m.counterpartName ? ` · ${m.counterpartName}` : ''}
                    </div>
                  </button>
                ))}
              </div>
            )
          })}
        </div>
      </div>

      {loading && <div className="md-loading" style={{ marginTop: '12px' }}>Loading calendar…</div>}
    </MainLayout>
  )
}
