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

/** POST /api/mentorships/{id}/meetings — mentor only. */
export async function createMeeting(request, token, mentorshipId, body) {
  const res = await request.post(`${apiBase()}/api/mentorships/${mentorshipId}/meetings`, {
    headers: authHeaders(token),
    data: body,
  });
  return expectOk(res, `POST /api/mentorships/${mentorshipId}/meetings`);
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
