import { test, expect } from '@playwright/test';
import { resetDb, seedMentor, seedMentee, seedAdmin } from '../fixtures/apiClient.js';

/**
 * AT-18 — Admin panel reads (paginated user list + per-user detail +
 * polymorphic report queue).
 *
 * Covers the read side of the admin surface shipped by:
 *  - #569: GET /api/admin/users with role / banStatus / q filters and
 *          GET /api/admin/users/{id} with banHistory + suspectedBot.
 *  - #568: GET /api/admin/reports queue (filtered by status).
 *
 * Requirements covered: 1.1.1.3.1, 1.1.1.3.2, 1.1.1.3.3.
 *
 * Class-level @PreAuthorize gates the entire AdminController; we re-verify
 * the non-admin -> 403 path on at least one route to lock it in.
 */

const apiBase = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function adminListUsers(request, token, params = {}) {
  const url = new URL(`${apiBase()}/api/admin/users`);
  for (const [k, v] of Object.entries(params)) {
    if (v != null && v !== '') url.searchParams.set(k, String(v));
  }
  return request.get(url.toString(), { headers: authHeaders(token) });
}

async function adminGetUser(request, token, id) {
  return request.get(`${apiBase()}/api/admin/users/${id}`, { headers: authHeaders(token) });
}

async function adminBanUser(request, token, userId, body) {
  return request.post(`${apiBase()}/api/admin/users/${userId}/ban`, {
    headers: authHeaders(token),
    data: body,
  });
}

async function adminListReports(request, token, status) {
  const url = new URL(`${apiBase()}/api/admin/reports`);
  if (status) url.searchParams.set('status', status);
  return request.get(url.toString(), { headers: authHeaders(token) });
}

async function fileReport(request, token, body) {
  return request.post(`${apiBase()}/api/reports`, {
    headers: authHeaders(token),
    data: body,
  });
}

test('AT-18 admin user list + detail + report queue', async ({ request }) => {
  await resetDb(request);

  // Seeded ladder: 1 admin (bootstrapper-provided), 2 mentors, 2 mentees.
  const admin = await seedAdmin(request);
  const m1 = await seedMentor(request);
  const m2 = await seedMentor(request);
  const e1 = await seedMentee(request);
  const e2 = await seedMentee(request);

  // C1 — non-admin hitting the queue endpoint gets 403.
  {
    const res = await adminListUsers(request, e1.sessionToken);
    expect(res.status()).toBe(403);
  }

  // C2 — admin default list returns all 5 users with the slim shape.
  {
    const res = await adminListUsers(request, admin.sessionToken);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.totalElements).toBeGreaterThanOrEqual(5);
    const sample = body.content[0];
    for (const k of ['id', 'firstName', 'lastName', 'email', 'role', 'banStatus', 'suspectedBot', 'createdAt']) {
      expect(sample).toHaveProperty(k);
    }
  }

  // C3 — role filter partitions the population correctly.
  {
    const r1 = await adminListUsers(request, admin.sessionToken, { role: 'MENTOR' });
    const r2 = await adminListUsers(request, admin.sessionToken, { role: 'MENTEE' });
    const r3 = await adminListUsers(request, admin.sessionToken, { role: 'ADMIN' });
    expect((await r1.json()).content.every(u => u.role === 'MENTOR')).toBe(true);
    expect((await r2.json()).content.every(u => u.role === 'MENTEE')).toBe(true);
    expect((await r3.json()).content.every(u => u.role === 'ADMIN')).toBe(true);
  }

  // C4 — banStatus filter. Initially no one is banned; ban M1 explicitly and
  // re-query so the active vs. none partition is provable.
  {
    const before = await adminListUsers(request, admin.sessionToken, { banStatus: 'ACTIVE' });
    expect((await before.json()).totalElements).toBe(0);

    const banRes = await adminBanUser(request, admin.sessionToken, m1.id, {
      reason: 'AT-18 fixture ban',
      durationHours: 24,
    });
    expect(banRes.status()).toBe(200);

    const after = await adminListUsers(request, admin.sessionToken, { banStatus: 'ACTIVE' });
    expect(after.status()).toBe(200);
    const afterBody = await after.json();
    expect(afterBody.totalElements).toBe(1);
    expect(afterBody.content[0].id).toBe(m1.id);

    const noneRes = await adminListUsers(request, admin.sessionToken, { banStatus: 'NONE' });
    const noneBody = await noneRes.json();
    expect(noneBody.content.every(u => u.id !== m1.id)).toBe(true);
  }

  // C5 — keyword filter — ILIKE across firstName / lastName / email.
  // Pick a fragment from M2's email so the match set is deterministic.
  {
    const fragment = m2.email.split('@')[0].slice(0, 6).toLowerCase();
    const res = await adminListUsers(request, admin.sessionToken, { q: fragment });
    const body = await res.json();
    expect(body.totalElements).toBeGreaterThanOrEqual(1);
    expect(body.content.some(u => u.id === m2.id)).toBe(true);
  }

  // C6 — per-user detail for the banned user surfaces ban history.
  {
    const res = await adminGetUser(request, admin.sessionToken, m1.id);
    expect(res.status()).toBe(200);
    const body = await res.json();
    // @JsonUnwrapped flattens the profile shape; assert the core fields.
    expect(body.id).toBe(m1.id);
    expect(body.role).toBe('MENTOR');
    expect(Array.isArray(body.banHistory)).toBe(true);
    expect(body.banHistory.length).toBeGreaterThanOrEqual(1);
    expect(body.banHistory[0].reason).toContain('AT-18 fixture ban');
    expect(body).toHaveProperty('suspectedBot');
  }

  // C7 — admin report queue. Seed a POST report from e1 against any feed post
  // by m2 so there is at least one entry — the queue and its denormalised
  // targetSummary should be returned.
  {
    // m2 publishes a post
    const postRes = await request.post(`${apiBase()}/api/feed/posts`, {
      headers: authHeaders(m2.sessionToken),
      data: { body: 'AT-18 fixture post', hashtags: [] },
    });
    expect(postRes.ok()).toBe(true);
    const post = await postRes.json();

    const reportRes = await fileReport(request, e1.sessionToken, {
      targetType: 'POST',
      targetId: post.id,
      problemType: 'INAPPROPRIATE_BEHAVIOR',
      description: 'AT-18 smoke report',
    });
    expect(reportRes.status()).toBe(201);

    const queueRes = await adminListReports(request, admin.sessionToken, 'OPEN');
    expect(queueRes.status()).toBe(200);
    const queue = await queueRes.json();
    const content = Array.isArray(queue) ? queue : (queue.content ?? []);
    expect(content.length).toBeGreaterThanOrEqual(1);
    const target = content.find(r => r.targetType === 'POST' && r.targetId === post.id);
    expect(target).toBeTruthy();
    expect(target.targetSummary).toBeTruthy();
    expect(target.reporterFirstName).toBeTruthy();
  }
});
