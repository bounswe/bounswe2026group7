/**
 * Backend API helpers for AT-02 legs whose UI doesn't exist yet (#316 notes
 * that SchedulePage/TasksPage are read-only mocks and there's no blog publish
 * page). These call the real backend endpoints with a bearer token so the
 * integration is genuine end-to-end — only the UI step is missing.
 *
 * Each helper accepts a Playwright `request` (APIRequestContext) and a JWT.
 */

const apiBase = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function expectOk(res, label) {
  if (!res.ok()) {
    const body = await res.text().catch(() => '');
    throw new Error(`${label} -> ${res.status()} ${body}`);
  }
  return res.json();
}

/** POST /api/mentorship-requests — mentee creates pending request. */
export async function createMentorshipRequest(request, token, body) {
  const res = await request.post(`${apiBase()}/api/mentorship-requests`, {
    headers: authHeaders(token),
    data: body,
  });
  return expectOk(res, 'POST /api/mentorship-requests');
}

/** PUT /api/mentorship-requests/{id}/accept — mentor accepts with duration. */
export async function acceptMentorshipRequest(request, token, requestId, durationMonths) {
  const res = await request.put(
    `${apiBase()}/api/mentorship-requests/${requestId}/accept`,
    { headers: authHeaders(token), data: { duration: durationMonths } },
  );
  return expectOk(res, `PUT /api/mentorship-requests/${requestId}/accept`);
}

/**
 * PUT /api/mentorships/{id}/goal — set the shared goal on an active mentorship.
 *
 * #335 added a precondition: meetings/tasks/milestones now refuse to be
 * created until the mentorship has a non-blank `sharedGoal`, returning
 * `409 GOAL_REQUIRED`. Specs that exercise the downstream verbs must
 * call this once after accept.
 */
export async function setSharedGoal(request, token, mentorshipId, sharedGoal) {
  const res = await request.put(`${apiBase()}/api/mentorships/${mentorshipId}/goal`, {
    headers: authHeaders(token),
    data: { sharedGoal },
  });
  return expectOk(res, `PUT /api/mentorships/${mentorshipId}/goal`);
}

/**
 * POST /api/mentorships/{id}/messages — send a chat message via the REST API.
 *
 * Used by AT-06 to seed a "warmup" message before opening the UI threads:
 * the frontend's `MessagesPage` only learns the conversation id from
 * `messages[0].conversationId`, so an empty thread leaves both sides
 * un-subscribed to STOMP and live updates never arrive. Sending one
 * warmup message before mounting the pages unblocks the subscription.
 */
export async function sendMessage(request, token, mentorshipId, content) {
  const res = await request.post(`${apiBase()}/api/mentorships/${mentorshipId}/messages`, {
    headers: authHeaders(token),
    data: { content },
  });
  return expectOk(res, `POST /api/mentorships/${mentorshipId}/messages`);
}

/**
 * POST /api/mentorships/{id}/meetings — mentor only.
 *
 * Accepts the test-ergonomic shape `{ title, description?, date,
 * durationMin?, meetingType?, meetingLink? }` and translates to the
 * backend's `MeetingCreateRequest` (`title`, `startTime`, `endTime`,
 * `meetingType`, `meetingLink`). `startTime`/`endTime` may also be passed
 * directly. ONLINE meetings (the default) require a meetingLink, so we
 * default to a placeholder URL — tests don't actually open the link.
 *
 * Backend returns `MeetingCreateResponse { meetings: [...], warnings: [...] }`;
 * callers want a single meeting, so we unwrap `meetings[0]`.
 */
export async function createMeeting(request, token, mentorshipId, body) {
  const {
    title,
    description,
    date,
    startTime,
    endTime,
    durationMin = 30,
    meetingType = 'ONLINE',
    meetingLink = 'https://meet.example.com/e2e',
    ...rest
  } = body;
  const start = startTime ?? date;
  const computedEnd =
    endTime ?? new Date(new Date(start).getTime() + durationMin * 60_000).toISOString();
  const payload = {
    title,
    description,
    startTime: start,
    endTime: computedEnd,
    meetingType,
    ...(meetingType === 'ONLINE' ? { meetingLink } : {}),
    ...rest,
  };
  const res = await request.post(`${apiBase()}/api/mentorships/${mentorshipId}/meetings`, {
    headers: authHeaders(token),
    data: payload,
  });
  const responseBody = await expectOk(res, `POST /api/mentorships/${mentorshipId}/meetings`);
  return responseBody?.meetings?.[0] ?? responseBody;
}

/**
 * PUT /api/mentorships/{id}/goal — set shared goal. Required before any
 * meeting / task / milestone write since #335 added the precondition gate.
 * Either mentor or mentee can set it.
 */
export async function setSharedGoal(request, token, mentorshipId, sharedGoal) {
  const res = await request.put(`${apiBase()}/api/mentorships/${mentorshipId}/goal`, {
    headers: authHeaders(token),
    data: { sharedGoal },
  });
  return expectOk(res, `PUT /api/mentorships/${mentorshipId}/goal`);
}

/** POST /api/meetings/{id}/confirm — mentee accepts the proposed slot. */
export async function confirmMeeting(request, token, meetingId) {
  const res = await request.post(`${apiBase()}/api/meetings/${meetingId}/confirm`, {
    headers: authHeaders(token),
  });
  return expectOk(res, `POST /api/meetings/${meetingId}/confirm`);
}

/** POST /api/mentorships/{id}/tasks — mentor assigns. */
export async function createTask(request, token, mentorshipId, body) {
  const res = await request.post(`${apiBase()}/api/mentorships/${mentorshipId}/tasks`, {
    headers: authHeaders(token),
    data: body,
  });
  return expectOk(res, `POST /api/mentorships/${mentorshipId}/tasks`);
}

/** POST /api/tasks/{id}/submission — mentee submits. */
export async function submitTask(request, token, taskId, body) {
  const res = await request.post(`${apiBase()}/api/tasks/${taskId}/submission`, {
    headers: authHeaders(token),
    data: body,
  });
  return expectOk(res, `POST /api/tasks/${taskId}/submission`);
}

/** PATCH /api/tasks/{id}/feedback — mentor reviews. */
export async function reviewTask(request, token, taskId, body) {
  const res = await request.patch(`${apiBase()}/api/tasks/${taskId}/feedback`, {
    headers: authHeaders(token),
    data: body,
  });
  return expectOk(res, `PATCH /api/tasks/${taskId}/feedback`);
}

/**
 * POST /api/feed/posts — used as the "blog publish" leg of AT-02. The product
 * has no dedicated blog feature; the social feed post is the closest thing,
 * and the issue's "blog publish" deliverable is satisfied by exercising this
 * controller end-to-end.
 */
export async function publishBlogPost(request, token, body) {
  const res = await request.post(`${apiBase()}/api/feed/posts`, {
    headers: authHeaders(token),
    data: body,
  });
  return expectOk(res, 'POST /api/feed/posts');
}

/** GET /api/feed/posts/{id} for verification. */
export async function getBlogPost(request, token, postId) {
  const res = await request.get(`${apiBase()}/api/feed/posts/${postId}`, {
    headers: authHeaders(token),
  });
  return expectOk(res, `GET /api/feed/posts/${postId}`);
}

/**
 * GET /api/mentorship-requests received by the authenticated mentor. AT-02
 * uses this to look up the request id created by the mentee's UI submit, so
 * the spec can later assert state transitions on it.
 *
 * The endpoint returns a Spring Data Page wrapper (`{ content: [...],
 * totalElements, ... }`), so unwrap `.content` here. Treat a missing
 * `content` field defensively as an empty list.
 */
export async function listIncomingRequests(request, token) {
  const res = await request.get(
    `${apiBase()}/api/mentorship-requests/received?page=0&size=50`,
    { headers: authHeaders(token) },
  );
  const body = await expectOk(res, 'GET /api/mentorship-requests/received');
  return Array.isArray(body) ? body : (body?.content ?? []);
}

/**
 * GET /api/mentorships returns the authenticated user's active mentorships
 * (mentor + mentee sides combined). Used after accept to discover the new
 * mentorship id.
 */
export async function listActiveMentorships(request, token) {
  const res = await request.get(`${apiBase()}/api/mentorships`, {
    headers: authHeaders(token),
  });
  return expectOk(res, 'GET /api/mentorships');
}

/** GET /api/notifications — Spring returns a List, not a Page wrapper. */
export async function listNotifications(request, token) {
  const res = await request.get(`${apiBase()}/api/notifications`, {
    headers: authHeaders(token),
  });
  return expectOk(res, 'GET /api/notifications');
}

/**
 * Fires the meeting-reminder scheduler manually so AT-06 (#317) doesn't have
 * to wait for the production cron (every 5 min). Backend route is
 * registered only when app.test-endpoints.enabled=true.
 */
export async function triggerMeetingReminders(request) {
  const res = await request.post(`${apiBase()}/api/test/trigger-meeting-reminders`);
  return expectOk(res, 'POST /api/test/trigger-meeting-reminders');
}

/**
 * Wipes the in-memory rate-limit bucket cache so AT-07's 11-login probe
 * doesn't bleed into other specs that also hit /api/auth/login.
 */
export async function resetRateLimits(request) {
  const res = await request.post(`${apiBase()}/api/test/reset-ratelimits`);
  if (!res.ok()) {
    const body = await res.text().catch(() => '');
    throw new Error(`POST /api/test/reset-ratelimits -> ${res.status()} ${body}`);
  }
}
