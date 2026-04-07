import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import {
  getMatchingMentors,
  getReceivedMentorshipRequests,
  acceptMentorshipRequest,
  rejectMentorshipRequest,
  getActiveMentorships,
  getOwnProfile,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

function timeAgo(iso) {
  const diff = Math.floor((Date.now() - new Date(iso)) / 1000)
  if (diff < 60) return 'just now'
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`
  return `${Math.floor(diff / 86400)}d ago`
}

export default function HomePage() {
  const navigate = useNavigate()
  const { role } = useAuth()
  const isMentee = role === 'MENTEE'

  // ── Mentee state ──────────────────────────────────────────────────────────
  const [hasActiveMentor, setHasActiveMentor] = useState(false)
  const [activeMentorship, setActiveMentorship] = useState(null)
  const [menteeLoading, setMenteeLoading] = useState(true)

  // ── Mentor state ──────────────────────────────────────────────────────────
  const [receivedRequests, setReceivedRequests] = useState([])
  const [activeMentorships, setActiveMentorships] = useState([])
  const [mentorStats, setMentorStats] = useState(null)
  const [acceptingId, setAcceptingId] = useState(null)   // request being accepted
  const [selectedDuration, setSelectedDuration] = useState(3)
  const [actionLoading, setActionLoading] = useState(false)
  const [mentorLoading, setMentorLoading] = useState(false)

  // ── Load mentee data ───────────────────────────────────────────────────────
  useEffect(() => {
    if (!isMentee) return
    async function loadMenteeData() {
      setMenteeLoading(true)
      try {
        const [mentorshipsData, matchData] = await Promise.allSettled([
          getActiveMentorships(),
          getMatchingMentors(),
        ])
        if (mentorshipsData.status === 'fulfilled') {
          const active = (mentorshipsData.value || []).find(m => m.status === 'ACTIVE')
          if (active) { setActiveMentorship(active); setHasActiveMentor(true) }
        }
        if (matchData.status === 'rejected') {
          const msg = matchData.reason?.message || ''
          if (msg.includes('403')) setHasActiveMentor(true)
        }
      } catch {
        // silently ignore
      } finally {
        setMenteeLoading(false)
      }
    }
    loadMenteeData()
  }, [isMentee])

  // ── Load mentor data ───────────────────────────────────────────────────────
  useEffect(() => {
    if (isMentee) return
    setMentorLoading(true)
    Promise.allSettled([
      getReceivedMentorshipRequests(),
      getActiveMentorships(),
      getOwnProfile(),
    ]).then(([reqs, mentorships, profile]) => {
      if (reqs.status === 'fulfilled') {
        setReceivedRequests(reqs.value.content || [])
      }
      if (mentorships.status === 'fulfilled') {
        setActiveMentorships(mentorships.value || [])
      }
      if (profile.status === 'fulfilled') {
        setMentorStats(profile.value)
      }
    }).finally(() => setMentorLoading(false))
  }, [isMentee])

  // ── Toast helper ───────────────────────────────────────────────────────────
  const showToast = (message, type = 'success') => {
    const el = document.createElement('div')
    el.className = `toast toast-${type}`
    el.textContent = message
    document.body.appendChild(el)
    setTimeout(() => el.remove(), 3500)
  }

  // ── Mentor request handlers ────────────────────────────────────────────────
  const handleReject = async (id) => {
    setActionLoading(true)
    try {
      await rejectMentorshipRequest(id)
      setReceivedRequests(prev => prev.filter(r => r.id !== id))
      showToast('Request declined.', 'success')
    } catch {
      showToast('Failed to decline request.', 'error')
    } finally {
      setActionLoading(false)
    }
  }

  const handleAcceptConfirm = async () => {
    if (!acceptingId) return
    setActionLoading(true)
    try {
      const newMentorship = await acceptMentorshipRequest(acceptingId, selectedDuration)
      setReceivedRequests(prev => prev.filter(r => r.id !== acceptingId))
      setActiveMentorships(prev => [newMentorship, ...prev])
      setMentorStats(prev => prev ? { ...prev, currentMenteeCount: (prev.currentMenteeCount || 0) + 1 } : prev)
      setAcceptingId(null)
      setSelectedDuration(3)
      showToast('Request accepted! Mentorship started.', 'success')
    } catch (err) {
      const msg = err.message || ''
      if (msg.includes('409') || msg.toLowerCase().includes('capacity')) {
        showToast('Cannot accept: capacity full or request already handled.', 'error')
      } else if (msg.includes('404')) {
        showToast('Request not found.', 'error')
      } else {
        showToast('Failed to accept request.', 'error')
      }
      setAcceptingId(null)
    } finally {
      setActionLoading(false)
    }
  }

  // ── Derived mentor stats ───────────────────────────────────────────────────
  const pendingCount = receivedRequests.filter(r => r.status === 'PENDING').length
  const pendingRequests = receivedRequests.filter(r => r.status === 'PENDING')
  const activeMenteeCount = mentorStats?.currentMenteeCount ?? '-'
  const maxCapacity = mentorStats?.maxMenteeCapacity ?? '-'
  const availableSlots = typeof activeMenteeCount === 'number' && typeof maxCapacity === 'number'
    ? maxCapacity - activeMenteeCount : '-'

  // ──────────────────────────────────────────────────────────────────────────
  return (
    <MainLayout>

      {isMentee ? (
        <>
          <div className="page-header">
            <div>
              <div className="page-title">Home</div>
              <div className="page-sub">
                {hasActiveMentor ? 'Your active mentorship' : 'Find your mentor on the Explore page'}
              </div>
            </div>
          </div>

          {/* ── Loading skeleton ── */}
          {menteeLoading ? (
            <div className="home-loading-skeleton">
              <div className="skeleton-block" style={{ height: '180px', borderRadius: '20px' }} />
            </div>
          ) : activeMentorship && (() => {
            const start = new Date(activeMentorship.startDate)
            const end = new Date(activeMentorship.endDate)
            const now = new Date()
            const totalMs = end - start
            const elapsedMs = Math.min(now - start, totalMs)
            const progress = Math.round((elapsedMs / totalMs) * 100)
            const daysLeft = Math.max(0, Math.ceil((end - now) / 86400000))
            return (
              <div className="active-mentorship-hero">
                <div className="amh-glow" />
                <div className="amh-top">
                  <div className="amh-avatar">{activeMentorship.mentorFirstName?.[0] ?? '?'}</div>
                  <div className="amh-info">
                    <div className="amh-label">Your Mentor</div>
                    <div className="amh-name">{activeMentorship.mentorFirstName}</div>
                    <div className="amh-meta">
                      {activeMentorship.duration} month{activeMentorship.duration !== 1 ? 's' : ''} · started {start.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}
                    </div>
                  </div>
                  <span className="amh-badge">Active</span>
                </div>

                {activeMentorship.sharedGoal && (
                  <div className="amh-goal">
                    <span className="amh-goal-label">Shared Goal</span>
                    <span className="amh-goal-text">"{activeMentorship.sharedGoal}"</span>
                  </div>
                )}

                <div className="amh-progress-section">
                  <div className="amh-progress-labels">
                    <span>Progress</span>
                    <span>{daysLeft} day{daysLeft !== 1 ? 's' : ''} remaining</span>
                  </div>
                  <div className="amh-progress-track">
                    <div className="amh-progress-fill" style={{ width: `${progress}%` }} />
                  </div>
                  <div className="amh-progress-pct">{progress}% complete</div>
                </div>

                <div className="amh-actions">
                  <button className="amh-btn-primary" onClick={() => navigate(`/users/${activeMentorship.mentorId}`)}>
                    View Mentor Profile
                  </button>
                </div>
              </div>
            )
          })()}

          {/* ── No active mentor: prompt to explore ── */}
          {!menteeLoading && !hasActiveMentor && (
            <div className="home-no-mentor">
              <div className="hnm-icon">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                  <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
              </div>
              <div className="hnm-text">
                <div className="hnm-title">Find your perfect mentor</div>
                <div className="hnm-sub">Browse all mentors or use AI matching to find your top 5 picks on the Explore page.</div>
              </div>
              <button className="hnm-btn" onClick={() => navigate('/explore')}>
                Go to Explore
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                  <line x1="5" y1="12" x2="19" y2="12" /><polyline points="12 5 19 12 12 19" />
                </svg>
              </button>
            </div>
          )}
        </>
      ) : (
        <>
          <div className="page-header">
            <div>
              <div className="page-title">Dashboard</div>
              <div className="page-sub">Your mentorship overview</div>
            </div>
          </div>

          {/* Stats row */}
          <div className="mentor-stats-row">
            {[
              { num: activeMenteeCount, label: 'Active Mentees' },
              { num: maxCapacity, label: 'Capacity' },
              { num: availableSlots, label: 'Available Slots' },
              { num: pendingCount, label: 'Pending Requests' },
            ].map(s => (
              <div className="mentor-stat-card" key={s.label}>
                <span className="mentor-stat-num">{s.num}</span>
                <span className="mentor-stat-lbl">{s.label}</span>
              </div>
            ))}
          </div>

          {mentorLoading ? (
            <div className="empty-state">Loading...</div>
          ) : (
            <div className="home-grid">
              {/* Incoming Requests */}
              <div>
                <div className="section-label">Incoming Requests</div>
                {pendingRequests.length === 0 ? (
                  <div className="empty-state" style={{ padding: '24px', fontSize: '14px' }}>
                    No pending requests
                  </div>
                ) : (
                  pendingRequests.map(req => (
                    <div className="request-card" key={req.id}>
                      <div className="req-header">
                        <div className="req-avatar">
                          {req.menteeFirstName?.[0] ?? '?'}
                        </div>
                        <div>
                          {/* Privacy: first name only per req 1.1.2.6 */}
                          <div className="req-name">{req.menteeFirstName}</div>
                          <div className="req-time">{timeAgo(req.createdAt)}</div>
                        </div>
                      </div>
                      {req.message && <div className="req-msg">{req.message}</div>}

                      {/* Duration picker shown when accepting this request */}
                      {acceptingId === req.id ? (
                        <div className="duration-picker">
                          <p className="duration-label">Select mentorship duration:</p>
                          <div className="duration-options">
                            {[1, 3, 6].map(d => (
                              <button
                                key={d}
                                className={`duration-btn${selectedDuration === d ? ' duration-btn--active' : ''}`}
                                onClick={() => setSelectedDuration(d)}
                              >
                                {d} {d === 1 ? 'month' : 'months'}
                              </button>
                            ))}
                          </div>
                          <div className="req-actions">
                            <button
                              className="btn-accept"
                              onClick={handleAcceptConfirm}
                              disabled={actionLoading}
                            >
                              {actionLoading ? 'Confirming…' : 'Confirm'}
                            </button>
                            <button
                              className="btn-decline"
                              onClick={() => setAcceptingId(null)}
                              disabled={actionLoading}
                            >
                              Cancel
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="req-actions">
                          <button
                            className="btn-accept"
                            onClick={() => { setAcceptingId(req.id); setSelectedDuration(3) }}
                            disabled={actionLoading}
                          >
                            Accept
                          </button>
                          <button
                            className="btn-decline"
                            onClick={() => handleReject(req.id)}
                            disabled={actionLoading}
                          >
                            Decline
                          </button>
                          <button
                            className="view-profile-btn"
                            style={{ fontSize: '13px', padding: '7px 14px' }}
                            onClick={() => navigate(`/profile/${req.menteeId}`)}
                          >
                            View Profile
                          </button>
                        </div>
                      )}
                    </div>
                  ))
                )}
              </div>

              {/* Active Mentorships */}
              <div>
                <div className="section-label">Active Mentorships</div>
                {activeMentorships.length === 0 ? (
                  <div className="empty-state" style={{ padding: '24px', fontSize: '14px' }}>
                    No active mentorships
                  </div>
                ) : (
                  activeMentorships.map(m => (
                    <div className="active-mentorship" key={m.id}>
                      <div className="am-header">
                        <div className="am-info">
                          <div className="req-avatar">{m.menteeFirstName?.[0] ?? '?'}</div>
                          <div>
                            <div className="req-name">{m.menteeFirstName}</div>
                            <div className="req-time">
                              {m.duration} month{m.duration !== 1 ? 's' : ''} · started {new Date(m.startDate).toLocaleDateString()}
                            </div>
                          </div>
                        </div>
                        <span className="badge-active">Active</span>
                      </div>
                      {m.sharedGoal && (
                        <div className="req-msg" style={{ marginTop: '8px', fontStyle: 'italic' }}>
                          Goal: {m.sharedGoal}
                        </div>
                      )}
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </>
      )}
    </MainLayout>
  )
}
