import { useState, useEffect, useRef } from 'react'
import MainLayout from '../components/MainLayout'
import RequestMentorshipModal from '../components/RequestMentorshipModal'
import { getMatchingMentors, getSentMentorshipRequests, createMentorshipRequest } from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

const FILTERS = ['All', 'Backend', 'Mobile', 'AI/ML', 'DevOps', 'Frontend', 'Data']

export default function ExplorePage() {
  const { role } = useAuth()
  const isMentee = role === 'MENTEE'

  const [mentors, setMentors] = useState([])
  const [loading, setLoading] = useState(false)
  const [hasActiveMentor, setHasActiveMentor] = useState(false)
  const [activeFilter, setActiveFilter] = useState('All')
  const [search, setSearch] = useState('')
  const [selectedMentor, setSelectedMentor] = useState(null)
  const [requestSentIds, setRequestSentIds] = useState(new Set())
  const [modalLoading, setModalLoading] = useState(false)
  const [modalError, setModalError] = useState('')

  const debounceRef = useRef(null)

  useEffect(() => {
    if (!isMentee) return

    async function init() {
      setLoading(true)
      try {
        const [mentorData, sentData] = await Promise.allSettled([
          getMatchingMentors(),
          getSentMentorshipRequests(),
        ])

        if (mentorData.status === 'fulfilled') {
          setMentors(mentorData.value)
        } else {
          const msg = mentorData.reason?.message || ''
          if (msg.includes('403') || mentorData.reason?.status === 403) {
            setHasActiveMentor(true)
          }
        }

        if (sentData.status === 'fulfilled') {
          const pending = new Set(
            (sentData.value.content || [])
              .filter(r => r.status === 'PENDING')
              .map(r => r.mentorId)
          )
          setRequestSentIds(pending)
        }
      } finally {
        setLoading(false)
      }
    }

    init()
  }, [isMentee])

  useEffect(() => {
    if (!isMentee) return
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const data = await getMatchingMentors(search || undefined)
        setMentors(data)
        setHasActiveMentor(false)
      } catch (err) {
        const msg = err?.message || ''
        if (msg.includes('403')) {
          setHasActiveMentor(true)
          setMentors([])
        }
      } finally {
        setLoading(false)
      }
    }, 400)
    return () => clearTimeout(debounceRef.current)
  }, [search, isMentee])

  const filtered = mentors.filter(m => {
    if (activeFilter === 'All') return true
    const interests = m.interests || []
    return (
      interests.some(i => i.toLowerCase().includes(activeFilter.toLowerCase())) ||
      (m.expertise || '').toLowerCase().includes(activeFilter.toLowerCase()) ||
      (m.field || '').toLowerCase().includes(activeFilter.toLowerCase())
    )
  })

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

      <div className="page-header">
        <div><div className="page-title">Find a Mentor</div></div>
      </div>

      {hasActiveMentor && (
        <div className="active-mentor-banner">
          You already have an active mentor. Sending new requests is disabled.
        </div>
      )}

      <div className="explore-header">
        <div className="search-box">
          <span style={{ color: 'var(--text-muted)' }}>🔍</span>
          <input
            type="text"
            placeholder="Search topic or mentor..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="chips">
        {FILTERS.map(f => (
          <div
            key={f}
            className={`chip${activeFilter === f ? ' active' : ''}`}
            onClick={() => setActiveFilter(f)}
          >
            {f}
          </div>
        ))}
      </div>

      {!isMentee ? (
        <div className="empty-state">This page is for mentees looking for a mentor.</div>
      ) : loading ? (
        <div className="empty-state">Loading mentors...</div>
      ) : filtered.length === 0 ? (
        <div className="empty-state">No mentors found. Try a different search or filter.</div>
      ) : (
        <div className="mentors-grid">
          {filtered.map(m => {
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
                {m.bio && <div className="mc-bio">{m.bio}</div>}
                <div className="mc-footer">
                  <div className="mentor-actions">
                    {isMentee && (
                      <button
                        className={`send-request-btn${alreadySent ? ' sent' : ''}`}
                        disabled={alreadySent || hasActiveMentor}
                        onClick={() => openRequestModal(m)}
                      >
                        {alreadySent ? 'Request Sent' : 'Send Request'}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </MainLayout>
  )
}
