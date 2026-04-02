import MainLayout from '../components/MainLayout'
import '../styles/main.css'

const INCOMING_REQUESTS = [
  {
    id: 1,
    initials: 'ÖA',
    name: 'Övgü Su Afşar',
    time: '2 hours ago',
    message: 'I would like mentorship on my React Native project.',
    avatarStyle: {},
  },
  {
    id: 2,
    initials: 'BK',
    name: 'Berkan Kılıç',
    time: '1 day ago',
    message: 'Looking for guidance in machine learning.',
    avatarStyle: { background: '#e8e4f5', color: '#5b4c8a' },
  },
]

const ACTIVE_MENTORSHIPS = [
  {
    id: 1,
    initials: 'ZD',
    name: 'Zeynep Demir',
    subtitle: 'Mentee · Week 3',
    progress: 65,
    avatarStyle: { background: '#f5ead8', color: '#8a6a20' },
  },
]

export default function HomePage() {
  return (
    <MainLayout>
      <div className="page-header">
        <div>
          <div className="page-title">Mentorship Requests</div>
          <div className="page-sub">Review and manage incoming requests</div>
        </div>
        <button className="action-btn">+ Invite Mentor</button>
      </div>

      <div className="home-grid">
        <div>
          <div className="section-label">Incoming Requests</div>
          {INCOMING_REQUESTS.map(req => (
            <div className="request-card" key={req.id}>
              <div className="req-header">
                <div className="req-avatar" style={req.avatarStyle}>{req.initials}</div>
                <div>
                  <div className="req-name">{req.name}</div>
                  <div className="req-time">{req.time}</div>
                </div>
              </div>
              <div className="req-msg">{req.message}</div>
              <div className="req-actions">
                <button className="btn-accept">Accept</button>
                <button className="btn-decline">Decline</button>
              </div>
            </div>
          ))}
        </div>

        <div>
          <div className="section-label">Active Mentorships</div>
          {ACTIVE_MENTORSHIPS.map(m => (
            <div className="active-mentorship" key={m.id}>
              <div className="am-header">
                <div className="am-info">
                  <div className="req-avatar" style={m.avatarStyle}>{m.initials}</div>
                  <div>
                    <div className="req-name">{m.name}</div>
                    <div className="req-time">{m.subtitle}</div>
                  </div>
                </div>
                <span className="badge-active">Active</span>
              </div>
              <div className="progress-bar">
                <div className="progress-fill" style={{ width: `${m.progress}%` }} />
              </div>
              <div className="progress-label">Progress: {m.progress}%</div>
            </div>
          ))}

          <div className="section-label" style={{ marginTop: '24px' }}>Quick Stats</div>
          <div className="stats-row">
            <div className="stat-card">
              <div className="stat-num">12</div>
              <div className="stat-lbl">Tasks</div>
            </div>
            <div className="stat-card">
              <div className="stat-num">3</div>
              <div className="stat-lbl">Meetings</div>
            </div>
            <div className="stat-card">
              <div className="stat-num">4.8</div>
              <div className="stat-lbl">Rating</div>
            </div>
          </div>
        </div>
      </div>
    </MainLayout>
  )
}
