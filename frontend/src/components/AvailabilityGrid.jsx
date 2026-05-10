import { useCallback, useEffect, useRef } from 'react'

/**
 * 7-day × hour grid for editing weekly availability.
 *
 * Cell coordinates are stored as `${DAY}-${hour}` strings in a Set so identity
 * comparisons stay cheap during drag operations. Hours are integers 0–23.
 *
 * Props:
 *   selected: Set<string>           — set of "DAY-HOUR" cell keys that are ON
 *   onChange(nextSelected: Set)     — called whenever the painted set changes
 *   startHour: number               — first hour shown (inclusive). Default 6
 *   endHour: number                 — last hour shown (exclusive). Default 22
 *   disabled: boolean               — visual + interaction lockout (e.g. while saving)
 *
 * Drag-paint:
 *   - mouse-down on a cell records the OPPOSITE state of that cell as the
 *     "paint mode" and applies it to the cell.
 *   - mouse-enter on subsequent cells (while held) applies the same paint
 *     mode — so dragging always paints in one direction (turns cells ON if
 *     started on an off cell, OFF if started on an on cell). This matches
 *     the de-facto pattern of when2meet, doodle, etc.
 *   - mouse-up anywhere ends the drag.
 */

export const DAYS = [
  { key: 'MONDAY', label: 'Mon' },
  { key: 'TUESDAY', label: 'Tue' },
  { key: 'WEDNESDAY', label: 'Wed' },
  { key: 'THURSDAY', label: 'Thu' },
  { key: 'FRIDAY', label: 'Fri' },
  { key: 'SATURDAY', label: 'Sat' },
  { key: 'SUNDAY', label: 'Sun' },
]

export function cellKey(day, hour) {
  return `${day}-${hour}`
}

function formatHour(h) {
  return `${String(h).padStart(2, '0')}:00`
}

export default function AvailabilityGrid({
  selected,
  onChange,
  startHour = 6,
  endHour = 22,
  disabled = false,
}) {
  const hours = []
  for (let h = startHour; h < endHour; h += 1) hours.push(h)

  // Stable refs so the global mouseup listener and onMouseEnter handlers
  // see the latest selected/onChange without re-binding listeners every render.
  const selectedRef = useRef(selected)
  const onChangeRef = useRef(onChange)
  useEffect(() => { selectedRef.current = selected }, [selected])
  useEffect(() => { onChangeRef.current = onChange }, [onChange])

  // null when not painting; 'on' / 'off' while a drag is in progress.
  const paintModeRef = useRef(null)
  // Tracks which cells we have already painted in the current drag so we
  // don't fire onChange repeatedly for the same cell as the cursor jitters.
  const paintedThisDragRef = useRef(new Set())

  function applyPaint(day, hour) {
    const mode = paintModeRef.current
    if (!mode) return
    const key = cellKey(day, hour)
    if (paintedThisDragRef.current.has(key)) return
    paintedThisDragRef.current.add(key)
    const next = new Set(selectedRef.current)
    if (mode === 'on') next.add(key)
    else next.delete(key)
    onChangeRef.current(next)
  }

  const handleCellMouseDown = useCallback((day, hour) => {
    if (disabled) return
    const key = cellKey(day, hour)
    const isOn = selectedRef.current.has(key)
    paintModeRef.current = isOn ? 'off' : 'on'
    paintedThisDragRef.current = new Set()
    applyPaint(day, hour)
  }, [disabled])

  const handleCellMouseEnter = useCallback((day, hour) => {
    if (disabled) return
    if (!paintModeRef.current) return
    applyPaint(day, hour)
  }, [disabled])

  // Global mouse-up to end the drag — listening on window so a release
  // outside the grid still terminates the paint.
  useEffect(() => {
    function endDrag() {
      paintModeRef.current = null
      paintedThisDragRef.current = new Set()
    }
    window.addEventListener('mouseup', endDrag)
    return () => window.removeEventListener('mouseup', endDrag)
  }, [])

  return (
    <div className={`avail-grid${disabled ? ' avail-grid--disabled' : ''}`}>
      <div className="avail-grid-corner" aria-hidden="true" />
      {DAYS.map(d => (
        <div key={`h-${d.key}`} className="avail-grid-day-header">
          {d.label}
        </div>
      ))}

      {hours.map(h => (
        <FragmentRow
          key={`row-${h}`}
          hour={h}
          selected={selected}
          disabled={disabled}
          onMouseDown={handleCellMouseDown}
          onMouseEnter={handleCellMouseEnter}
        />
      ))}
    </div>
  )
}

function FragmentRow({ hour, selected, disabled, onMouseDown, onMouseEnter }) {
  return (
    <>
      <div className="avail-grid-hour-label">{formatHour(hour)}</div>
      {DAYS.map(d => {
        const k = cellKey(d.key, hour)
        const on = selected.has(k)
        return (
          <button
            key={k}
            type="button"
            className={`avail-grid-cell${on ? ' avail-grid-cell--on' : ''}`}
            disabled={disabled}
            onMouseDown={(e) => { e.preventDefault(); onMouseDown(d.key, hour) }}
            onMouseEnter={() => onMouseEnter(d.key, hour)}
            aria-pressed={on}
            aria-label={`${d.key} ${formatHour(hour)} ${on ? 'available' : 'unavailable'}`}
          />
        )
      })}
    </>
  )
}

// ── Helpers ──────────────────────────────────────────────────────────────

/** Convert a server slot list (one row per range per day) into a Set of cell keys. */
export function slotsToCellSet(slots) {
  const set = new Set()
  for (const s of slots || []) {
    const day = typeof s.dayOfWeek === 'string' ? s.dayOfWeek : s.dayOfWeek
    const startH = parseHour(s.startTime)
    const endH = parseHour(s.endTime)
    if (day == null || startH == null || endH == null) continue
    for (let h = startH; h < endH; h += 1) set.add(cellKey(day, h))
  }
  return set
}

/** Convert a Set of cell keys back to per-day contiguous-range slot rows. */
export function cellSetToSlots(set) {
  const out = []
  for (const day of DAYS.map(d => d.key)) {
    const hours = []
    for (let h = 0; h < 24; h += 1) {
      if (set.has(cellKey(day, h))) hours.push(h)
    }
    if (hours.length === 0) continue
    // Pack contiguous hours into [start, end) ranges
    let runStart = hours[0]
    let prev = hours[0]
    for (let i = 1; i < hours.length; i += 1) {
      const cur = hours[i]
      if (cur === prev + 1) {
        prev = cur
        continue
      }
      out.push({ dayOfWeek: day, startTime: hourLT(runStart), endTime: hourLT(prev + 1), recurring: true })
      runStart = cur
      prev = cur
    }
    out.push({ dayOfWeek: day, startTime: hourLT(runStart), endTime: hourLT(prev + 1), recurring: true })
  }
  return out
}

function hourLT(h) {
  return `${String(h).padStart(2, '0')}:00`
}

function parseHour(t) {
  if (t == null) return null
  if (typeof t === 'string') {
    const [hh] = t.split(':')
    const n = parseInt(hh, 10)
    return Number.isFinite(n) ? n : null
  }
  if (typeof t === 'object' && Number.isFinite(t.hour)) return t.hour
  return null
}

