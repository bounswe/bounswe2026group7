import { useState, useEffect } from 'react'
import MainLayout from '../components/MainLayout'
import RequestMentorshipModal from '../components/RequestMentorshipModal'
import { getMatchingMentors, getSentMentorshipRequests, createMentorshipRequest } from '../services/api'
import { useAuth } from '../context/AuthContext'
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
  const { role } = useAuth()
  const isMentee = role === 'MENTEE'

  const [recommended, setRecommended] = useState([])
  const [requestSentIds, setRequestSentIds] = useState(new Set())
  const [selectedMentor, setSelectedMentor] = useState(null)
  const [modalLoading, setModalLoading] = useState(false)
  const [modalError, setModalError] = useState('')
  const [hasActiveMentor, setHasActiveMentor] = useState(false)

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
        // silently ignore — section just won't render
      }
    }

    loadRecommended()
  }, [isMentee])

  const showToast = (message, type = 'success') => {
    const notification = document.createElement('div')
    notification.className = `toast toast-${type}`
    notification.textContent = message
    document.body.appendChild(notification)
    setTimeout(() => notification.remove(), 3500)
  }

  const openRequestModal = (mentor) => {
    setSelectedMentor(mentor)
    setModalError('')
  }

  const closeRequestModal = () => {
    if (!modalLoading) {
      setSelectedMentor(null)
      setModalError('')
    }
  }

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
                            disabled={alreadySent}
                            onClick={() => openRequestModal(m)}
                          >
                            {alreadySent ? 'Request Sent' : 'Send Request'}
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
        </>
      )}
    </MainLayout>
  )
}
