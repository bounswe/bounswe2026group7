const BASE_URL = '/api'

async function handleResponse(res) {
  if (res.ok) {
    const text = await res.text()
    return text ? JSON.parse(text) : null
  }
  let message
  try {
    const body = JSON.parse(await res.text())
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
  const token = localStorage.getItem('auth_token')
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${BASE_URL}/users/mentors`, { headers })
  return handleResponse(res)
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

export async function getOwnProfile() {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function updateOwnProfile(data, role) {
  const token = localStorage.getItem('auth_token')
  const endpoint = role === 'MENTOR' ? '/users/me/mentor' : '/users/me/mentee'
  const res = await fetch(`${BASE_URL}${endpoint}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  })
  return handleResponse(res)
}

export async function getUserById(id) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/users/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function getNotifications(unreadOnly = false) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/notifications?unreadOnly=${unreadOnly}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function markNotificationAsRead(id) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/notifications/${id}/read`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function markAllNotificationsAsRead() {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/notifications/read-all`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function getReceivedMentorshipRequests(page = 0, size = 20) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/mentorship-requests/received?page=${page}&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function acceptMentorshipRequest(id, duration) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/mentorship-requests/${id}/accept`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ duration }),
  })
  return handleResponse(res)
}

export async function rejectMentorshipRequest(id) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/mentorship-requests/${id}/reject`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function getMatchingMentees(keyword) {
  const token = localStorage.getItem('auth_token')
  const url = keyword
    ? `${BASE_URL}/matching/mentees?keyword=${encodeURIComponent(keyword)}`
    : `${BASE_URL}/matching/mentees`
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function getActiveMentorships() {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/mentorships`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}
