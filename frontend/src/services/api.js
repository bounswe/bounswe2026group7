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
  let parsedBody = null
  try {
    parsedBody = JSON.parse(await res.text())
    message = parsedBody.message || parsedBody.error || JSON.stringify(parsedBody)
  } catch {
    message = res.statusText
  }
  // Cross-cutting concern: when the server reports the caller is banned, surface
  // the structured payload to whoever cares (AuthProvider listens) without
  // coupling this module to React. Listeners observe via window events; the
  // throw still happens so existing `.catch` handlers behave unchanged.
  if (res.status === 403 && parsedBody && parsedBody.code === 'BANNED_UNTIL'
      && typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    try {
      window.dispatchEvent(new CustomEvent('auth:banned', { detail: parsedBody }))
    } catch {
      // ignore — older browsers / jsdom edge cases
    }
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

// ── Follow graph (#343) ─────────────────────────────────────────────────

export async function followUser(id) {
  const res = await fetch(`${BASE_URL}/users/${id}/follow`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function unfollowUser(id) {
  const res = await fetch(`${BASE_URL}/users/${id}/follow`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getFollowers(id, page = 0, size = 20) {
  const res = await fetch(`${BASE_URL}/users/${id}/followers?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getFollowing(id, page = 0, size = 20) {
  const res = await fetch(`${BASE_URL}/users/${id}/following?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// ── Follow recommendations (#344) ───────────────────────────────────────

export async function getFollowRecommendations(page = 0, size = 12) {
  const res = await fetch(`${BASE_URL}/users/me/follow-recommendations?page=${page}&size=${size}`, {
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

// Author posts feed (#546 / backend #471). Paginated list of posts authored
// by `authorId`. Returns Page<FeedPostListItem>; backend caps size at 100.
export async function getUserFeedPosts(authorId, page = 0, size = 10) {
  const res = await fetch(`${BASE_URL}/feed/users/${authorId}/posts?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// Thin wrapper around the paginated mentorship history endpoint kept separate
// from getActiveMentorships() because HomePage + MentorshipContext rely on the
// older list-shaped response and shouldn't shift to Page<> semantics.
export async function listMentorships({ status = 'ALL', page = 0, size = 50 } = {}) {
  const params = new URLSearchParams({ status, page: String(page), size: String(size) })
  const res = await fetch(`${BASE_URL}/mentorships?${params}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// Trending hashtags (#545 / backend #487). Materialized-view aggregate over
// the last 24h, refreshed hourly server-side. Returns at most `limit`
// entries sorted by composite engagement score, each carrying { tag,
// postCount, score, ... }.
export async function getTrendingHashtags(limit = 10) {
  const res = await fetch(`${BASE_URL}/feed/trending/hashtags?limit=${limit}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// Paginated mentorship history filter (#408 / backend #521). status accepts
// 'ALL' or any MentorshipStatus name (ACTIVE / COMPLETED / CANCELLED /
// TERMINATED). Backend caps size at 100. Returns a Spring Page<>:
//   { content, totalPages, totalElements, number, size, last, ... }
export async function getMentorshipsByStatus({ status = 'ALL', page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ status, page: String(page), size: String(size) })
  const res = await fetch(`${BASE_URL}/mentorships?${params}`, {
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

// Mentor-only extend (#276 / 1.1.1.2.13). additionalMonths must be 1, 3, or 6
// per backend ExtendMentorshipRequest validator. Returns the updated mentorship.
export async function extendMentorship(id, additionalMonths) {
  const res = await fetch(`${BASE_URL}/mentorships/${id}/extend`, {
    method: 'PATCH',
    headers: authJsonHeaders(),
    body: JSON.stringify({ additionalMonths }),
  })
  return handleResponse(res)
}

// User notification preferences (#289 / 1.1.5.8). Backend lazily creates the
// row with all toggles enabled on first GET. PATCH is partial — omitted
// fields keep their current value, so the client only sends the toggle
// being flipped.
export async function getNotificationPreferences() {
  const res = await fetch(`${BASE_URL}/users/me/notification-preferences`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function updateNotificationPreferences(patch) {
  const res = await fetch(`${BASE_URL}/users/me/notification-preferences`, {
    method: 'PATCH',
    headers: authJsonHeaders(),
    body: JSON.stringify(patch),
  })
  return handleResponse(res)
}

// Read the rating for a mentorship (#556 / backend #534 / #518). Visible to
// both participants. Returns 404 when no rating exists yet — wrappers throw
// an Error with a status field so callers can branch cleanly on first paint.
export async function getMentorshipRating(id) {
  const res = await fetch(`${BASE_URL}/mentorships/${id}/rating`, {
    headers: authHeaders(),
  })
  if (res.status === 404) {
    const err = new Error('Not rated yet')
    err.status = 404
    throw err
  }
  return handleResponse(res)
}

// Paginated mentor ratings list (#556 / backend #534 / #518). Newest-first.
// Returns Spring Page<MentorRatingResponse>. Size capped at 50 server-side.
export async function getMentorRatings(userId, page = 0, size = 10) {
  const res = await fetch(`${BASE_URL}/users/${userId}/ratings?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// Mentee-only rating (#278 / 1.1.1.1.11). Backend rejects with 409 if the
// mentorship is still ACTIVE or already rated; 403 if a mentor calls it.
// Score must be 1..5; comment is optional and capped at 1000 chars.
export async function rateMentor(id, score, comment) {
  const body = { score }
  if (comment) body.comment = comment
  const res = await fetch(`${BASE_URL}/mentorships/${id}/rating`, {
    method: 'POST',
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

export async function createFeedPost({ body, hashtags = [], attachmentIds = [] }) {
  const payload = { body, hashtags }
  if (attachmentIds.length > 0) payload.attachmentIds = attachmentIds
  const res = await fetch(`${BASE_URL}/feed/posts`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify(payload),
  })
  return handleResponse(res)
}

export async function getFeedPostById(id) {
  const res = await fetch(`${BASE_URL}/feed/posts/${id}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function updateFeedPost(id, { body, hashtags, attachmentIds }) {
  const payload = {}
  if (body !== undefined) payload.body = body
  if (hashtags !== undefined) payload.hashtags = hashtags
  // attachmentIds: omit to leave attachments untouched; pass [] to clear them;
  // pass an array to replace the post's attachment list (backend semantics).
  if (attachmentIds !== undefined) payload.attachmentIds = attachmentIds
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

// #128 / #358 / #411 / backend #135: submit a polymorphic report.
// targetType ∈ {POST, MENTORSHIP, USER}; the backend rejects self-reports
// (USER target with reporter_id == target_id), non-participant mentorship
// reports (403), and duplicate active reports (409 — same reporter +
// same target while still-open).
export async function submitReport({ targetType, targetId, problemType, description }) {
  const res = await fetch(`${BASE_URL}/reports`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify({ targetType, targetId, problemType, description }),
  })
  return handleResponse(res)
}

// #356 / backend #349: feed read-state cursor + companion unread count.
// `markFeedRead` is idempotent — backend sets the cursor to clock_timestamp.
// `getFeedUnreadCount` returns { count, cappedAtMax } capped at 99 by default.
export async function markFeedRead() {
  const res = await fetch(`${BASE_URL}/feed/mark-read`, {
    method: 'POST',
    headers: authHeaders(),
  })
  if (res.status === 204) return null
  return handleResponse(res)
}

export async function getFeedUnreadCount() {
  const res = await fetch(`${BASE_URL}/feed/unread-count`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// #448 / backend #320: taxonomy autocomplete endpoints. Each returns a
// list of { label, identifierUri } pairs from a canonical source — ESCO
// for skills, ISCED-F for fields of study, Wikidata for hobbies.
//
// The `lang` parameter is a BCP-47 short tag (e.g. "en", "tr") and falls
// back to the request's Accept-Language server-side when omitted.
export async function searchTaxonomySkills(q, { lang, limit = 10 } = {}) {
  const params = new URLSearchParams({ q, limit: String(limit) })
  if (lang) params.set('lang', lang)
  const res = await fetch(`${BASE_URL}/taxonomies/skills?${params}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function searchTaxonomyFields(q, { lang, limit = 10 } = {}) {
  const params = new URLSearchParams({ q, limit: String(limit) })
  if (lang) params.set('lang', lang)
  const res = await fetch(`${BASE_URL}/taxonomies/fields?${params}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function searchTaxonomyHobbies(q, { lang, limit = 10 } = {}) {
  const params = new URLSearchParams({ q, limit: String(limit) })
  if (lang) params.set('lang', lang)
  const res = await fetch(`${BASE_URL}/taxonomies/hobbies?${params}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// #542 / backend #484: repost or quote-share a feed post. body is optional —
// null/blank produces a bare repost, non-blank (up to 2000 chars) attaches
// commentary. Backend dedupes repeated payloads within ~60s. Returns the
// updated FeedPostInteractionState for the original post so the share count
// can refresh in place.
export async function repostPost(postId, body) {
  const payload = body && body.trim() ? { body: body.trim() } : {}
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/reposts`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify(payload),
  })
  return handleResponse(res)
}

// #544 / backend #487: restore a soft-deleted post within the 30-day window.
// 410 means the window expired; surfaced as a regular Error from handleResponse.
export async function restoreFeedPost(id) {
  const res = await fetch(`${BASE_URL}/feed/posts/${id}/restore`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// #544 / backend #487: edit-history entries for a post, newest-first.
// Author or admin only; non-author callers get 403.
export async function getFeedPostHistory(id, limit = 50) {
  const res = await fetch(`${BASE_URL}/feed/posts/${id}/history?limit=${limit}`, {
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

// #543 / backend #486: feed search accepts q + hashtag + since + until + lang.
// since is inclusive, until is exclusive (both ISO 8601). lang is a BCP-47
// short tag (e.g. "en", "tr-TR"). Backend requires at least one filter and
// returns 400 if all are null; the UI gates the request when needed.
export async function searchFeed({ q, hashtag, since, until, lang, page = 0, size = 20 }) {
  const params = new URLSearchParams()
  if (q) params.set('q', q)
  if (hashtag) params.set('hashtag', hashtag)
  if (since) params.set('since', since)
  if (until) params.set('until', until)
  if (lang) params.set('lang', lang)
  params.set('page', String(page))
  params.set('size', String(size))
  const res = await fetch(`${BASE_URL}/feed/search?${params.toString()}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

// Per-user keyword mutes (#543 / backend #486). Server lowercases + validates
// charset on add. Add returns 201; remove returns 204. Mutes are applied
// transparently by every feed read path on the server side.
export async function getMutedKeywords() {
  const res = await fetch(`${BASE_URL}/users/me/keyword-mutes`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function addMutedKeyword(keyword) {
  const res = await fetch(`${BASE_URL}/users/me/keyword-mutes`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify({ keyword }),
  })
  return handleResponse(res)
}

export async function removeMutedKeyword(id) {
  const res = await fetch(`${BASE_URL}/users/me/keyword-mutes/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  if (res.status === 204) return null
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

export async function toggleLikeOnPost(postId) {
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/like`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function getPostComments(postId, page = 0, size = 20) {
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/comments?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  return handleResponse(res)
}

export async function addCommentToPost(postId, body) {
  const res = await fetch(`${BASE_URL}/feed/posts/${postId}/comments`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify({ body }),
  })
  return handleResponse(res)
}

export async function toggleLikeOnComment(commentId) {
  const res = await fetch(`${BASE_URL}/feed/comments/${commentId}/like`, {
    method: 'POST',
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

// Admin-only direct-message endpoint. Backend route: POST
// /api/admin/messages/direct/{userId} accepting a SendMessageRequest with a
// `content` body. The 403 path is handled by the shared handleResponse
// interceptor (BANNED_UNTIL events) — admins ban-immune, but the same shape.
export async function sendAdminDirectMessage(userId, content) {
  const res = await fetch(`${BASE_URL}/admin/messages/direct/${userId}`, {
    method: 'POST',
    headers: authJsonHeaders(),
    body: JSON.stringify({ content }),
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
