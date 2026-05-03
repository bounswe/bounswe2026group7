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

  const activeMenteeCount = typeof mentorStats?.currentMenteeCount === 'number'
    ? mentorStats.currentMenteeCount
    : null
  const maxCapacity = typeof mentorStats?.maxMenteeCapacity === 'number'
    ? mentorStats.maxMenteeCapacity
    : null
  const availableSlots = activeMenteeCount != null && maxCapacity != null
    ? maxCapacity - activeMenteeCount
    : null

  const sentPendingCount = useMemo(
    () => sentRequests.filter(r => r.status === 'PENDING').length,
    [sentRequests]
  )

  const activeMenteeBadgeCount = isMentor
    ? activeMenteeCount
    : (menteeLoading ? null : activeMentorships.filter(m => m.status === 'ACTIVE').length)

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
  }, [])

  const handleMentorRequestAccepted = useCallback((requestId, newMentorship) => {
    setReceivedRequests(prev => prev.filter(r => r.id !== requestId))
    setActiveMentorships(prev => [newMentorship, ...prev])
    setMentorStats(prev => prev
      ? { ...prev, currentMenteeCount: (prev.currentMenteeCount || 0) + 1 }
      : prev
    )
  }, [])

  const value = {
    mentorLoading,
    menteeLoading,
    receivedRequests,
    activeMentorships,
    mentorStats,
    pendingRequests,
    pendingCount: pendingRequests.length,
    sentRequests,
    sentPendingCount,
    tasksCount,
    sessionsCount,
    activeMenteeCount,
    maxCapacity,
    availableSlots,
    activeMenteeBadgeCount,
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
