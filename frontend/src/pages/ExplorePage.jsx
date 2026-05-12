import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import MainLayout from '../components/MainLayout'
import Avatar from '../components/Avatar'
import RequestMentorshipModal from '../components/RequestMentorshipModal'
import { getAllMentors, getMatchingMentors, getMatchingMentees, getActiveMentorships, getSentMentorshipRequests, createMentorshipRequest } from '../services/api'
import MentorFilterSidebar from '../components/MentorFilterSidebar'
import MentorCompareModal from '../components/MentorCompareModal'
import { showTransientToast } from '../utils/toast'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

const FILTERS = ['All', 'Backend', 'Mobile', 'AI/ML', 'DevOps', 'Frontend', 'Data']

// ── Factor formatting ─────────────────────────────────────────────────────────
// Backend emits machine-readable factor strings (see MentorRanker spec 1.1.2.5).
// We turn them into short user-facing chips. Codes we don't recognize, or
// operational signals (semantic-unavailable, location-unset) return null so
// they don't render. `diverse-pick` is rendered separately as a pill, not a chip.
function formatFactor(code) {
  if (!code || typeof code !== 'string') return null
  const colon = code.indexOf(':')
  const head = colon === -1 ? code : code.slice(0, colon)
  const value = colon === -1 ? '' : code.slice(colon + 1)
  switch (head) {
    case 'interest-match':
    case 'shared-interest':      return value ? { label: value, kind: 'interest' } : null
    case 'skill-match':
    case 'shared-skill':         return value ? { label: value, kind: 'skill' } : null
    case 'major-exact':
    case 'major-exact-match':    return { label: 'Same major', kind: 'major' }
    case 'major-field':
    case 'major-field-overlap':  return { label: 'Major fits field', kind: 'major' }
    case 'availability':         return value ? { label: `${value} overlap`, kind: 'time' } : null
    case 'nearby':               return value ? { label: `${value} away`, kind: 'location' } : null
    case 'city-match':           return { label: 'Same city', kind: 'location' }
    case 'semantic-match':       return { label: 'Strong content match', kind: 'semantic' }
    default:                     return null
  }
}

const FACTOR_STYLES = {
  interest: { background: 'rgba(168,240,198,0.18)', color: '#a8f0c6' },
  skill:    { background: 'rgba(99,179,237,0.18)',  color: '#b8d8ff' },
  major:    { background: 'rgba(245,158,11,0.18)',  color: '#fcd34d' },
  time:     { background: 'rgba(192,132,252,0.18)', color: '#e9d5ff' },
  location: { background: 'rgba(96,165,250,0.18)',  color: '#bfdbfe' },
  semantic: { background: 'rgba(244,114,182,0.18)', color: '#fbcfe8' },
}

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
  const rawFactors = Array.isArray(mentor.factors) ? mentor.factors : []
  const isDiversePick = rawFactors.includes('diverse-pick')
  const factorChips = rawFactors.map(formatFactor).filter(Boolean)

  return (
    <div
      className="match-card"
      style={{
        animationDelay: `${rank * 110}ms`,
        animationName: visible ? 'matchCardIn' : 'none',
      }}
    >
      <div className="match-rank">#{rank + 1}</div>
      {isDiversePick && (
        <span
          className="match-diverse-pick"
          title="Surfaced outside your primary goal for diversity"
          style={{
            position: 'absolute', top: 12, right: 12,
            background: 'linear-gradient(135deg,#f59e0b,#ec4899)',
            color: '#fff', fontSize: 10, fontWeight: 700,
            padding: '3px 8px', borderRadius: 999, letterSpacing: 0.3,
          }}
          data-testid={`explore-diverse-pill-${mentor.id}`}
        >
          Diverse pick
        </span>
      )}

      <div className="match-card-header">
        <div className="match-avatar">{mentor.firstName?.[0] ?? '?'}</div>
        <div className="match-header-info">
          <p className="match-name" data-testid={`explore-mentor-name-${mentor.id}`}>{mentor.firstName}</p>
          <p className="match-role" data-testid={`explore-mentor-city-${mentor.id}`}>{[mentor.expertise, mentor.affiliation].filter(Boolean).join(' · ')}</p>
        </div>
        <ScoreRing score={mentor.matchScore ?? 0} animate={scoreAnimate} />
      </div>

      {tags.length > 0 && (
        <div className="match-tags">
          {tags.map(t => <span key={t} className="match-tag">{t}</span>)}
        </div>
      )}

      {mentor.bio && <p className="match-bio">{mentor.bio}</p>}

      {factorChips.length > 0 && (
        <div className="match-factors" style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
          {factorChips.map((f, i) => (
            <span
              key={`${f.kind}-${f.label}-${i}`}
              className={`match-factor match-factor--${f.kind}`}
              style={{
                fontSize: 11, fontWeight: 600, padding: '3px 8px',
                borderRadius: 999, ...FACTOR_STYLES[f.kind],
              }}
              data-testid={`explore-factor-${mentor.id}-${i}`}
            >
              {f.label}
            </span>
          ))}
        </div>
      )}

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

  // #139: advanced filters. State lives here; controls in MentorFilterSidebar.
  // Filters are passed straight through to the matching API which has been
  // accepting them since backend #571. The selection is also applied client-
  // side when the AI Match panel isn't open (i.e., to the regular mentor
  // grid which comes from getAllMentors — that endpoint doesn't take the
  // same filter params).
  const [filters, setFilters] = useState({
    availabilityDays: [],
    mentorshipDuration: [],
    minMatchScore: 0,
  })
  // #139: compare mode. Selection is a Set of mentor ids, capped at 3.
  const [compareIds, setCompareIds] = useState(() => new Set())
  const [compareOpen, setCompareOpen] = useState(false)

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
      // #139: forward the advanced filters to the matching endpoint. None of
      // them are required — empty arrays / zero score are sent as nothing,
      // so this is backward compatible with the no-filter case.
      const data = await getMatchingMentors(null, {
        availabilityDays: filters.availabilityDays,
        mentorshipDuration: filters.mentorshipDuration,
        minMatchScore: filters.minMatchScore > 0 ? filters.minMatchScore : undefined,
      })
      const list = Array.isArray(data) ? data.slice(0, 5) : []
      setMatches(list)
      setAiState('done')
      setShowMatches(true)
      setTimeout(() => matchSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100)
    } catch {
      setAiState('idle')
    }
  }

  function updateFilters(patch) {
    setFilters(prev => ({ ...prev, ...patch }))
  }

  function resetFilters() {
    setFilters({ availabilityDays: [], mentorshipDuration: [], minMatchScore: 0 })
  }

  function toggleCompare(mentorId) {
    setCompareIds(prev => {
      const next = new Set(prev)
      if (next.has(mentorId)) {
        next.delete(mentorId)
      } else {
        if (next.size >= 3) {
          showTransientToast('You can compare up to 3 mentors at a time.')
          return prev
        }
        next.add(mentorId)
      }
      return next
    })
  }

  // Mentor objects currently selected for comparison. Combines the AI
  // matches list and the regular all-mentors list so a user can mix-and-
  // match across surfaces.
  const compareMentors = (() => {
    const pool = new Map()
    for (const m of mentors) pool.set(m.id, m)
    for (const m of matches) pool.set(m.id, m)
    return [...compareIds].map(id => pool.get(id)).filter(Boolean)
  })()

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
            data-testid="explore-search-input"
          />
        </div>
        {/* #139: open compare modal. Disabled when no mentor selected. */}
        {isMentee && (
          <button
            type="button"
            className="action-btn"
            disabled={compareIds.size === 0}
            onClick={() => setCompareOpen(true)}
            data-testid="explore-compare-btn"
            title={compareIds.size === 0 ? 'Select up to 3 mentors to compare' : `Compare ${compareIds.size} selected`}
            style={{ whiteSpace: 'nowrap' }}
          >
            Compare ({compareIds.size}/3)
          </button>
        )}
      </div>

      <div className={`explore-body${isMentee ? ' explore-body--with-sidebar' : ''}`}>
        {/* #139: mentor-side filter sidebar. Mentees only — backend matching
            API is mentee-only too, so showing it on mentor view would be moot. */}
        {isMentee && (
          <MentorFilterSidebar
            availabilityDays={filters.availabilityDays}
            mentorshipDuration={filters.mentorshipDuration}
            minMatchScore={filters.minMatchScore}
            onChange={updateFilters}
            onReset={resetFilters}
          />
        )}

      <div className="explore-main">
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
        <div className="mentors-grid" data-testid="explore-mentor-grid">
          {filtered.map(m => {
            const tags = (m.interests || []).slice(0, 3)
            const alreadySent = requestSentIds.has(m.id)
            const full = m.maxMenteeCapacity != null && m.currentMenteeCount >= m.maxMenteeCapacity
            const btnDisabled = alreadySent || hasActiveMentor || full
            return (
              <div className="mentor-card" key={m.id} data-testid={`explore-mentor-card-${m.id}`}>
                <div className="mc-header">
                  <div className="mc-info">
                    <Avatar src={m.profilePhoto} initials={m.firstName?.[0]?.toUpperCase() ?? '?'} size="md" />
                    <div>
                      <div className="mc-name" data-testid={`explore-mentor-name-${m.id}`}>{m.firstName}</div>
                      <div className="mc-sub" data-testid={`explore-mentor-city-${m.id}`}>{[m.expertise, m.affiliation].filter(Boolean).join(' · ')}</div>
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
                  <label className="mentor-compare-toggle" title="Add to comparison (up to 3)">
                    <input
                      type="checkbox"
                      checked={compareIds.has(m.id)}
                      onChange={() => toggleCompare(m.id)}
                      aria-label={`Add ${m.firstName} to comparison`}
                    />
                    Compare
                  </label>
                  <div className="mentor-actions">
                    <button
                      className={`send-request-btn${alreadySent ? ' sent' : ''}${hasActiveMentor && !alreadySent ? ' locked' : ''}`}
                      disabled={btnDisabled}
                      onClick={() => !btnDisabled && openRequestModal(m)}
                      title={hasActiveMentor && !alreadySent ? 'You already have an active mentor' : undefined}
                      data-testid={`explore-send-request-${m.id}`}
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
      </div> {/* /explore-main */}
      </div> {/* /explore-body */}

      <MentorCompareModal
        open={compareOpen}
        mentors={compareMentors}
        onClose={() => setCompareOpen(false)}
        onRemove={(id) => setCompareIds(prev => {
          const next = new Set(prev)
          next.delete(id)
          if (next.size === 0) setCompareOpen(false)
          return next
        })}
      />
    </MainLayout>
  )
}
