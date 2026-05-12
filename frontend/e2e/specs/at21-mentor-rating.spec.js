import { test, expect } from '@playwright/test';
import { resetDb, seedMentor, seedMentee } from '../fixtures/apiClient.js';
import {
  createMentorshipRequest,
  acceptMentorshipRequest,
  setSharedGoal,
} from '../fixtures/mentorshipApi.js';

/**
 * AT-21 — Mentor rating after the mentorship terminates.
 *
 * Verifies #237 / #518 contract on MentorshipController:
 *   POST /api/mentorships/{id}/rating   {score: 1-5, comment?: <=1000}
 *   GET  /api/mentorships/{id}/rating
 *   GET  /api/users/{mentorId}  -> averageRating / ratingCount
 *
 * Rules under test:
 *   - rating is mentee-only (mentor attempt -> 403)
 *   - mentorship must be terminated (ACTIVE -> 409)
 *   - one rating per mentorship (second POST -> 409)
 *   - mentor profile aggregate reflects the new entry
 *
 * Requirements covered: 1.1.1.1.11.
 */

const apiBase = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function postRating(request, token, mentorshipId, body) {
  return request.post(`${apiBase()}/api/mentorships/${mentorshipId}/rating`, {
    headers: authHeaders(token),
    data: body,
  });
}

async function getRating(request, token, mentorshipId) {
  return request.get(`${apiBase()}/api/mentorships/${mentorshipId}/rating`, {
    headers: authHeaders(token),
  });
}

async function getMentorPublicProfile(request, token, mentorId) {
  return request.get(`${apiBase()}/api/users/${mentorId}`, { headers: authHeaders(token) });
}

async function endMentorship(request, token, mentorshipId) {
  return request.patch(`${apiBase()}/api/mentorships/${mentorshipId}/end`, {
    headers: authHeaders(token),
    data: { reason: 'AT-21 wrap-up' },
  });
}

async function seedActiveMentorship(request) {
  const mentor = await seedMentor(request);
  const mentee = await seedMentee(request);
  const created = await createMentorshipRequest(request, mentee.sessionToken, {
    mentorId: mentor.id,
    message: 'AT-21 rating fixture',
  });
  await acceptMentorshipRequest(request, mentor.sessionToken, created.id, 3);
  const res = await request.get(`${apiBase()}/api/mentorships`, {
    headers: authHeaders(mentee.sessionToken),
  });
  const body = await res.json();
  const list = Array.isArray(body) ? body : (body.content ?? []);
  const mentorship = list.find(m => m.mentorId === mentor.id || m.menteeId === mentee.id);
  if (!mentorship) throw new Error('No active mentorship found for seeded pair');
  await setSharedGoal(request, mentor.sessionToken, mentorship.id, 'AT-21 shared goal');
  return { mentor, mentee, mentorshipId: mentorship.id };
}

test('AT-21 mentor rating after mentorship terminates', async ({ request }) => {
  await resetDb(request);

  const M = await seedActiveMentorship(request);

  // C1 — Active mentorship: mentee attempts to rate. Backend rejects with 409
  // because the mentorship has not terminated yet.
  {
    const res = await postRating(request, M.mentee.sessionToken, M.mentorshipId, {
      score: 5,
      comment: 'too early',
    });
    expect(res.status()).toBe(409);
  }

  // C2 — Mentor ends the mentorship gracefully.
  {
    const res = await endMentorship(request, M.mentor.sessionToken, M.mentorshipId);
    expect(res.status()).toBe(200);
  }

  // C3 — Mentor attempts to rate themselves — 403 (mentee-only).
  {
    const res = await postRating(request, M.mentor.sessionToken, M.mentorshipId, {
      score: 5,
      comment: 'self-rating',
    });
    expect(res.status()).toBe(403);
  }

  // C4 — Mentee posts the rating. 201; response carries the score + comment.
  {
    const res = await postRating(request, M.mentee.sessionToken, M.mentorshipId, {
      score: 5,
      comment: 'outstanding',
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.score).toBe(5);
    expect(body.comment).toContain('outstanding');
  }

  // C5 — Re-submit by the same mentee — 409 (one rating per mentorship).
  {
    const res = await postRating(request, M.mentee.sessionToken, M.mentorshipId, {
      score: 4,
      comment: 'second attempt',
    });
    expect(res.status()).toBe(409);
  }

  // C6 — GET reflects the same row.
  {
    const res = await getRating(request, M.mentee.sessionToken, M.mentorshipId);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.score).toBe(5);
  }

  // C7 — Mentor public profile aggregate now reflects the rating.
  {
    const res = await getMentorPublicProfile(request, M.mentee.sessionToken, M.mentor.id);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.averageRating).toBe(5);
    expect(body.ratingCount).toBeGreaterThanOrEqual(1);
  }

  // C8 — Out-of-range score is validated at the controller. 400.
  {
    // Need a second terminated mentorship for a fresh attempt; reuse the same
    // ratable pair via direct call on the original id is blocked by C5's 409.
    const M2 = await seedActiveMentorship(request);
    await endMentorship(request, M2.mentor.sessionToken, M2.mentorshipId);
    const res = await postRating(request, M2.mentee.sessionToken, M2.mentorshipId, {
      score: 10,
      comment: 'out of range',
    });
    expect(res.status()).toBe(400);
  }
});
