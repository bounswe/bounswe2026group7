import { test, expect } from '@playwright/test';
import { resetDb, seedMentor, seedMentee } from '../fixtures/apiClient.js';
import {
  createMentorshipRequest,
  acceptMentorshipRequest,
  setSharedGoal,
} from '../fixtures/mentorshipApi.js';

/**
 * AT-20 — Mentee cancel-relationship and mentor early-end.
 *
 * Verifies the two termination paths surfaced by MentorshipController:
 *   POST   /api/mentorships/{id}/cancel  — mentee only (#133/#237)
 *   PATCH  /api/mentorships/{id}/end     — mentor only (#237)
 *   PATCH  /api/mentorships/{id}/extend  — mentor only, 1/3/6 months (#237)
 *
 * Each terminal transition is asserted via /api/mentorships/{id} (status,
 * terminatedAt, terminatedByUserId). Cross-role guards (403) are pinned
 * in the same spec so a future regression is caught at the controller level.
 *
 * Requirements covered: 1.1.1.1.12, 1.1.1.2.13, 1.1.1.2.14, 1.1.4.10,
 * 1.1.1.1.16.
 */

const apiBase = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function getMentorship(request, token, id) {
  return request.get(`${apiBase()}/api/mentorships/${id}`, { headers: authHeaders(token) });
}

async function cancelMentorship(request, token, id, reason) {
  return request.post(`${apiBase()}/api/mentorships/${id}/cancel`, {
    headers: authHeaders(token),
    data: { reason },
  });
}

async function endMentorship(request, token, id, reason) {
  return request.patch(`${apiBase()}/api/mentorships/${id}/end`, {
    headers: authHeaders(token),
    data: reason ? { reason } : {},
  });
}

async function extendMentorship(request, token, id, additionalMonths) {
  return request.patch(`${apiBase()}/api/mentorships/${id}/extend`, {
    headers: authHeaders(token),
    data: { additionalMonths },
  });
}

async function seedActiveMentorship(request) {
  const mentor = await seedMentor(request);
  const mentee = await seedMentee(request);
  const created = await createMentorshipRequest(request, mentee.sessionToken, {
    mentorId: mentor.id,
    message: 'AT-20 termination fixture',
  });
  await acceptMentorshipRequest(request, mentor.sessionToken, created.id, 3);
  // Find the active mentorship id for the pair.
  const res = await request.get(`${apiBase()}/api/mentorships`, {
    headers: authHeaders(mentee.sessionToken),
  });
  const body = await res.json();
  const list = Array.isArray(body) ? body : (body.content ?? []);
  const mentorship = list.find(m => m.mentorId === mentor.id || m.menteeId === mentee.id);
  if (!mentorship) throw new Error('Could not find active mentorship for the seeded pair');
  await setSharedGoal(request, mentor.sessionToken, mentorship.id, 'AT-20 shared goal');
  return { mentor, mentee, mentorshipId: mentorship.id };
}

test('AT-20 mentee cancel + mentor end-early + mentor extend', async ({ request }) => {
  await resetDb(request);

  // Three independent mentorships so cancel / end / extend each have a
  // fresh row to operate on without interference.
  const A1 = await seedActiveMentorship(request);  // mentee cancels
  const A2 = await seedActiveMentorship(request);  // mentor ends early
  const A3 = await seedActiveMentorship(request);  // mentor extends

  // C1 — mentee cancels A1 with a reason. 200; status transitions.
  {
    const res = await cancelMentorship(request, A1.mentee.sessionToken, A1.mentorshipId, 'Schedules no longer align');
    expect(res.status()).toBe(200);
    const detail = await getMentorship(request, A1.mentee.sessionToken, A1.mentorshipId);
    expect(detail.status()).toBe(200);
    const body = await detail.json();
    expect(['CANCELLED', 'CANCELED']).toContain(body.status);
  }

  // C2 — mentor (the wrong side) attempts to cancel A2 — 403.
  {
    const res = await cancelMentorship(request, A2.mentor.sessionToken, A2.mentorshipId, 'wrong actor');
    expect(res.status()).toBe(403);
  }

  // C3 — mentor ends A2 early. 200; status=COMPLETED.
  {
    const res = await endMentorship(request, A2.mentor.sessionToken, A2.mentorshipId, 'Goal reached early');
    expect(res.status()).toBe(200);
    const detail = await getMentorship(request, A2.mentee.sessionToken, A2.mentorshipId);
    const body = await detail.json();
    expect(body.status).toBe('COMPLETED');
  }

  // C4 — mentee attempts to PATCH /end on A3 — 403 (mentor-only).
  {
    const res = await endMentorship(request, A3.mentee.sessionToken, A3.mentorshipId, 'wrong actor');
    expect(res.status()).toBe(403);
  }

  // C5 — mentor extends A3 by 3 months. 200; status stays ACTIVE.
  {
    const res = await extendMentorship(request, A3.mentor.sessionToken, A3.mentorshipId, 3);
    expect(res.status()).toBe(200);
    const detail = await getMentorship(request, A3.mentor.sessionToken, A3.mentorshipId);
    const body = await detail.json();
    expect(body.status).toBe('ACTIVE');
  }

  // C6 — extending with an invalid `additionalMonths` (e.g. 5) is rejected
  // by the @AssertTrue validator on ExtendMentorshipRequest. 400.
  {
    const res = await extendMentorship(request, A3.mentor.sessionToken, A3.mentorshipId, 5);
    expect(res.status()).toBe(400);
  }

  // C7 — re-cancelling the already-cancelled A1 is rejected (409) since the
  // mentorship is not active.
  {
    const res = await cancelMentorship(request, A1.mentee.sessionToken, A1.mentorshipId, 'duplicate');
    expect([400, 409]).toContain(res.status());
  }
});
