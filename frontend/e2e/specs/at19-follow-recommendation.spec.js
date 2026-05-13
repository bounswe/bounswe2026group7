import { test, expect } from '@playwright/test';
import { resetDb, seedMentor, seedMentee } from '../fixtures/apiClient.js';

/**
 * AT-19 — Follow graph + follow-recommendation surface.
 *
 * Verifies the social-feed follow graph for mentee + mentor, the Following
 * tab data source, and the follow-recommendation endpoint exposed by #344.
 *
 * Requirements covered: 1.1.1.1.14, 1.1.1.1.15, 1.1.1.2.15, 1.1.1.2.16,
 * 1.1.2.8, 1.1.7.4.
 */

const apiBase = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function follow(request, token, targetId) {
  return request.post(`${apiBase()}/api/users/${targetId}/follow`, { headers: authHeaders(token) });
}

async function unfollow(request, token, targetId) {
  return request.delete(`${apiBase()}/api/users/${targetId}/follow`, { headers: authHeaders(token) });
}

async function followers(request, token, targetId) {
  const res = await request.get(`${apiBase()}/api/users/${targetId}/followers`, {
    headers: authHeaders(token),
  });
  if (!res.ok()) throw new Error(`GET followers -> ${res.status()}`);
  const body = await res.json();
  return Array.isArray(body) ? body : (body.content ?? []);
}

async function publishFeedPost(request, token, body) {
  const res = await request.post(`${apiBase()}/api/feed/posts`, {
    headers: authHeaders(token),
    data: { body, hashtags: [] },
  });
  if (!res.ok()) throw new Error(`POST /api/feed/posts -> ${res.status()}`);
  return res.json();
}

async function followingFeed(request, token) {
  const res = await request.get(`${apiBase()}/api/feed/following`, { headers: authHeaders(token) });
  if (!res.ok()) throw new Error(`GET /api/feed/following -> ${res.status()}`);
  const body = await res.json();
  return Array.isArray(body) ? body : (body.content ?? []);
}

async function followRecommendations(request, token) {
  const res = await request.get(`${apiBase()}/api/users/me/follow-recommendations`, {
    headers: authHeaders(token),
  });
  if (!res.ok()) throw new Error(`GET follow-recommendations -> ${res.status()}`);
  const body = await res.json();
  return Array.isArray(body) ? body : (body.content ?? []);
}

test('AT-19 follow + unfollow + Following feed + follow-recommendations', async ({ request }) => {
  await resetDb(request);

  const mentee = await seedMentee(request);
  const mentorA = await seedMentor(request);
  const mentorB = await seedMentor(request);

  // C1 — mentee follows mentorA. 201; mentorA's follower count includes mentee.
  {
    const res = await follow(request, mentee.sessionToken, mentorA.id);
    expect([200, 201]).toContain(res.status());
    const list = await followers(request, mentorA.sessionToken, mentorA.id);
    expect(list.some(u => u.id === mentee.id)).toBe(true);
  }

  // C2 — mentee's Following feed surfaces a post by mentorA.
  {
    const post = await publishFeedPost(request, mentorA.sessionToken, 'AT-19 follow feed seed post');
    const feed = await followingFeed(request, mentee.sessionToken);
    expect(feed.some(item => item.id === post.id || item.postId === post.id)).toBe(true);
  }

  // C3 — mentee unfollows mentorA. 200; mentorA no longer in following list.
  {
    const res = await unfollow(request, mentee.sessionToken, mentorA.id);
    expect([200, 204]).toContain(res.status());
    const list = await followers(request, mentorA.sessionToken, mentorA.id);
    expect(list.some(u => u.id === mentee.id)).toBe(false);
  }

  // C4 — mentor-to-mentor follow. mentorB follows mentorA.
  {
    const res = await follow(request, mentorB.sessionToken, mentorA.id);
    expect([200, 201]).toContain(res.status());
  }

  // C5 — and mentorB unfollows.
  {
    const res = await unfollow(request, mentorB.sessionToken, mentorA.id);
    expect([200, 204]).toContain(res.status());
  }

  // C6 — mentee opens follow-recommendations. The endpoint returns 200 with
  // a list; the mentee themselves must not appear and any user the mentee
  // currently follows must not appear. Each entry carries a `factors` array
  // per the #344 contract.
  {
    const recs = await followRecommendations(request, mentee.sessionToken);
    expect(Array.isArray(recs)).toBe(true);
    expect(recs.some(r => r.id === mentee.id)).toBe(false);
    // After unfollow in C3, mentorA may legitimately appear as a candidate.
    for (const r of recs) {
      expect(r).toHaveProperty('factors');
    }
  }
});
