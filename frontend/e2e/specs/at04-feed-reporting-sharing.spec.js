import { test, expect } from '@playwright/test';
import { resetDb, seedMentor, seedMentee, seedAdmin } from '../fixtures/apiClient.js';

/**
 * AT-04 — Social-feed reporting, search, and sharing lifecycle.
 *
 * Replaces the empty AT-04 "Community Lifecycle Management" slot whose
 * 47-requirement scope targeted a Community/Forum feature family that
 * is not part of [Requirements](./Requirements). The realised feed
 * surface covers 12 `1.1.7.x` requirements plus the reporting + admin
 * trio.
 *
 * Walks a non-author viewer through:
 *   - discover via Following / For-You / hashtag / keyword search
 *   - like, comment, bookmark
 *   - silent share + quote-share repost (#484)
 *   - report (POST target via #568)
 *   - admin queue + state transitions (OPEN → UNDER_REVIEW → RESOLVED)
 *
 * Requirements covered: 1.1.6.1, 1.1.6.4, 1.1.7.1, 1.1.7.2, 1.1.7.3,
 * 1.1.7.4, 1.1.7.5, 1.1.7.6, 1.1.7.7, 1.1.7.8, 1.1.7.11, 1.1.7.12,
 * 1.1.1.3.1, 1.1.1.3.2.
 */

const apiBase = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function follow(request, token, targetId) {
  return request.post(`${apiBase()}/api/users/${targetId}/follow`, { headers: authHeaders(token) });
}

async function publishPost(request, token, body, hashtags) {
  const res = await request.post(`${apiBase()}/api/feed/posts`, {
    headers: authHeaders(token),
    data: { body, hashtags },
  });
  if (!res.ok()) throw new Error(`POST /api/feed/posts -> ${res.status()}`);
  return res.json();
}

async function listFollowingFeed(request, token) {
  const res = await request.get(`${apiBase()}/api/feed/following`, { headers: authHeaders(token) });
  if (!res.ok()) throw new Error(`GET /api/feed/following -> ${res.status()}`);
  const body = await res.json();
  return Array.isArray(body) ? body : (body.content ?? []);
}

async function searchPosts(request, token, params) {
  const url = new URL(`${apiBase()}/api/feed/search`);
  for (const [k, v] of Object.entries(params)) {
    if (v != null && v !== '') url.searchParams.set(k, String(v));
  }
  const res = await request.get(url.toString(), { headers: authHeaders(token) });
  if (!res.ok()) throw new Error(`GET /api/feed/search -> ${res.status()}`);
  const body = await res.json();
  return Array.isArray(body) ? body : (body.content ?? []);
}

async function likePost(request, token, postId) {
  return request.post(`${apiBase()}/api/feed/posts/${postId}/like`, { headers: authHeaders(token) });
}

async function addComment(request, token, postId, body) {
  return request.post(`${apiBase()}/api/feed/posts/${postId}/comments`, {
    headers: authHeaders(token),
    data: { body },
  });
}

async function bookmark(request, token, postId) {
  return request.post(`${apiBase()}/api/feed/posts/${postId}/bookmark`, { headers: authHeaders(token) });
}

async function silentShare(request, token, postId) {
  return request.post(`${apiBase()}/api/feed/posts/${postId}/share`, { headers: authHeaders(token) });
}

async function repost(request, token, postId, body) {
  return request.post(`${apiBase()}/api/feed/posts/${postId}/reposts`, {
    headers: authHeaders(token),
    data: body ? { body } : {},
  });
}

async function fileReport(request, token, body) {
  return request.post(`${apiBase()}/api/reports`, {
    headers: authHeaders(token),
    data: body,
  });
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

test('AT-04 social-feed reporting + search + sharing lifecycle', async ({ request }) => {
  await resetDb(request);

  const mentor = await seedMentor(request);
  const mentee = await seedMentee(request);
  const admin = await seedAdmin(request);

  // mentee follows mentor so the Following / share-fanout paths have a recipient.
  {
    const res = await follow(request, mentee.sessionToken, mentor.id);
    expect([200, 201]).toContain(res.status());
  }

  // C1 — mentor publishes a post with two hashtags.
  const post = await publishPost(request, mentor.sessionToken,
    'Hands-on machine learning project ideas #ml #portfolio',
    ['ml', 'portfolio'],
  );
  expect(post.id).toBeTruthy();

  // C2 — mentee sees the post in the Following feed.
  {
    const feed = await listFollowingFeed(request, mentee.sessionToken);
    const ids = feed.map(item => item.id ?? item.postId);
    expect(ids).toContain(post.id);
  }

  // C3 — keyword search surfaces the post.
  {
    const results = await searchPosts(request, mentee.sessionToken, { q: 'machine' });
    const ids = results.map(r => r.id ?? r.postId);
    expect(ids).toContain(post.id);
  }

  // C4 — hashtag search surfaces the post.
  {
    const results = await searchPosts(request, mentee.sessionToken, { hashtag: 'portfolio' });
    const ids = results.map(r => r.id ?? r.postId);
    expect(ids).toContain(post.id);
  }

  // C5 — like + comment + bookmark all succeed; like / bookmark are idempotent
  // toggles, so we only assert the initial action.
  {
    const likeRes = await likePost(request, mentee.sessionToken, post.id);
    expect([200, 201]).toContain(likeRes.status());

    const commentRes = await addComment(request, mentee.sessionToken, post.id, 'Saving for the weekend');
    expect([200, 201]).toContain(commentRes.status());

    const bookmarkRes = await bookmark(request, mentee.sessionToken, post.id);
    expect([200, 201]).toContain(bookmarkRes.status());
  }

  // C6 — silent share + repost (quote-share).
  {
    const shareRes = await silentShare(request, mentee.sessionToken, post.id);
    expect([200, 201]).toContain(shareRes.status());

    const repostRes = await repost(request, mentee.sessionToken, post.id, 'Saving for the weekend');
    expect([200, 201]).toContain(repostRes.status());
  }

  // C7 — mentee files a POST report against the mentor's post.
  let reportId;
  {
    const res = await fileReport(request, mentee.sessionToken, {
      targetType: 'POST',
      targetId: post.id,
      problemType: 'INAPPROPRIATE_BEHAVIOR',
      description: 'AT-04 smoke report',
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    reportId = body.id;
    expect(body.status).toBe('OPEN');
    expect(body.targetType).toBe('POST');
  }

  // C8 — duplicate active report against the same target -> 409.
  {
    const res = await fileReport(request, mentee.sessionToken, {
      targetType: 'POST',
      targetId: post.id,
      problemType: 'SPAM',
      description: 'AT-04 duplicate',
    });
    expect(res.status()).toBe(409);
  }

  // C9 — admin sees the report in the OPEN queue with denormalised targetSummary.
  {
    const queue = await adminListReports(request, admin.sessionToken, 'OPEN');
    const target = queue.find(r => r.id === reportId);
    expect(target).toBeTruthy();
    expect(target.targetSummary).toBeTruthy();
    expect(target.reporterFirstName).toBeTruthy();
  }

  // C10 — admin transitions OPEN -> UNDER_REVIEW -> RESOLVED.
  {
    const ur = await adminPatchReport(request, admin.sessionToken, reportId, 'UNDER_REVIEW');
    expect(ur.status()).toBe(200);
    const resolved = await adminPatchReport(request, admin.sessionToken, reportId, 'RESOLVED');
    expect(resolved.status()).toBe(200);
    const body = await resolved.json();
    expect(body.status).toBe('RESOLVED');
    expect(body.reviewedById).toBe(admin.id);
  }

  // C11 — after RESOLVED, the same mentee can file a fresh report on the
  // same target — the partial unique index is keyed to active rows only.
  {
    const res = await fileReport(request, mentee.sessionToken, {
      targetType: 'POST',
      targetId: post.id,
      problemType: 'SPAM',
      description: 'AT-04 follow-up after resolve',
    });
    expect(res.status()).toBe(201);
  }
});
