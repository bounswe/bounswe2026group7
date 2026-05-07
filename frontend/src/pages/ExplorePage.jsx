import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import RequestMentorshipModal from '../components/RequestMentorshipModal'
import { getAllMentors, getMatchingMentors, getMatchingMentees, getActiveMentorships, getSentMentorshipRequests, createMentorshipRequest } from '../services/api'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

const FILTERS = ['All', 'Backend', 'Mobile', 'AI/ML', 'DevOps', 'Frontend', 'Data']

// ── Score ring SVG ────────────────────────────────────────────────────────────
function ScoreRing({ score, animate }) {
  const r = 16
  const circ = 2 * Math.PI * r
  const dash = animate ? (score / 100) * circ : 0
  return (
    <svg width="44" height="44" viewBox="0 0 44 44" style={{ flexShrink: 0 }}>
      <circle cx="22" cy="22" r={r} fill="none" stroke="rgba(255,255,255,0.15)" strokeWidth="3" />
      <circle
        cx="22" cy="22" r={r}
        fill="none"
        stroke="url(#scoreGrad)"
        strokeWidth="3"
        strokeLinecap="round"
        strokeDasharray={`${dash} ${circ}`}
        strokeDashoffset={circ / 4}
        style={{ transition: 'stroke-dasharray 1.2s cubic-bezier(0.34,1,0.64,1)' }}
      />
      <defs>
        <linearGradient id="scoreGrad" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#a8f0c6" />
          <stop offset="100%" stopColor="#34d399" />
        </linearGradient>
      </defs>
      <text x="22" y="27" textAnchor="middle" fontSize="10" fontWeight="700" fill="white">
        {animate ? score : '–'}
      </text>
    </svg>
  )
}

// ── Match card (dark, animated) ───────────────────────────────────────────────
function MatchCard({ mentor, rank, visible, alreadySent, hasActiveMentor, onRequest, onViewProfile }) {
  const [scoreAnimate, setScoreAnimate] = useState(false)

  useEffect(() => {
    if (visible) {
      const t = setTimeout(() => setScoreAnimate(true), rank * 120 + 400)
      return () => clearTimeout(t)
    } else {
      setScoreAnimate(false)
    }
  }, [visible, rank])

  const full = mentor.maxMenteeCapacity != null && mentor.currentMenteeCount >= mentor.maxMenteeCapacity
  const locked = hasActiveMentor && !alreadySent
  const btnDisabled = alreadySent || hasActiveMentor || full
  const tags = (mentor.interests || []).slice(0, 3)

  return (
    <div
      className="match-card"
      style={{
        animationDelay: `${rank * 110}ms`,
        animationName: visible ? 'matchCardIn' : 'none',
      }}
    >
      <div className="match-rank">#{rank + 1}</div>

      <div className="match-card-header">
        <div className="match-avatar">{mentor.firstName?.[0] ?? '?'}</div>
        <div className="match-header-info">
          <p className="match-name">{mentor.firstName}</p>
          <p className="match-role">{[mentor.expertise, mentor.affiliation].filter(Boolean).join(' · ')}</p>
        </div>
        <ScoreRing score={mentor.matchScore ?? 0} animate={scoreAnimate} />
      </div>

      {tags.length > 0 && (
        <div className="match-tags">
          {tags.map(t => <span key={t} className="match-tag">{t}</span>)}
        </div>
      )}

      {mentor.bio && <p className="match-bio">{mentor.bio}</p>}

      <div className="match-actions">
        <button
          className={`match-btn-primary${alreadySent ? ' match-btn--sent' : ''}${locked ? ' match-btn--locked' : ''}`}
          disabled={btnDisabled}
          onClick={() => !btnDisabled && onRequest(mentor)}
          title={locked ? 'You already have an active mentor' : undefined}
        >
          {alreadySent ? 'Request Sent' : full ? 'At Capacity' : locked ? 'Already Mentored' : 'Send Request'}
        </button>
        <button className="match-btn-ghost" onClick={() => onViewProfile(mentor.id)}>
          View Profile
        </button>
      </div>
    </div>
  )
}

// ── AI button ─────────────────────────────────────────────────────────────────
function AiMatchButton({ state, matchCount, onClick }) {
  if (state === 'idle') return (
    <button className="ai-btn ai-btn--idle" onClick={onClick}>
      <span className="ai-btn-sparkle">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
        </svg>
      </span>
      Find my best matches
      <span className="ai-btn-badge">AI</span>
    </button>
  )
  if (state === 'loading') return (
    <button className="ai-btn ai-btn--loading" disabled>
      <span className="ai-spinner-sm" />
      Analysing your profile…
    </button>
  )
  return (
    <button className="ai-btn ai-btn--done" onClick={onClick}>
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
        <polyline points="20 6 9 17 4 12" />
      </svg>
      {matchCount} match{matchCount !== 1 ? 'es' : ''} found · Refresh
    </button>
  )
}

// ── Main component ────────────────────────────────────────────────────────────
export default function ExplorePage() {
  const navigate = useNavigate()
  const { role } = useAuth()
  const isMentee = role === 'MENTEE'

  const [mentors, setMentors] = useState([])
  const [mentees, setMentees] = useState([])
  const [matches, setMatches] = useState([])
  const [loading, setLoading] = useState(false)
  const [hasActiveMentor, setHasActiveMentor] = useState(false)
  const [atCapacity, setAtCapacity] = useState(false)
  const [activeFilter, setActiveFilter] = useState('All')
  const [search, setSearch] = useState('')
  const [selectedMentor, setSelectedMentor] = useState(null)
  const [requestSentIds, setRequestSentIds] = useState(new Set())
  const [modalLoading, setModalLoading] = useState(false)
  const [modalError, setModalError] = useState('')
  const [aiState, setAiState] = useState('idle') // idle | loading | done
  const [showMatches, setShowMatches] = useState(false)
  const matchSectionRef = useRef(null)

  useEffect(() => {
    async function init() {
      setLoading(true)
      try {
        if (isMentee) {
          const [mentorData, sentData, mentorshipsData] = await Promise.allSettled([
            getAllMentors(),
            getSentMentorshipRequests(),
            getActiveMentorships(),
          ])
          if (mentorData.status === 'fulfilled') setMentors(mentorData.value)
          if (sentData.status === 'fulfilled' && sentData.value) {
            const pending = new Set(
              (sentData.value.content || [])
                .filter(r => r.status === 'PENDING')
                .map(r => r.mentorId)
            )
            setRequestSentIds(pending)
          }
          if (mentorshipsData.status === 'fulfilled') {
            const active = (mentorshipsData.value || []).find(m => m.status === 'ACTIVE')
            if (active) setHasActiveMentor(true)
          }
        } else {
          try {
            const data = await getMatchingMentees()
            setMentees(Array.isArray(data) ? data : [])
          } catch (err) {
            if ((err?.message || '').includes('403')) setAtCapacity(true)
          }
        }
      } finally {
        setLoading(false)
      }
    }
    init()
  }, [isMentee])

  const handleAiClick = async () => {
    if (aiState === 'done') {
      setShowMatches(false)
      setTimeout(() => setAiState('idle'), 300)
      return
    }
    setAiState('loading')
    try {
      const data = await getMatchingMentors()
      const list = Array.isArray(data) ? data.slice(0, 5) : []
      setMatches(list)
      setAiState('done')
      setShowMatches(true)
      setTimeout(() => matchSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100)
    } catch {
      setAiState('idle')
    }
  }

  const filtered = isMentee
    ? mentors.filter(m => {
        const interests = m.interests || []
        const matchesFilter = activeFilter === 'All' ||
          interests.some(i => i.toLowerCase().includes(activeFilter.toLowerCase())) ||
          (m.expertise || '').toLowerCase().includes(activeFilter.toLowerCase()) ||
          (m.field || '').toLowerCase().includes(activeFilter.toLowerCase())
        const kw = search.toLowerCase()
        const matchesSearch = !kw ||
          (m.firstName || '').toLowerCase().includes(kw) ||
          (m.expertise || '').toLowerCase().includes(kw) ||
          (m.field || '').toLowerCase().includes(kw) ||
          (m.affiliation || '').toLowerCase().includes(kw) ||
          interests.some(i => i.toLowerCase().includes(kw))
        return matchesFilter && matchesSearch
      })
    : mentees.filter(m => {
        const interests = m.interests || []
        const skills = m.skills || []
        const matchesFilter = activeFilter === 'All' ||
          interests.some(i => i.toLowerCase().includes(activeFilter.toLowerCase())) ||
          skills.some(s => s.toLowerCase().includes(activeFilter.toLowerCase())) ||
          (m.major || '').toLowerCase().includes(activeFilter.toLowerCase())
        const kw = search.toLowerCase()
        const matchesSearch = !kw ||
          (m.firstName || '').toLowerCase().includes(kw) ||
          (m.major || '').toLowerCase().includes(kw) ||
          (m.careerInterest || '').toLowerCase().includes(kw) ||
          (m.goals || '').toLowerCase().includes(kw) ||
          interests.some(i => i.toLowerCase().includes(kw)) ||
          skills.some(s => s.toLowerCase().includes(kw))
        return matchesFilter && matchesSearch
      })

  const showToast = (message, type = 'success') => {
    const el = document.createElement('div')
    el.className = `toast toast-${type}`
    el.textContent = message
    document.body.appendChild(el)
    setTimeout(() => el.remove(), 3500)
  }

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

      <div className="page-header" style={{ alignItems: 'flex-start' }}>
        <div>
          <div className="page-title">{isMentee ? 'Find a Mentor' : 'Find Mentees'}</div>
          <div className="page-sub">
            {isMentee ? 'Browse and connect with mentors' : 'Candidate mentees matched to your profile'}
          </div>
        </div>
        {isMentee && !hasActiveMentor && (
          <AiMatchButton state={aiState} matchCount={matches.length} onClick={handleAiClick} />
        )}
      </div>

      {hasActiveMentor && (
        <div className="active-mentor-banner">
          You already have an active mentor. Sending new requests is disabled.
        </div>
      )}
      {atCapacity && (
        <div className="active-mentor-banner">
          You have reached your maximum mentee capacity.
        </div>
      )}

      <div className="explore-header">
        <div className="search-box">
          <span style={{ color: 'var(--text-muted)' }}>🔍</span>
          <input
            type="text"
            placeholder={isMentee ? 'Search topic or mentor...' : 'Search by name, major, goals, skills...'}
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="chips">
        {FILTERS.map(f => (
          <div key={f} className={`chip${activeFilter === f ? ' active' : ''}`} onClick={() => setActiveFilter(f)}>
            {f}
          </div>
        ))}
      </div>

      {/* ── Best matches section ── */}
      {isMentee && showMatches && matches.length > 0 && (
        <div ref={matchSectionRef} className="matches-section">
          <div className="matches-header">
            <div className="matches-header-left">
              <div className="matches-icon">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                </svg>
              </div>
              <div>
                <p className="matches-title">Your top {matches.length} match{matches.length !== 1 ? 'es' : ''}</p>
                <p className="matches-sub">Ranked by profile compatibility</p>
              </div>
            </div>
            <button className="matches-dismiss" onClick={() => { setShowMatches(false); setTimeout(() => setAiState('idle'), 300) }}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
              Dismiss
            </button>
          </div>

          <div className="matches-grid">
            {matches.map((m, i) => (
              <MatchCard
                key={m.id}
                mentor={m}
                rank={i}
                visible={showMatches}
                alreadySent={requestSentIds.has(m.id)}
                hasActiveMentor={hasActiveMentor}
                onRequest={openRequestModal}
                onViewProfile={(id) => navigate(`/users/${id}`)}
              />
            ))}
          </div>

          <div className="matches-divider">
            <span className="matches-divider-line" />
            <span className="matches-divider-label">All mentors</span>
            <span className="matches-divider-line" />
          </div>
        </div>
      )}

      {/* ── Regular grid ── */}
      {loading ? (
        <div className="empty-state">Loading...</div>
      ) : filtered.length === 0 ? (
        <div className="empty-state">
          {isMentee ? 'No mentors found. Try a different search or filter.' : 'No candidate mentees found.'}
        </div>
      ) : isMentee ? (
        <div className="mentors-grid">
          {filtered.map(m => {
            const tags = (m.interests || []).slice(0, 3)
            const alreadySent = requestSentIds.has(m.id)
            const full = m.maxMenteeCapacity != null && m.currentMenteeCount >= m.maxMenteeCapacity
            const btnDisabled = alreadySent || hasActiveMentor || full
            return (
              <div className="mentor-card" key={m.id}>
                <div className="mc-header">
                  <div className="mc-info">
                    <Avatar src={m.profilePhoto} initials={m.firstName?.[0]?.toUpperCase() ?? '?'} size="md" />
                    <div>
                      <div className="mc-name">{m.firstName}</div>
                      <div className="mc-sub">{[m.expertise, m.affiliation].filter(Boolean).join(' · ')}</div>
                    </div>
                  </div>
                  {full && <span className="badge-full">Full</span>}
                </div>
                {tags.length > 0 && (
                  <div className="mc-tags">
                    {tags.map(tag => <span className="tag" key={tag}>{tag}</span>)}
                  </div>
                )}
                {m.bio && <div className="mc-bio">{m.bio}</div>}
                <div className="mc-footer">
                  <div className="mentor-actions">
                    <button
                      className={`send-request-btn${alreadySent ? ' sent' : ''}${hasActiveMentor && !alreadySent ? ' locked' : ''}`}
                      disabled={btnDisabled}
                      onClick={() => !btnDisabled && openRequestModal(m)}
                      title={hasActiveMentor && !alreadySent ? 'You already have an active mentor' : undefined}
                    >
                      {alreadySent ? 'Request Sent' : full ? 'At Capacity' : hasActiveMentor ? 'Already Mentored' : 'Send Request'}
                    </button>
                    <button className="view-profile-btn" onClick={() => navigate(`/users/${m.id}`)}>
                      View Profile
                    </button>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="mentors-grid">
          {filtered.map(m => {
            const tags = (m.interests || []).slice(0, 3)
            const skillTags = (m.skills || []).slice(0, 3)
            return (
              <div className="mentor-card" key={m.id}>
                <div className="mc-header">
                  <div className="mc-info">
                    <Avatar src={m.profilePhoto} initials={m.firstName?.[0]?.toUpperCase() ?? '?'} size="md" />
                    <div>
                      <div className="mc-name">{m.firstName}</div>
                      <div className="mc-sub">{[m.major, m.careerInterest].filter(Boolean).join(' · ')}</div>
                    </div>
                  </div>
                </div>
                {m.goals && <div className="mc-bio" style={{ fontStyle: 'italic' }}>"{m.goals}"</div>}
                {tags.length > 0 && (
                  <div className="mc-tags">
                    {tags.map(tag => <span className="tag" key={tag}>{tag}</span>)}
                    {skillTags.map(s => <span className="tag" key={s} style={{ background: '#e8f0fe', color: '#3b5998' }}>{s}</span>)}
                  </div>
                )}
                <div className="mc-footer">
                  <div className="mentor-actions">
                    <button className="view-profile-btn" onClick={() => navigate(`/users/${m.id}`)}>View Profile</button>
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
