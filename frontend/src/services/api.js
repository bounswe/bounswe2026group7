const BASE_URL = '/api'

function authHeaders() {
  const token = localStorage.getItem('auth_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function authJsonHeaders() {
  const headers = { 'Content-Type': 'application/json' }
  const token = localStorage.getItem('auth_token')
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

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
  const url = keyword
    ? `${BASE_URL}/matching/mentors/all?keyword=${encodeURIComponent(keyword)}`
    : `${BASE_URL}/matching/mentors/all`
  const res = await fetch(url, { headers: authHeaders() })
  return handleResponse(res)
}

export async function getAllMentors() {
  const res = await fetch(`${BASE_URL}/users/mentors/all`, { headers: authHeaders() })
  return handleResponse(res)
}

export async function getSentMentorshipRequests() {
  const res = await fetch(`${BASE_URL}/mentorship-requests/sent`, { headers: authHeaders() })
  return handleResponse(res)
}

export async function createMentorshipRequest({ mentorId, message }) {
  const res = await fetch(`${BASE_URL}/mentorship-requests`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify({ mentorId, message }),
  })
  return handleResponse(res)
}

export async function getOwnProfile() {
  const res = await fetch(`${BASE_URL}/users/me`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function uploadProfilePhoto(file) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await fetch(`${BASE_URL}/users/me/photo`, {
    method: 'POST',
    headers: authHeaders(),
    body: formData,
  })
  return handleResponse(res)
}

export async function deleteProfilePhoto() {
  const res = await fetch(`${BASE_URL}/users/me/photo`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function updateOwnProfile(data, role) {
  // Backend exposes role-specific PATCH endpoints (`/users/me/mentor` and
  // `/users/me/mentee`); the generic `/users/me` PATCH does not exist.
  // Pick the path from the explicit role argument, falling back to the
  // role stored on login.
  const effectiveRole = role || localStorage.getItem('auth_role')
  const path = effectiveRole === 'MENTOR' ? '/users/me/mentor' : '/users/me/mentee'
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'PATCH',
    headers: authJsonHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse(res)
}

export async function getUserById(id) {
  const res = await fetch(`${BASE_URL}/users/${id}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getNotifications(unreadOnly = false) {
  const res = await fetch(`${BASE_URL}/notifications?unreadOnly=${unreadOnly}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function markNotificationAsRead(id) {
  const res = await fetch(`${BASE_URL}/notifications/${id}/read`, {
    method: 'PATCH',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function markAllNotificationsAsRead() {
  const res = await fetch(`${BASE_URL}/notifications/read-all`, {
    method: 'PATCH',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getReceivedMentorshipRequests(page = 0, size = 20) {
  const res = await fetch(`${BASE_URL}/mentorship-requests/received?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function acceptMentorshipRequest(id, duration) {
  const res = await fetch(`${BASE_URL}/mentorship-requests/${id}/accept`, {
    method: 'PUT',
    headers: authJsonHeaders(),
    body: JSON.stringify({ duration }),
  })
  return handleResponse(res)
}

export async function rejectMentorshipRequest(id) {
  const res = await fetch(`${BASE_URL}/mentorship-requests/${id}/reject`, {
    method: 'PUT',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getMatchingMentees(keyword) {
  const url = keyword
    ? `${BASE_URL}/matching/mentees?keyword=${encodeURIComponent(keyword)}`
    : `${BASE_URL}/matching/mentees`
  const res = await fetch(url, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getActiveMentorships() {
  const res = await fetch(`${BASE_URL}/mentorships`, {
    headers: authHeaders(),
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
  const res = await fetch(`${BASE_URL}/mentorships/${id}/cancel`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify({ reason }),
  })
  return handleResponse(res)
}

// Mentor-only end (graceful close). `reason` is optional per backend
// EndMentorshipRequest — sending {} is valid.
export async function endMentorship(id, reason) {
  const body = reason ? { reason } : {}
  const res = await fetch(`${BASE_URL}/mentorships/${id}/end`, {
    method: 'PATCH',
    headers: authJsonHeaders(),
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

// ── Mentorship tasks (#125) ───────────────────────────────────────────────
// Backend: TaskController. Status enum: PENDING, SUBMITTED, REVISION_REQUESTED, COMPLETED.

export async function createTask(mentorshipId, { title, description, dueDate, assignmentAttachmentIds } = {}) {
  const body = { title }
  if (description !== undefined) body.description = description
  if (dueDate !== undefined) body.dueDate = dueDate
  if (Array.isArray(assignmentAttachmentIds) && assignmentAttachmentIds.length > 0) {
    body.assignmentAttachmentIds = assignmentAttachmentIds
  }
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/tasks`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

export async function listMentorshipTasks(mentorshipId) {
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/tasks`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getTaskDetail(taskId) {
  const res = await fetch(`${BASE_URL}/tasks/${taskId}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function submitTask(taskId, { submissionText, attachmentIds } = {}) {
  const body = { submissionText }
  if (Array.isArray(attachmentIds) && attachmentIds.length > 0) body.attachmentIds = attachmentIds
  const res = await fetch(`${BASE_URL}/tasks/${taskId}/submission`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

export async function reviewTask(taskId, { feedback, status }) {
  const res = await fetch(`${BASE_URL}/tasks/${taskId}/feedback`, {
    method: 'PATCH',
    headers: authJsonHeaders(),
    body: JSON.stringify({ feedback, status }),
  })
  return handleResponse(res)
}

export async function deleteTask(taskId) {
  const res = await fetch(`${BASE_URL}/tasks/${taskId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// ── Mentorship milestones (#288) ─────────────────────────────────────────
// Backend: MilestoneController. Status enum: PENDING, IN_PROGRESS, COMPLETED.
// Mentor-only mutations (1.1.5.12); both parties may toggle action-item completion.

export async function listMilestones(mentorshipId) {
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/milestones`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getMilestoneDetail(milestoneId) {
  const res = await fetch(`${BASE_URL}/milestones/${milestoneId}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function createMilestone(mentorshipId, { title, description, targetDate, orderIndex } = {}) {
  const body = { title }
  if (description !== undefined) body.description = description
  if (targetDate !== undefined) body.targetDate = targetDate
  if (orderIndex !== undefined) body.orderIndex = orderIndex
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/milestones`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

// status / title / description / targetDate / orderIndex are all optional;
// pass only the fields the caller wants to change.
export async function updateMilestone(milestoneId, payload) {
  const res = await fetch(`${BASE_URL}/milestones/${milestoneId}`, {
    method: 'PATCH',
    headers: authJsonHeaders(),
    body: JSON.stringify(payload || {}),
  })
  return handleResponse(res)
}

export async function deleteMilestone(milestoneId) {
  const res = await fetch(`${BASE_URL}/milestones/${milestoneId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function createMilestoneActionItem(milestoneId, { text, orderIndex } = {}) {
  const body = { text }
  if (orderIndex !== undefined) body.orderIndex = orderIndex
  const res = await fetch(`${BASE_URL}/milestones/${milestoneId}/action-items`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

// Backend expects `completed` (JSON name), not `isCompleted`. text + orderIndex
// are mentor-only fields; `completed` is mentee-or-mentor.
export async function updateMilestoneActionItem(itemId, { text, completed, orderIndex } = {}) {
  const body = {}
  if (text !== undefined) body.text = text
  if (completed !== undefined) body.completed = completed
  if (orderIndex !== undefined) body.orderIndex = orderIndex
  const res = await fetch(`${BASE_URL}/milestone-action-items/${itemId}`, {
    method: 'PATCH',
    headers: authJsonHeaders(),
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

export async function deleteMilestoneActionItem(itemId) {
  const res = await fetch(`${BASE_URL}/milestone-action-items/${itemId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// ── Meetings (#337 / #338) ────────────────────────────────────────────────
// Backend: MeetingController. Statuses: PENDING_CONFIRMATION, CONFIRMED,
// DECLINED, EXPIRED, COMPLETED, CANCELLED. ONLINE meetings require
// `meetingLink`; recurring meetings carry an RFC 5545 RRULE.

export async function listMentorshipMeetings(mentorshipId) {
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/meetings`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getMeetingDetail(meetingId) {
  const res = await fetch(`${BASE_URL}/meetings/${meetingId}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// Mentor creates a meeting. Returns MeetingCreateResponse { meetings[], warnings[] }
// because a recurring meeting expands server-side into multiple Meeting rows.
export async function createMeeting(mentorshipId, payload) {
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/meetings`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify(payload),
  })
  return handleResponse(res)
}

export async function confirmMeeting(meetingId) {
  const res = await fetch(`${BASE_URL}/meetings/${meetingId}/confirm`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function declineMeeting(meetingId) {
  const res = await fetch(`${BASE_URL}/meetings/${meetingId}/decline`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// ── Meeting reschedule + cancel (#338) ────────────────────────────────────
// Backend: MeetingController. Either party may request a reschedule;
// the OTHER party (counterpart) approves or rejects. Cancel is mentor-only (DELETE).

export async function requestMeetingReschedule(meetingId, { proposedStart, proposedEnd, reason } = {}) {
  const token = localStorage.getItem('auth_token')
  const body = { proposedStart, proposedEnd }
  if (reason) body.reason = reason
  const res = await fetch(`${BASE_URL}/meetings/${meetingId}/reschedule-requests`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

export async function approveMeetingReschedule(meetingId, rescheduleId) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(
    `${BASE_URL}/meetings/${meetingId}/reschedule-requests/${rescheduleId}/approve`,
    { method: 'POST', headers: { Authorization: `Bearer ${token}` } },
  )
  return handleResponse(res)
}

export async function rejectMeetingReschedule(meetingId, rescheduleId) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(
    `${BASE_URL}/meetings/${meetingId}/reschedule-requests/${rescheduleId}/reject`,
    { method: 'POST', headers: { Authorization: `Bearer ${token}` } },
  )
  return handleResponse(res)
}

export async function cancelMeeting(meetingId) {
  const token = localStorage.getItem('auth_token')
  const res = await fetch(`${BASE_URL}/meetings/${meetingId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  return handleResponse(res)
}

// ── Mentorship progress + timeline (#126 + #333) ──────────────────────────
// Backend: MentorshipController.
//   /progress  → MentorshipProgressResponse (counts + 0..1 ratio + lastActivityAt)
//   /timeline  → TimelineResponse (startDate, endDate, currentDate, items[])

export async function getMentorshipProgress(id) {
  const res = await fetch(`${BASE_URL}/mentorships/${id}/progress`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getMentorshipTimeline(id) {
  const res = await fetch(`${BASE_URL}/mentorships/${id}/timeline`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function updateSharedGoal(id, sharedGoal) {
  const res = await fetch(`${BASE_URL}/mentorships/${id}/goal`, {
    method: 'PUT',
    headers: authJsonHeaders(),
    body: JSON.stringify({ sharedGoal }),
  })
  return handleResponse(res)
}

export async function getMentorAvailability(mentorId) {
  const res = await fetch(`${BASE_URL}/availability/${mentorId}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function saveMentorAvailability(data) {
  const res = await fetch(`${BASE_URL}/availability`, {
    method: 'PUT',
    headers: authJsonHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse(res)
}

// ── Mentorship messages ────────────────────────────────────────────────────
// Backend: MessageController @ /api/mentorships/{mentorshipId}/messages

export async function getMentorshipMessages(mentorshipId, page = 0, size = 20) {
  const res = await fetch(
    `${BASE_URL}/mentorships/${mentorshipId}/messages?page=${page}&size=${size}`,
    { headers: authHeaders() },
  )
  return handleResponse(res)
}

export async function sendMentorshipMessage(mentorshipId, { content, attachmentId } = {}) {
  const body = { content }
  if (attachmentId) body.attachmentId = attachmentId
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/messages`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify(body),
  })
  return handleResponse(res)
}

export async function markMentorshipMessagesRead(mentorshipId) {
  const res = await fetch(`${BASE_URL}/mentorships/${mentorshipId}/messages/read`, {
    method: 'PATCH',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// ── Mentor-pair (mentor-to-mentor) messaging ──────────────────────────────
// Backend: MentorPairInboxController + MentorPairMessageController, both
// gated by @PreAuthorize("hasRole('MENTOR')"). Conversations are auto-created
// on first GET/POST per peer mentor id.

export async function getMentorPairInbox(page = 0, size = 20) {
  const res = await fetch(
    `${BASE_URL}/conversations/mentor-pair?page=${page}&size=${size}`,
    { headers: authHeaders() },
  )
  return handleResponse(res)
}

export async function getMentorPairMessages(otherMentorId, page = 0, size = 20) {
  const res = await fetch(
    `${BASE_URL}/conversations/mentor-pair/${otherMentorId}/messages?page=${page}&size=${size}`,
    { headers: authHeaders() },
  )
  return handleResponse(res)
}

export async function sendMentorPairMessage(otherMentorId, { content, attachmentId } = {}) {
  const body = { content }
  if (attachmentId) body.attachmentId = attachmentId
  const res = await fetch(
    `${BASE_URL}/conversations/mentor-pair/${otherMentorId}/messages`,
    {
      method: 'POST',
      headers: authJsonHeaders(),
      body: JSON.stringify(body),
    },
  )
  return handleResponse(res)
}

export async function markMentorPairMessagesRead(otherMentorId) {
  const res = await fetch(
    `${BASE_URL}/conversations/mentor-pair/${otherMentorId}/messages/read`,
    { method: 'PATCH', headers: authHeaders() },
  )
  return handleResponse(res)
}

// ── Social feed ───────────────────────────────────────────────────────────
// Backend: FeedPostController + FeedReadController (+ FeedInteractionController in #340)

export async function createFeedPost({ body, hashtags = [] }) {
  const res = await fetch(`${BASE_URL}/feed/posts`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify({ body, hashtags }),
  })
  return handleResponse(res)
}

export async function getFeedPostById(id) {
  const res = await fetch(`${BASE_URL}/feed/posts/${id}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function updateFeedPost(id, { body, hashtags }) {
  const payload = {}
  if (body !== undefined) payload.body = body
  if (hashtags !== undefined) payload.hashtags = hashtags
  const res = await fetch(`${BASE_URL}/feed/posts/${id}`, {
    method: 'PATCH',
    headers: authJsonHeaders(),
    body: JSON.stringify(payload),
  })
  return handleResponse(res)
}

export async function deleteFeedPost(id) {
  const res = await fetch(`${BASE_URL}/feed/posts/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getForYouFeed(page = 0, size = 20) {
  const res = await fetch(`${BASE_URL}/feed/for-you?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getFollowingFeed(page = 0, size = 20) {
  const res = await fetch(`${BASE_URL}/feed/following?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function searchFeed({ q, hashtag, page = 0, size = 20 }) {
  const params = new URLSearchParams()
  if (q) params.set('q', q)
  if (hashtag) params.set('hashtag', hashtag)
  params.set('page', String(page))
  params.set('size', String(size))
  const res = await fetch(`${BASE_URL}/feed/search?${params.toString()}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// ── Feed interactions (share, bookmark) — #340 ───────────────────────────
// Backend: FeedInteractionController. Each endpoint returns a
// FeedPostInteractionState { likeCount, commentCount, shareCount,
// bookmarkCount, viewerHasLiked, viewerHasBookmarked } the UI uses to
// render counts + viewer-relative toggle state.

export async function getPostInteractions(postId) {
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/interactions`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function toggleBookmarkOnPost(postId) {
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/bookmark`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function recordShareOnPost(postId) {
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/share`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getMyBookmarks(page = 0, size = 20) {
  const res = await fetch(`${BASE_URL}/feed/me/bookmarks?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getMenteeAvailability() {
  const res = await fetch(`${BASE_URL}/mentee-availability`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function saveMenteeAvailability(data) {
  const res = await fetch(`${BASE_URL}/mentee-availability`, {
    method: 'PUT',
    headers: authJsonHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse(res)
}
