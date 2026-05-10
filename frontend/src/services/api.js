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
    ? `${BASE_URL}/matching/mentors/all?keyword=${encodeURIComponent(keyword)}`
    : `${BASE_URL}/matching/mentors/all`
  const res = await fetch(url, { headers })
  return handleResponse(res)
}

export async function getAllMentors() {
  const token = localStorage.getItem('auth_token')
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${BASE_URL}/users/mentors/all`, { headers })
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

export async function uploadProfilePhoto(file) {
  const token = localStorage.getItem('auth_token')
  const formData = new FormData()
  formData.append('file', file)
  const res = await fetch(`${BASE_URL}/users/me/photo`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
  })
  return handleResponse(res)
}

export async function deleteProfilePhoto() {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/users/me/photo`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function updateOwnProfile(data, role) {
  const token = localStorage.getItem('auth_token')
  // Backend exposes role-specific PATCH endpoints (`/users/me/mentor` and
  // `/users/me/mentee`); the generic `/users/me` PATCH does not exist.
  // Pick the path from the explicit role argument, falling back to the
  // role stored on login.
  const effectiveRole = role || localStorage.getItem('auth_role')
  const path = effectiveRole === 'MENTOR' ? '/users/me/mentor' : '/users/me/mentee'
  const res = await fetch(`${BASE_URL}${path}`, {
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

// Backend has no GET /api/mentorships/{id} yet — fetch the user's list and
// filter client-side. If the id isn't in the list, the caller treats it as 403.
export async function getMentorshipById(id) {
  const list = await getActiveMentorships()
  const match = (list || []).find(m => String(m.id) === String(id))
  if (!match) {
    const err = new Error('Forbidden')
    err.status = 403
    throw err
  }
  return match
}

// ── Mentorship lifecycle (cancel / end / extend / rate) ──────────────────
// Backend: MentorshipController endpoints landed via #237/#133. Mentee uses
// /cancel; mentor uses /end + /extend; rating is mentee-only after end.

export async function cancelMentorship(id, reason) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/mentorships/${id}/cancel`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ reason }),
  })
  return handleResponse(res)
}

// Mentor-only end (graceful close). `reason` is optional per backend
// EndMentorshipRequest — sending {} is valid.
export async function endMentorship(id, reason) {
  const token = localStorage.getItem('auth_token')
  const body = reason ? { reason } : {}
  const res = await fetch(`${BASE_URL}/mentorships/${id}/end`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

export async function updateSharedGoal(id, sharedGoal) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/mentorships/${id}/goal`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ sharedGoal }),
  })
  return handleResponse(res)
}

export async function getMentorAvailability(mentorId) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/availability/${mentorId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function saveMentorAvailability(data) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/availability`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  })
  return handleResponse(res)
}

// ── Mentorship messages ────────────────────────────────────────────────────
// Backend: MessageController @ /api/mentorships/{mentorshipId}/messages

export async function getMentorshipMessages(mentorshipId, page = 0, size = 20) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(
    `${BASE_URL}/mentorships/${mentorshipId}/messages?page=${page}&size=${size}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  return handleResponse(res)
}

export async function sendMentorshipMessage(mentorshipId, { content, attachmentId } = {}) {
  const token = localStorage.getItem('auth_token')
  const body = { content }
  if (attachmentId) body.attachmentId = attachmentId
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

export async function markMentorshipMessagesRead(mentorshipId) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/messages/read`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

// ── Mentor-pair (mentor-to-mentor) messaging ──────────────────────────────
// Backend: MentorPairInboxController + MentorPairMessageController, both
// gated by @PreAuthorize("hasRole('MENTOR')"). Conversations are auto-created
// on first GET/POST per peer mentor id.

export async function getMentorPairInbox(page = 0, size = 20) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(
    `${BASE_URL}/conversations/mentor-pair?page=${page}&size=${size}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  return handleResponse(res)
}

export async function getMentorPairMessages(otherMentorId, page = 0, size = 20) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(
    `${BASE_URL}/conversations/mentor-pair/${otherMentorId}/messages?page=${page}&size=${size}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  return handleResponse(res)
}

export async function sendMentorPairMessage(otherMentorId, { content, attachmentId } = {}) {
  const token = localStorage.getItem('auth_token')
  const body = { content }
  if (attachmentId) body.attachmentId = attachmentId
  const res = await fetch(
    `${BASE_URL}/conversations/mentor-pair/${otherMentorId}/messages`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    },
  )
  return handleResponse(res)
}

export async function markMentorPairMessagesRead(otherMentorId) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(
    `${BASE_URL}/conversations/mentor-pair/${otherMentorId}/messages/read`,
    { method: 'PATCH', headers: { Authorization: `Bearer ${token}` } },
  )
  return handleResponse(res)
}

// ── Social feed ───────────────────────────────────────────────────────────
// Backend: FeedPostController + FeedReadController (+ FeedInteractionController in #340)

export async function createFeedPost({ body, hashtags = [] }) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/feed/posts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ body, hashtags }),
  })
  return handleResponse(res)
}

export async function getFeedPostById(id) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/feed/posts/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function updateFeedPost(id, { body, hashtags }) {
  const token = localStorage.getItem('auth_token')
  const payload = {}
  if (body !== undefined) payload.body = body
  if (hashtags !== undefined) payload.hashtags = hashtags
  const res = await fetch(`${BASE_URL}/feed/posts/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  })
  return handleResponse(res)
}

export async function deleteFeedPost(id) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/feed/posts/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function getForYouFeed(page = 0, size = 20) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/feed/for-you?page=${page}&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function getFollowingFeed(page = 0, size = 20) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/feed/following?page=${page}&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function searchFeed({ q, hashtag, page = 0, size = 20 }) {
  const token = localStorage.getItem('auth_token')
  const params = new URLSearchParams()
  if (q) params.set('q', q)
  if (hashtag) params.set('hashtag', hashtag)
  params.set('page', String(page))
  params.set('size', String(size))
  const res = await fetch(`${BASE_URL}/feed/search?${params.toString()}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

// ── Feed interactions (share, bookmark) — #340 ───────────────────────────
// Backend: FeedInteractionController. Each endpoint returns a
// FeedPostInteractionState { likeCount, commentCount, shareCount,
// bookmarkCount, viewerHasLiked, viewerHasBookmarked } the UI uses to
// render counts + viewer-relative toggle state.

export async function getPostInteractions(postId) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/interactions`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function toggleBookmarkOnPost(postId) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/bookmark`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function recordShareOnPost(postId) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/share`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function getMyBookmarks(page = 0, size = 20) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/feed/me/bookmarks?page=${page}&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function getMenteeAvailability() {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/mentee-availability`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

export async function saveMenteeAvailability(data) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/mentee-availability`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  })
  return handleResponse(res)
}
