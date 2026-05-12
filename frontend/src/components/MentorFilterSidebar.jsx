/**
 * Advanced mentor filter sidebar (#139). Three filter dimensions backed
 * by the matching API's #571 params:
 *  - availabilityDays: multi-select of weekdays
 *  - mentorshipDuration: multi-select of 1/3/6 month options
 *  - minMatchScore: slider, 0-100
 *
 * Controlled component. Parent owns the state; this just renders the
 * controls and emits onChange on every interaction. Reset clears the
 * three filters back to their initial empty state.
 */

const DAYS = [
  { value: 'MONDAY', label: 'Mon' },
  { value: 'TUESDAY', label: 'Tue' },
  { value: 'WEDNESDAY', label: 'Wed' },
  { value: 'THURSDAY', label: 'Thu' },
  { value: 'FRIDAY', label: 'Fri' },
  { value: 'SATURDAY', label: 'Sat' },
  { value: 'SUNDAY', label: 'Sun' },
]

const DURATIONS = [1, 3, 6]

export default function MentorFilterSidebar({
  availabilityDays = [], mentorshipDuration = [], minMatchScore = 0,
  onChange,
  onReset,
}) {
  function toggleDay(day) {
    const next = availabilityDays.includes(day)
      ? availabilityDays.filter(d => d !== day)
      : [...availabilityDays, day]
    onChange?.({ availabilityDays: next })
  }

  function toggleDuration(n) {
    const next = mentorshipDuration.includes(n)
      ? mentorshipDuration.filter(d => d !== n)
      : [...mentorshipDuration, n]
    onChange?.({ mentorshipDuration: next })
  }

  function setMinScore(v) {
    onChange?.({ minMatchScore: Number(v) })
  }

  const hasAny = availabilityDays.length > 0 || mentorshipDuration.length > 0 || minMatchScore > 0

  return (
    <aside className="mentor-filter-sidebar" aria-label="Advanced mentor filters">
      <div className="mentor-filter-head">
        <span className="section-label" style={{ margin: 0 }}>Filters</span>
        {hasAny && (
          <button type="button" className="mentor-filter-reset" onClick={onReset}>
            Reset
          </button>
        )}
      </div>

      <div className="mentor-filter-block">
        <div className="mentor-filter-label">Availability days</div>
        <div className="mentor-filter-days">
          {DAYS.map(d => {
            const selected = availabilityDays.includes(d.value)
            return (
              <button
                key={d.value}
                type="button"
                className={`mentor-filter-day${selected ? ' mentor-filter-day--active' : ''}`}
                aria-pressed={selected}
                onClick={() => toggleDay(d.value)}
              >
                {d.label}
              </button>
            )
          })}
        </div>
      </div>

      <div className="mentor-filter-block">
        <div className="mentor-filter-label">Mentorship duration</div>
        <div className="mentor-filter-durations">
          {DURATIONS.map(n => {
            const selected = mentorshipDuration.includes(n)
            return (
              <button
                key={n}
                type="button"
                className={`mentor-filter-duration${selected ? ' mentor-filter-duration--active' : ''}`}
                aria-pressed={selected}
                onClick={() => toggleDuration(n)}
              >
                {n} month{n !== 1 ? 's' : ''}
              </button>
            )
          })}
        </div>
      </div>

      <div className="mentor-filter-block">
        <div className="mentor-filter-label">
          Minimum match score
          <span className="mentor-filter-score-value">{minMatchScore || 0}+</span>
        </div>
        <input
          type="range"
          min="0"
          max="100"
          step="5"
          value={minMatchScore}
          onChange={(e) => setMinScore(e.target.value)}
          className="mentor-filter-slider"
          aria-label="Minimum match score"
        />
      </div>
    </aside>
  )
}
