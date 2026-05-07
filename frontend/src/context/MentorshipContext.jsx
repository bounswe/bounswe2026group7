import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import {
  getActiveMentorships,
  getOwnProfile,
  getReceivedMentorshipRequests,
  getSentMentorshipRequests,
} from '../services/api'
import { getMeetingsAcrossMentorships, getTasksAcrossMentorships } from '../services/mentorshipMocks'
import { useAuth } from './AuthContext'

const MentorshipContext = createContext(null)

const emptyMentorState = {
  receivedRequests: [],
  activeMentorships: [],
  mentorStats: null,
  mentorLoading: false,
}

const emptyMenteeState = {
  sentRequests: [],
  menteeLoading: false,
}

export function MentorshipProvider({ children }) {
  const { role } = useAuth()
  const isMentor = role === 'MENTOR'
  const isMentee = role === 'MENTEE'

  const [receivedRequests, setReceivedRequests] = useState(emptyMentorState.receivedRequests)
  const [activeMentorships, setActiveMentorships] = useState(emptyMentorState.activeMentorships)
  const [mentorStats, setMentorStats] = useState(emptyMentorState.mentorStats)
  const [mentorLoading, setMentorLoading] = useState(emptyMentorState.mentorLoading)
  const [sentRequests, setSentRequests] = useState(emptyMenteeState.sentRequests)
  const [menteeLoading, setMenteeLoading] = useState(emptyMenteeState.menteeLoading)
  const [tasksCount, setTasksCount] = useState(0)
  const [sessionsCount, setSessionsCount] = useState(0)

  const resetMentorState = useCallback(() => {
    setReceivedRequests(emptyMentorState.receivedRequests)
    setActiveMentorships(emptyMentorState.activeMentorships)
    setMentorStats(emptyMentorState.mentorStats)
    setMentorLoading(emptyMentorState.mentorLoading)
  }, [])

  const resetMenteeState = useCallback(() => {
    setSentRequests(emptyMenteeState.sentRequests)
    setMenteeLoading(emptyMenteeState.menteeLoading)
  }, [])

  const loadMentorData = useCallback(() => {
    if (!isMentor) return
    setMentorLoading(true)
    Promise.allSettled([
      getReceivedMentorshipRequests(),
      getActiveMentorships(),
      getOwnProfile(),
    ]).then(([reqs, mentorships, profile]) => {
      if (reqs.status === 'fulfilled') {
        setReceivedRequests(reqs.value?.content || [])
      } else {
        setReceivedRequests([])
      }
      if (mentorships.status === 'fulfilled') {
        setActiveMentorships(mentorships.value || [])
      } else {
        setActiveMentorships([])
      }
      if (profile.status === 'fulfilled') {
        setMentorStats(profile.value)
      } else {
        setMentorStats(null)
      }
    }).finally(() => setMentorLoading(false))
  }, [isMentor])

  const loadMenteeData = useCallback(() => {
    if (!isMentee) return
    setMenteeLoading(true)
    Promise.allSettled([
      getSentMentorshipRequests(),
      getActiveMentorships(),
    ]).then(([sent, mentorships]) => {
      if (sent.status === 'fulfilled') {
        setSentRequests(sent.value?.content || [])
      } else {
        setSentRequests([])
      }
      if (mentorships.status === 'fulfilled') {
        setActiveMentorships(mentorships.value || [])
      } else {
        setActiveMentorships([])
      }
    }).finally(() => setMenteeLoading(false))
  }, [isMentee])

  useEffect(() => {
    if (isMentor) {
      resetMenteeState()
      loadMentorData()
      return
    }
    if (isMentee) {
      resetMentorState()
      loadMenteeData()
      return
    }
    resetMentorState()
    resetMenteeState()
  }, [isMentor, isMentee, loadMentorData, loadMenteeData, resetMentorState, resetMenteeState])

  const pendingRequests = useMemo(
    () => receivedRequests.filter(r => r.status === 'PENDING'),
    [receivedRequests]
  )

  const pendingCount = pendingRequests.length
  const activeMenteeCount = mentorStats?.currentMenteeCount ?? '-'
  const maxCapacity = mentorStats?.maxMenteeCapacity ?? '-'
  const availableSlots = typeof activeMenteeCount === 'number' && typeof maxCapacity === 'number'
    ? maxCapacity - activeMenteeCount : '-'
  const sentPendingCount = useMemo(
    () => sentRequests.filter(r => r.status === 'PENDING').length,
    [sentRequests]
  )
  // Role-agnostic active-mentorship count for surfaces shared between mentor and mentee
  // (sidebar badge, navbar dropdown). Mentors see how many mentees they have; mentees
  // see whether they currently have an active mentor (max 1 per requirement 1.1.1.1.9).
  const activeMentorshipCount = isMentor
    ? (typeof activeMenteeCount === 'number' ? activeMenteeCount : (activeMentorships?.length ?? 0))
    : (activeMentorships?.length ?? 0)

  useEffect(() => {
    let cancelled = false
    if (!activeMentorships || activeMentorships.length === 0) {
      setTasksCount(0)
      setSessionsCount(0)
      return () => { cancelled = true }
    }
    Promise.all([
      getTasksAcrossMentorships(activeMentorships),
      getMeetingsAcrossMentorships(activeMentorships),
    ]).then(([tasks, sessions]) => {
      if (cancelled) return
      setTasksCount(tasks.length)
      setSessionsCount(sessions.length)
    }).catch(() => {
      if (cancelled) return
      setTasksCount(0)
      setSessionsCount(0)
    })
    return () => { cancelled = true }
  }, [activeMentorships])

  const handleMentorRequestRejected = useCallback((requestId) => {
    setReceivedRequests(prev => prev.filter(r => r.id !== requestId))
    // Re-fetch from backend so sidebar/navbar/dashboard counts converge to the source of truth
    loadMentorData()
  }, [loadMentorData])

  const handleMentorRequestAccepted = useCallback((requestId, newMentorship) => {
    setReceivedRequests(prev => prev.filter(r => r.id !== requestId))
    setActiveMentorships(prev => [newMentorship, ...prev])
    setMentorStats(prev => prev
      ? { ...prev, currentMenteeCount: (prev.currentMenteeCount || 0) + 1 }
      : prev
    )
    // Re-fetch from backend so all count surfaces reconcile (mentorStats, requests, mentorships)
    loadMentorData()
  }, [loadMentorData])

  const value = {
    mentorLoading,
    menteeLoading,
    receivedRequests,
    activeMentorships,
    mentorStats,
    pendingRequests,
    pendingCount,
    sentRequests,
    sentPendingCount,
    tasksCount,
    sessionsCount,
    activeMenteeCount,
    activeMentorshipCount,
    maxCapacity,
    availableSlots,
    loadMentorData,
    loadMenteeData,
    handleMentorRequestRejected,
    handleMentorRequestAccepted,
    isMentor,
    isMentee,
  }

  return (
    <MentorshipContext.Provider value={value}>
      {children}
    </MentorshipContext.Provider>
  )
}

export function useMentorship() {
  const ctx = useContext(MentorshipContext)
  if (!ctx) throw new Error('useMentorship must be used within MentorshipProvider')
  return ctx
}
