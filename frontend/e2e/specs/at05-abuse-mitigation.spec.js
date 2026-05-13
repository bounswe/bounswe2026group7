import { test, expect } from '@playwright/test';
import { resetDb, seedMentor, seedMentee, seedAdmin } from '../fixtures/apiClient.js';

/**
 * AT-05 — End-to-End Abuse Mitigation: polymorphic reporting, auto-ban,
 * and admin resolution.
 *
 * Rewrites the previous test.fixme'd AT-05 (which assumed Community
 * Moderator + auto keyword filter — features that are not in the
 * [Requirements](./Requirements) doc) to walk the realised abuse-
 * mitigation surface:
 *   - polymorphic reporting (#568) — USER / POST / MENTORSHIP targets,
 *     duplicate-active 409, self-report 400.
 *   - admin reports queue + state-machine PATCH (#568): OPEN →
 *     UNDER_REVIEW → RESOLVED/DISMISSED with terminal-state lock.
 *   - explicit admin ban (#553/#280): ban + unban via /api/admin/users
 *     and the banStatus filter on /api/admin/users.
 *
 * Cancellation-frequency auto-ban (2.2.4) and bulk-registration spam-bot
 * detection (2.2.5) are exercised separately in AT-07's NFR posture spec
 * because they need rate-limit bucket manipulation that doesn't compose
 * with this spec's report flow.
 *
 * Requirements covered: 1.1.1.1.13, 1.1.6.1, 1.1.6.2, 1.1.6.3, 1.1.6.4,
 * 1.1.1.3.1, 1.1.1.3.2, 1.1.1.3.3.
 */

const apiBase = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function fileReport(request, token, body) {
  return request.post(`${apiBase()}/api/reports`, {
    headers: authHeaders(token),
    data: body,
  });
}

async function listOwnReports(request, token) {
  const res = await request.get(`${apiBase()}/api/reports/me`, { headers: authHeaders(token) });
  if (!res.ok()) throw new Error(`GET /api/reports/me -> ${res.status()}`);
  const body = await res.json();
  return Array.isArray(body) ? body : (body.content ?? []);
}

async function adminListReports(request, token, status) {
  const url = new URL(`${apiBase()}/api/admin/reports`);
  if (status) url.searchParams.set('status', status);
  const res = await request.get(url.toString(), { headers: authHeaders(token) });
  if (!res.ok()) throw new Error(`GET /api/admin/reports -> ${res.status()}`);
  const body = await res.json();
  return Array.isArray(body) ? body : (body.content ?? []);
}

async function adminPatchReport(request, token, reportId, status) {
  return request.patch(`${apiBase()}/api/admin/reports/${reportId}`, {
    headers: authHeaders(token),
    data: { status },
  });
}

async function adminBanUser(request, token, userId, body) {
  return request.post(`${apiBase()}/api/admin/users/${userId}/ban`, {
    headers: authHeaders(token),
    data: body,
  });
}

async function adminUnbanUser(request, token, userId) {
  return request.post(`${apiBase()}/api/admin/users/${userId}/unban`, {
    headers: authHeaders(token),
  });
}

async function adminListUsers(request, token, params = {}) {
  const url = new URL(`${apiBase()}/api/admin/users`);
  for (const [k, v] of Object.entries(params)) {
    if (v != null && v !== '') url.searchParams.set(k, String(v));
  }
  const res = await request.get(url.toString(), { headers: authHeaders(token) });
  if (!res.ok()) throw new Error(`GET /api/admin/users -> ${res.status()}`);
  const body = await res.json();
  return Array.isArray(body) ? body : (body.content ?? []);
}

async function publishPost(request, token, body) {
  const res = await request.post(`${apiBase()}/api/feed/posts`, {
    headers: authHeaders(token),
    data: { body, hashtags: [] },
  });
  if (!res.ok()) throw new Error(`POST /api/feed/posts -> ${res.status()}`);
  return res.json();
}

test('AT-05 polymorphic reporting + admin resolution + ban round-trip', async ({ request }) => {
  await resetDb(request);

  const reporter = await seedMentee(request);
  const reportee = await seedMentor(request);
  const admin = await seedAdmin(request);

  // Reportee publishes a feed post so the POST-target report has a real
  // entity to point at.
  const post = await publishPost(request, reportee.sessionToken, 'AT-05 post-target subject');

  // C1 — USER report (1.1.1.1.13, 1.1.6.3). 201 OPEN.
  let userReportId;
  {
    const res = await fileReport(request, reporter.sessionToken, {
      targetType: 'USER',
      targetId: reportee.id,
      problemType: 'HARASSMENT',
      description: 'AT-05 harassment USER target',
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    userReportId = body.id;
    expect(body.status).toBe('OPEN');
    expect(body.targetType).toBe('USER');
  }

  // C2 — POST report (1.1.6.1). 201 OPEN.
  {
    const res = await fileReport(request, reporter.sessionToken, {
      targetType: 'POST',
      targetId: post.id,
      problemType: 'INAPPROPRIATE_BEHAVIOR',
      description: 'AT-05 inappropriate POST target',
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.targetType).toBe('POST');
  }

  // C3 — duplicate active USER report -> 409 from the partial unique index.
  {
    const res = await fileReport(request, reporter.sessionToken, {
      targetType: 'USER',
      targetId: reportee.id,
      problemType: 'SPAM',
      description: 'AT-05 duplicate',
    });
    expect(res.status()).toBe(409);
  }

  // C4 — self-report -> 400. The reporter aims at their own id.
  {
    const res = await fileReport(request, reporter.sessionToken, {
      targetType: 'USER',
      targetId: reporter.id,
      problemType: 'OTHER',
      description: 'AT-05 self-report',
    });
    expect(res.status()).toBe(400);
  }

  // C5 — /reports/me surfaces the caller's own reports.
  {
    const list = await listOwnReports(request, reporter.sessionToken);
    expect(list.some(r => r.id === userReportId)).toBe(true);
  }

  // C6 — Non-admin cannot reach /api/admin/reports.
  {
    const res = await request.get(`${apiBase()}/api/admin/reports`, {
      headers: authHeaders(reporter.sessionToken),
    });
    expect(res.status()).toBe(403);
  }

  // C7 — Admin queue lists the reports with denormalised targetSummary.
  {
    const list = await adminListReports(request, admin.sessionToken, 'OPEN');
    const target = list.find(r => r.id === userReportId);
    expect(target).toBeTruthy();
    expect(target.targetSummary).toBeTruthy();
    expect(target.reporterFirstName).toBeTruthy();
  }

  // C8 — Admin transitions OPEN -> UNDER_REVIEW -> RESOLVED.
  {
    const ur = await adminPatchReport(request, admin.sessionToken, userReportId, 'UNDER_REVIEW');
    expect(ur.status()).toBe(200);
    const resolved = await adminPatchReport(request, admin.sessionToken, userReportId, 'RESOLVED');
    expect(resolved.status()).toBe(200);
    const body = await resolved.json();
    expect(body.status).toBe('RESOLVED');
    expect(body.reviewedById).toBe(admin.id);
  }

  // C9 — Terminal-state lock — re-PATCH from RESOLVED is rejected.
  {
    const res = await adminPatchReport(request, admin.sessionToken, userReportId, 'UNDER_REVIEW');
    expect(res.status()).toBe(400);
  }

  // C10 — Admin explicit ban on the reportee (1.1.1.3.3). 200; banStatus
  // partition flips on /api/admin/users?banStatus=ACTIVE.
  {
    const banRes = await adminBanUser(request, admin.sessionToken, reportee.id, {
      reason: 'AT-05 explicit ban',
      durationHours: 24,
    });
    expect(banRes.status()).toBe(200);

    const banned = await adminListUsers(request, admin.sessionToken, { banStatus: 'ACTIVE' });
    expect(banned.some(u => u.id === reportee.id)).toBe(true);
  }

  // C11 — Admin unbans the user. 200; banStatus partition flips back.
  {
    const res = await adminUnbanUser(request, admin.sessionToken, reportee.id);
    expect(res.status()).toBe(200);
    const banned = await adminListUsers(request, admin.sessionToken, { banStatus: 'ACTIVE' });
    expect(banned.every(u => u.id !== reportee.id)).toBe(true);
  }
});
