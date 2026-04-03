import MainLayout from '../components/MainLayout'
import '../styles/main.css'

export default function TasksPage() {
  return (
    <MainLayout>
      <div className="page-header">
        <div><div className="page-title">My Tasks</div></div>
        <button className="action-btn">+ Add</button>
      </div>

      <div className="tasks-layout">
        <div>
          <div className="section-label">Pending</div>
          <div className="task-item">
            <div className="task-check" />
            <div>
              <div className="task-name">Mentor List with FlatList</div>
              <span className="task-due due-today">Today</span>
            </div>
          </div>
          <div className="task-item">
            <div className="task-check" />
            <div>
              <div className="task-name">AsyncStorage Token Management</div>
              <span className="task-due due-tomorrow">Tomorrow</span>
            </div>
          </div>

          <div className="section-label" style={{ marginTop: '24px' }}>Awaiting Feedback</div>
          <div className="task-review">
            <div className="task-name">Registration Screen Implementation</div>
            <div className="review-info">Form validation added, awaiting mentor review</div>
            <div className="review-footer">
              <div className="review-mentor">
                <div className="review-avatar-sm">BA</div>
                Burak Afşar
              </div>
              <span className="badge-review">In Review</span>
            </div>
          </div>

          <div className="section-label" style={{ marginTop: '24px' }}>Completed</div>
          <div className="task-item" style={{ opacity: 0.6 }}>
            <div className="task-check done">✓</div>
            <div><div className="task-name done">Expo Project Setup</div></div>
          </div>
          <div className="task-item" style={{ opacity: 0.6 }}>
            <div className="task-check done">✓</div>
            <div><div className="task-name done">React Navigation Integration</div></div>
          </div>
        </div>

        <div>
          <div className="card">
            <div className="section-label">Summary</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
              {[
                { label: 'Total Tasks', value: '12', color: '' },
                { label: 'Completed', value: '8', color: 'var(--green-mid)' },
                { label: 'In Progress', value: '2', color: '#b45309' },
                { label: 'In Review', value: '1', color: '#2563eb' },
              ].map((item, i, arr) => (
                <div
                  key={item.label}
                  style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    padding: '12px 0',
                    borderBottom: i < arr.length - 1 ? '1px solid var(--border)' : 'none',
                  }}
                >
                  <span style={{ color: 'var(--text-mid)', fontSize: '14px' }}>{item.label}</span>
                  <span style={{ fontWeight: 700, color: item.color || 'inherit' }}>{item.value}</span>
                </div>
              ))}
            </div>
            <div className="divider" />
            <div className="section-label">Completion</div>
            <div className="progress-bar" style={{ height: '10px', marginBottom: '8px' }}>
              <div className="progress-fill" style={{ width: '67%' }} />
            </div>
            <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>67% complete</div>
          </div>
        </div>
      </div>
    </MainLayout>
  )
}
