import MainLayout from '../components/MainLayout'
import '../styles/main.css'

const SCHEDULE = [
  { day: 'Mon', hour: '10:00', title: 'Code Review', mentor: 'Burak Afşar', badge: 'badge-confirmed', label: 'Confirmed' },
  { day: 'Wed', hour: '14:00', title: 'Architecture Discussion', mentor: 'Burak Afşar', badge: 'badge-scheduled', label: 'Scheduled' },
  { day: 'Fri', hour: '16:00', title: 'Weekly Summary', mentor: 'Burak Afşar', badge: 'badge-pending', label: 'Pending' },
]

export default function SchedulePage() {
  return (
    <MainLayout>
      <div className="page-header">
        <div><div className="page-title">Meeting Schedule</div></div>
        <button className="action-btn">+ Schedule</button>
      </div>

      <div className="upcoming-card">
        <div className="uc-label">Upcoming Meeting</div>
        <div className="uc-title">Sprint Review</div>
        <div className="uc-date">March 29, 2026 · 15:00</div>
        <div className="uc-btns">
          <button className="uc-btn-ghost">Reschedule</button>
          <button className="uc-btn-white">Join</button>
        </div>
      </div>

      <div className="section-label">This Week</div>
      {SCHEDULE.map((item, i) => (
        <div className="sched-item" key={i}>
          <div className="sched-time">
            <div className="sched-day">{item.day}</div>
            <div className="sched-hour">{item.hour}</div>
          </div>
          <div style={{ flex: 1 }}>
            <div className="sched-title">{item.title}</div>
            <div className="sched-mentor">{item.mentor}</div>
          </div>
          <span className={item.badge}>{item.label}</span>
        </div>
      ))}
    </MainLayout>
  )
}
