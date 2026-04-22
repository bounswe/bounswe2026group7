import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import { getTasks } from '../services/mentorshipMocks'
import '../styles/main.css'

function formatDue(iso) {
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

export default function MentorshipTasksPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [tasks, setTasks] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getTasks(id).then(data => {
      setTasks(data)
      setLoading(false)
    })
  }, [id])

  function toggle(taskId) {
    setTasks(prev => prev.map(t => (
      t.id === taskId ? { ...t, status: t.status === 'DONE' ? 'TODO' : 'DONE' } : t
    )))
  }

  const doneCount = tasks.filter(t => t.status === 'DONE').length

  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <button
            onClick={() => navigate(`/mentorships/${id}`)}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px', padding: 0, marginBottom: '8px' }}
          >
            ← Back to mentorship
          </button>
          <div className="page-title">Tasks</div>
          <div className="page-sub">{tasks.length > 0 ? `${doneCount} of ${tasks.length} complete` : 'No tasks yet'}</div>
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
            return (
              <label key={t.id} className={`md-task-item${done ? ' md-task-done' : ''}`}>
                <input
                  type="checkbox"
                  checked={done}
                  onChange={() => toggle(t.id)}
                  className="md-task-check"
                />
                <span className="md-task-title">{t.title}</span>
                {t.dueDate && <span className="md-task-due">Due {formatDue(t.dueDate)}</span>}
              </label>
            )
          })}
        </div>
      )}
    </MainLayout>
  )
}
