import { useEffect, useState } from 'react'
import { getNotificationPreferences, updateNotificationPreferences } from '../services/api'

/**
 * Per-user notification toggles (#289 / req 1.1.5.8). Backed by
 * `GET / PATCH /api/users/me/notification-preferences`. Each toggle flips
 * optimistically and PATCHes the single field; on failure we roll back.
 *
 * The required-by-#289 toggles are taskDeadlineReminders + milestoneReminders.
 * The other 7 categories the backend exposes are surfaced too, since they're
 * the same data flow and there's no value in hiding controls the user has
 * opinions about.
 */
const CATEGORIES = [
  {
    key: 'taskDeadlineRemindersEnabled',
    title: 'Task deadline reminders',
    sub: 'Notify me 24 hours before a task is due.',
    group: 'Reminders',
  },
  {
    key: 'milestoneRemindersEnabled',
    title: 'Milestone reminders',
    sub: 'Notify me 3 days before a milestone is due.',
    group: 'Reminders',
  },
  {
    key: 'matchesEnabled',
    title: 'Matches',
    sub: 'New mentor / mentee match suggestions.',
    group: 'Mentorship',
  },
  {
    key: 'requestsEnabled',
    title: 'Mentorship requests',
    sub: 'Incoming requests, accepts, and rejections.',
    group: 'Mentorship',
  },
  {
    key: 'meetingsEnabled',
    title: 'Meetings',
    sub: 'Confirmations, reschedules, cancellations, and upcoming meeting reminders.',
    group: 'Mentorship',
  },
  {
    key: 'tasksEnabled',
    title: 'Tasks',
    sub: 'New assignments, submissions, and reviews.',
    group: 'Mentorship',
  },
  {
    key: 'messagesEnabled',
    title: 'Messages',
    sub: 'New direct messages from your mentor or mentee.',
    group: 'Messaging',
  },
  {
    key: 'feedEngagementEnabled',
    title: 'Feed engagement',
    sub: 'Likes, comments, and shares on your posts.',
    group: 'Social',
  },
  {
    key: 'newFollowerEnabled',
    title: 'New followers',
    sub: 'Notify me when someone follows me.',
    group: 'Social',
  },
]

export default function NotificationPreferences() {
  const [prefs, setPrefs] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [savingKey, setSavingKey] = useState(null)
  const [savedKey, setSavedKey] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getNotificationPreferences()
      .then(p => { if (!cancelled) setPrefs(p) })
      .catch(err => { if (!cancelled) setError(err?.message || 'Failed to load preferences.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  async function handleToggle(key) {
    if (!prefs || savingKey) return
    const previous = prefs[key]
    const next = !previous
    setPrefs(p => ({ ...p, [key]: next }))
    setSavingKey(key)
    setSavedKey(null)
    try {
      const updated = await updateNotificationPreferences({ [key]: next })
      setPrefs(updated)
      setSavedKey(key)
      setTimeout(() => setSavedKey(k => (k === key ? null : k)), 1500)
    } catch (err) {
      setPrefs(p => ({ ...p, [key]: previous }))
      setError(err?.message || 'Failed to save preference.')
    } finally {
      setSavingKey(null)
    }
  }

  if (loading) {
    return (
      <div className="card" style={{ marginTop: '24px' }}>
        <div className="section-label">Notification Preferences</div>
        <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>Loading…</p>
      </div>
    )
  }
  if (error && !prefs) {
    return (
      <div className="card" style={{ marginTop: '24px' }}>
        <div className="section-label">Notification Preferences</div>
        <p style={{ fontSize: '13px', color: 'var(--red-text)' }}>{error}</p>
      </div>
    )
  }
  if (!prefs) return null

  // Group categories for visual structure without changing the data model.
  const groups = CATEGORIES.reduce((acc, cat) => {
    if (!acc[cat.group]) acc[cat.group] = []
    acc[cat.group].push(cat)
    return acc
  }, {})

  return (
    <div className="card" style={{ marginTop: '24px' }} data-testid="notification-preferences">
      <div className="section-label">Notification Preferences</div>
      <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px', marginBottom: '12px' }}>
        Toggles save automatically. Disabling a category suppresses notifications of that type
        the next time the system would have sent one.
      </p>

      {Object.entries(groups).map(([groupName, items]) => (
        <div key={groupName} style={{ marginTop: '16px' }}>
          <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '4px' }}>
            {groupName}
          </div>
          {items.map(cat => (
            <div
              key={cat.key}
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid var(--border)' }}
              data-testid={`pref-row-${cat.key}`}
            >
              <div style={{ paddingRight: '16px' }}>
                <div style={{ fontSize: '14px', fontWeight: 500 }}>{cat.title}</div>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
                  {cat.sub}
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                {savedKey === cat.key && (
                  <span style={{ fontSize: '11px', color: 'var(--green-dark)' }}>Saved</span>
                )}
                <button
                  type="button"
                  className={`toggle${prefs[cat.key] ? '' : ' off'}`}
                  onClick={() => handleToggle(cat.key)}
                  aria-label={`Toggle ${cat.title}`}
                  aria-pressed={prefs[cat.key]}
                  disabled={savingKey != null}
                  data-testid={`pref-toggle-${cat.key}`}
                />
              </div>
            </div>
          ))}
        </div>
      ))}

      {error && (
        <p style={{ fontSize: '12px', color: 'var(--red-text)', marginTop: '12px' }}>{error}</p>
      )}
    </div>
  )
}
