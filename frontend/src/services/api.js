const BASE_URL = '/api'

async function handleResponse(res) {
  if (res.ok) return res.json()
  let message
  try {
    const body = await res.json()
    message = body.message || body.error || JSON.stringify(body)
  } catch {
    message = res.statusText
  }
  throw new Error(message)
}

export async function registerUser({ firstName, lastName, email, password, isMentor }) {
  const res = await fetch(`${BASE_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ firstName, lastName, email, password, isMentor }),
  })
  return handleResponse(res)
}

export async function loginUser({ email, password }) {
  const res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  return handleResponse(res)
}

export async function verifyEmail({ token }) {
  const res = await fetch(`${BASE_URL}/auth/verify-email?token=${encodeURIComponent(token)}`)
  return handleResponse(res)
}

export async function validateResetToken({ token }) {
  const res = await fetch(`${BASE_URL}/auth/validate-reset-token?token=${encodeURIComponent(token)}`)
  return handleResponse(res)
}

export async function resendVerification({ email }) {
  const res = await fetch(`${BASE_URL}/auth/resend-verification`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  })
  return handleResponse(res)
}

export async function forgotPassword({ email }) {
  const res = await fetch(`${BASE_URL}/auth/forgot-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  })
  return handleResponse(res)
}

export async function resetPassword({ token, newPassword }) {
  const res = await fetch(`${BASE_URL}/auth/reset-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, newPassword }),
  })
  return handleResponse(res)
}

export async function getMatchingMentors(keyword) {
  const token = localStorage.getItem('auth_token')
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  const url = keyword
    ? `${BASE_URL}/matching/mentors?keyword=${encodeURIComponent(keyword)}`
    : `${BASE_URL}/matching/mentors`
  const res = await fetch(url, { headers })
  return handleResponse(res)
}

export async function getAllMentors() {
  await new Promise(r => setTimeout(r, 600))
  // Provide robust mock mentors since the backend endpoint might not be ready
  return [
    {
      id: 'm1',
      firstName: 'Alan',
      lastName: 'Turing',
      expertise: 'Senior Engineer',
      affiliation: 'Tech Corp',
      interests: ['Backend', 'AI/ML', 'Data'],
      bio: 'Passionate about building scalable systems and training neural networks.',
      maxMenteeCapacity: 3,
      currentMenteeCount: 1
    },
    {
      id: 'm2',
      firstName: 'Grace',
      lastName: 'Hopper',
      expertise: 'DevOps Specialist',
      affiliation: 'CloudNet',
      interests: ['DevOps', 'Backend'],
      bio: 'Infrastructure as code enthusiast and CI/CD expert.',
      maxMenteeCapacity: 2,
      currentMenteeCount: 2
    },
    {
      id: 'm3',
      firstName: 'Ada',
      lastName: 'Lovelace',
      expertise: 'Frontend Architect',
      affiliation: 'WebWorks',
      interests: ['Frontend', 'Mobile'],
      bio: 'Crafting beautiful user interfaces and pixel-perfect mobile apps.',
      maxMenteeCapacity: 5,
      currentMenteeCount: 3
    }
  ]
}

export async function getSentMentorshipRequests() {
  const token = localStorage.getItem('auth_token')
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${BASE_URL}/mentorship-requests/sent`, { headers })
  return handleResponse(res)
}

export async function createMentorshipRequest({ mentorId, message }) {
  const token = localStorage.getItem('auth_token')
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`${BASE_URL}/mentorship-requests`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ mentorId, message }),
  })
  return handleResponse(res)
}

// --- MOCK API ENDPOINTS FOR INCOMING REQUESTS ---
// These should be updated to real fetches once the backend is ready.

export async function getIncomingMentorshipRequests() {
  await new Promise(r => setTimeout(r, 800))
  return [
    {
      id: '101',
      menteeId: '201',
      menteeFirstName: 'Ayşe', 
      createdAt: '2026-04-05T14:30:00Z',
      message: 'I would love to learn more about frontend architecture from you.',
      interests: ['Frontend', 'React', 'System Design'],
      background: 'Junior Software Engineer with 1 year of experience in web development, focusing on React and modern JS frameworks.'
    },
    {
      id: '102',
      menteeId: '202',
      menteeFirstName: 'Can',
      createdAt: '2026-04-06T09:15:00Z',
      message: 'Looking for guidance on career progression.',
      interests: ['Career Growth', 'Backend', 'DevOps'],
      background: 'New grad from Computer Engineering looking for first full-time role and technical mentorship.'
    }
  ]
}

export async function acceptMentorshipRequest(requestId) {
  await new Promise(r => setTimeout(r, 1000))
  return { success: true }
}

export async function declineMentorshipRequest(requestId) {
  await new Promise(r => setTimeout(r, 1000))
  return { success: true }
}

export async function getMentorCapacityInfo() {
  await new Promise(r => setTimeout(r, 500))
  return {
    activeMentees: 1, 
    maxCapacity: 3
  }
}
