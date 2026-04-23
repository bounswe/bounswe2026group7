import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import { getActiveMentorships } from '../services/api'
import { getTasks, getTasksAcrossMentorships } from '../services/mentorshipMocks'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

function formatDue(iso) {
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

function labelFor(mentorship, role) {
  if (!mentorship) return ''
  return role === 'MENTOR' ? mentorship.menteeFirstName : mentorship.mentorFirstName
}

export default function TasksPage() {
  const [params] = useSearchParams()
  const scopedId = params.get('mentorshipId')
  const navigate = useNavigate()
  const { role } = useAuth()

  const [tasks, setTasks] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)

    if (scopedId) {
      // Scoped to one mentorship: also fetch the mentorship so we can label the header
      Promise.all([getTasks(scopedId), getActiveMentorships().catch(() => [])])
        .then(([t, list]) => {
          if (cancelled) return
          const m = (list || []).find(x => String(x.id) === String(scopedId))
          setTasks(t.map(task => ({ ...task, mentorship: m ? { id: m.id, mentorFirstName: m.mentorFirstName, menteeFirstName: m.menteeFirstName } : null })))
          setLoading(false)
        })
        .catch(() => setLoading(false))
    } else {
      getActiveMentorships()
        .then(list => getTasksAcrossMentorships(list || []))
        .then(t => { if (!cancelled) { setTasks(t); setLoading(false) } })
        .catch(() => setLoading(false))
    }

    return () => { cancelled = true }
  }, [scopedId])

  const doneCount = useMemo(() => tasks.filter(t => t.status === 'DONE').length, [tasks])

  function toggle(taskId) {
    setTasks(prev => prev.map(t => (
      t.id === taskId ? { ...t, status: t.status === 'DONE' ? 'TODO' : 'DONE' } : t
    )))
  }

  const headerSub = scopedId
    ? `Tasks for your mentorship with ${labelFor(tasks[0]?.mentorship, role) || '…'}`
    : 'Tasks across all your active mentorships'

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          {scopedId && (
            <button
              onClick={() => navigate(`/mentorships/${scopedId}`)}
              style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px', padding: 0, marginBottom: '8px' }}
            >
              ← Back to mentorship
            </button>
          )}
          <div className="page-title">My Tasks</div>
          <div className="page-sub">
            {loading ? '…' : (tasks.length > 0 ? `${doneCount} of ${tasks.length} complete · ${headerSub}` : headerSub)}
          </div>
        </div>
      </div>

      {loading ? (
        <div className="md-loading">Loading tasks…</div>
      ) : tasks.length === 0 ? (
        <div className="empty-state">No tasks yet.</div>
      ) : (
        <div className="md-task-list">
          {tasks.map(t => {
            const done = t.status === 'DONE'
            const mentLabel = !scopedId ? labelFor(t.mentorship, role) : null
            return (
              <label key={t.id} className={`md-task-item${done ? ' md-task-done' : ''}`}>
                <input
                  type="checkbox"
                  checked={done}
                  onChange={() => toggle(t.id)}
                  className="md-task-check"
                />
                <span className="md-task-title">
                  {t.title}
                  {mentLabel && (
                    <span className="md-task-ment"> · with {mentLabel}</span>
                  )}
                </span>
                {t.dueDate && <span className="md-task-due">Due {formatDue(t.dueDate)}</span>}
              </label>
            )
          })}
        </div>
      )}
    </MainLayout>
  )
}
