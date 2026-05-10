import { useEffect, useMemo, useState } from 'react'
import MainLayout from '../components/MainLayout'
import AvailabilityGrid, { slotsToCellSet, cellSetToSlots } from '../components/AvailabilityGrid'
import { useAuth } from '../context/AuthContext'
import { getMentorAvailability, saveMentorAvailability } from '../services/api'
import '../styles/main.css'

const BROWSER_TZ = (() => {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC' }
  catch { return 'UTC' }
})()

export default function AvailabilityPage() {
  const { role, userId } = useAuth()
  const isMentor = role === 'MENTOR'

  const [selected, setSelected] = useState(() => new Set())
  const [originalKey, setOriginalKey] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [status, setStatus] = useState(null) // null | 'success' | string error

  // ── Load existing slots and seed the grid ─────────────────────────────
  useEffect(() => {
    if (!userId || !isMentor) {
      setLoading(false)
      return undefined
    }
    let cancelled = false
    setLoading(true)
    getMentorAvailability(userId)
      .then(data => {
        if (cancelled) return
        const slots = Array.isArray(data) ? data : data?.slots ?? []
        const cellSet = slotsToCellSet(slots)
        setSelected(cellSet)
        setOriginalKey(setToKey(cellSet))
      })
      .catch(() => { /* keep empty grid on error */ })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [userId, isMentor])

  const dirty = useMemo(() => setToKey(selected) !== originalKey, [selected, originalKey])

  // ── Save handler ──────────────────────────────────────────────────────
  async function handleSave() {
    setStatus(null)
    const slots = cellSetToSlots(selected)
    if (slots.length === 0) {
      setStatus('Please select at least one available hour before saving.')
      return
    }
    if (slots.length > 50) {
      setStatus('Too many separate ranges (max 50). Try painting larger contiguous blocks.')
      return
    }
    setSaving(true)
    try {
      await saveMentorAvailability({ slots })
      setOriginalKey(setToKey(selected))
      setStatus('success')
    } catch (err) {
      const msg = err?.message || 'Failed to save availability.'
      if (msg.includes('409') || msg.toLowerCase().includes('overlap')) {
        setStatus('Overlapping slots or invalid time range.')
      } else {
        setStatus(msg)
      }
    } finally {
      setSaving(false)
    }
  }

  // ── Mentee gate (1.1.4.1 mentor-only feature) ─────────────────────────
  if (!isMentor) {
    return (
      <MainLayout>
        <div className="page-header">
          <div>
            <div className="page-title">Availability</div>
          </div>
        </div>
        <div className="md-error-card">
          <div className="md-error-title">Mentors only</div>
          <div className="md-error-sub">Setting weekly availability is a mentor capability.</div>
        </div>
      </MainLayout>
    )
  }

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <div className="page-title">Edit Availability</div>
          <div className="page-sub">Timezone: {BROWSER_TZ}</div>
        </div>
        <button
          className="action-btn"
          onClick={handleSave}
          disabled={saving || loading || !dirty}
          title={!dirty ? 'No unsaved changes' : 'Save availability'}
        >
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

      {loading ? (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
          Loading availability…
        </div>
      ) : (
        <div className="card">
          <div className="section-label" style={{ marginBottom: '6px' }}>Weekly schedule</div>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '16px' }}>
            Click an hour cell to toggle availability. Adjacent cells on the same day are merged into one time range when saved.
          </p>
          <AvailabilityGrid
            selected={selected}
            onChange={(next) => { setSelected(next); setStatus(null) }}
            disabled={saving}
          />
        </div>
      )}
    </MainLayout>
  )
}

// Stable string identity for a Set, used to detect dirty state
function setToKey(set) {
  return Array.from(set).sort().join('|')
}
