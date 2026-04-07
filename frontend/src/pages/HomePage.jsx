import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import RequestMentorshipModal from '../components/RequestMentorshipModal'
import {
  getMatchingMentors,
  getSentMentorshipRequests,
  createMentorshipRequest,
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
  const [recommended, setRecommended] = useState([])
  const [requestSentIds, setRequestSentIds] = useState(new Set())
  const [selectedMentor, setSelectedMentor] = useState(null)
  const [modalLoading, setModalLoading] = useState(false)
  const [modalError, setModalError] = useState('')
  const [hasActiveMentor, setHasActiveMentor] = useState(false)

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
    async function loadRecommended() {
      try {
        const [mentorData, sentData] = await Promise.allSettled([
          getMatchingMentors(),
          getSentMentorshipRequests(),
        ])
        if (mentorData.status === 'fulfilled') {
          setRecommended(mentorData.value)
        } else {
          const msg = mentorData.reason?.message || ''
          if (msg.includes('403')) setHasActiveMentor(true)
        }
        if (sentData.status === 'fulfilled') {
          const pending = new Set(
            (sentData.value.content || [])
              .filter(r => r.status === 'PENDING')
              .map(r => r.mentorId)
          )
          setRequestSentIds(pending)
        }
      } catch {
        // silently ignore
      }
    }
    loadRecommended()
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

  // ── Mentee request handlers ────────────────────────────────────────────────
  const openRequestModal = (mentor) => { setSelectedMentor(mentor); setModalError('') }
  const closeRequestModal = () => { if (!modalLoading) { setSelectedMentor(null); setModalError('') } }

  const handleSendRequest = async (message) => {
    if (!selectedMentor || modalLoading) return
    setModalLoading(true)
    setModalError('')
    try {
      await createMentorshipRequest({ mentorId: selectedMentor.id, message })
      setRequestSentIds(prev => new Set(prev).add(selectedMentor.id))
      setSelectedMentor(null)
      showToast('Request sent successfully.', 'success')
    } catch (err) {
      setModalError(err.message || 'Failed to send request. Please try again.')
      showToast('Unable to send request.', 'error')
    } finally {
      setModalLoading(false)
    }
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
      await acceptMentorshipRequest(acceptingId, selectedDuration)
      setReceivedRequests(prev => prev.filter(r => r.id !== acceptingId))
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
      <RequestMentorshipModal
        visible={Boolean(selectedMentor)}
        onClose={closeRequestModal}
        onSubmit={handleSendRequest}
        loading={modalLoading}
        error={modalError}
        mentorName={selectedMentor?.firstName}
      />

      {isMentee ? (
        <>
          <div className="page-header">
            <div>
              <div className="page-title">Home</div>
              <div className="page-sub">Your recommended mentors and activity</div>
            </div>
          </div>

          {!hasActiveMentor && recommended.length > 0 && (
            <div style={{ marginBottom: '32px' }}>
              <div className="section-label">Recommended for You</div>
              <div className="mentors-grid">
                {recommended.map(m => {
                  const tags = (m.interests || []).slice(0, 3)
                  const alreadySent = requestSentIds.has(m.id)
                  const atCapacity = m.maxMenteeCapacity != null && m.currentMenteeCount >= m.maxMenteeCapacity
                  const btnDisabled = alreadySent || atCapacity
                  return (
                    <div className="mentor-card" key={m.id}>
                      <div className="mc-header">
                        <div className="mc-info">
                          <div className="mc-avatar">{m.firstName?.[0] ?? '?'}</div>
                          <div>
                            <div className="mc-name">{m.firstName}</div>
                            <div className="mc-sub">
                              {[m.expertise, m.affiliation].filter(Boolean).join(' · ')}
                            </div>
                          </div>
                        </div>
                        {atCapacity && <span className="badge-full">Full</span>}
                      </div>
                      {tags.length > 0 && (
                        <div className="mc-tags">
                          {tags.map(tag => <span className="tag" key={tag}>{tag}</span>)}
                        </div>
                      )}
                      <div className="mc-footer">
                        <div className="mentor-actions">
                          <button
                            className={`send-request-btn${alreadySent ? ' sent' : ''}`}
                            disabled={btnDisabled}
                            onClick={() => !btnDisabled && openRequestModal(m)}
                          >
                            {alreadySent ? 'Request Sent' : atCapacity ? 'At Capacity' : 'Send Request'}
                          </button>
                          <button
                            className="view-profile-btn"
                            onClick={() => navigate(`/users/${m.id}`)}
                          >
                            View Profile
                          </button>
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
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
