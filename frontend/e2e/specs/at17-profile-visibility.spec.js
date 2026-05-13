import { test, expect } from '@playwright/test';
import { resetDb, seedMentor, seedMentee, seedAdmin } from '../fixtures/apiClient.js';

/**
 * AT-17 — server-side profile-visibility enforcement.
 *
 * Verifies #570 + #589 contract:
 *  - 1.1.2.5: mentor viewing a mentee gets a null lastName + null
 *    profilePhoto on the response (the mask).
 *  - 1.2.2.3: a mentee/mentor who flips profileVisibility=false is
 *    inaccessible to non-owner / non-admin viewers (`GET /api/users/{id}`
 *    returns 403 with "Profile is private") AND is filtered out of every
 *    mentor/mentee list endpoint that backs explore + matching.
 *
 * The whole flow is API-only; no UI piece is exercised here because the
 * visibility-toggle UI is partial today and the server-side gate is the
 * contract being protected by this test.
 */

const apiBase = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function getProfile(request, token, targetId) {
  return request.get(`${apiBase()}/api/users/${targetId}`, { headers: authHeaders(token) });
}

async function patchMenteeVisibility(request, token, visible) {
  return request.patch(`${apiBase()}/api/users/me/mentee`, {
    headers: authHeaders(token),
    data: { profileVisibility: visible },
  });
}

async function patchMentorVisibility(request, token, visible) {
  return request.patch(`${apiBase()}/api/users/me/mentor`, {
    headers: authHeaders(token),
    data: { profileVisibility: visible },
  });
}

async function listMentors(request, token) {
  const res = await request.get(`${apiBase()}/api/users/mentors`, { headers: authHeaders(token) });
  if (!res.ok()) {
    throw new Error(`GET /api/users/mentors -> ${res.status()}`);
  }
  const body = await res.json();
  // Spring Data Page or List depending on the route; normalise to ids.
  const content = Array.isArray(body) ? body : (body.content ?? []);
  return content.map(item => item.id);
}

async function listMatchingMentors(request, token) {
  const res = await request.get(`${apiBase()}/api/matching/mentors`, { headers: authHeaders(token) });
  if (!res.ok()) {
    // /api/matching/mentors may return 403 for an admin (mentee-only gate);
    // callers handle that, return null to signal "not applicable here".
    return null;
  }
  const body = await res.json();
  const content = Array.isArray(body) ? body : (body.content ?? []);
  return content.map(item => item.id);
}

test('AT-17 server-side profile visibility + 1.1.2.5 mask', async ({ request }) => {
  await resetDb(request);

  const m1 = await seedMentor(request);
  const m2 = await seedMentor(request);
  const e1 = await seedMentee(request);
  const e2 = await seedMentee(request);
  const admin = await seedAdmin(request);

  // C1 — Default visibility: M1 views E1 (mentee). 200 with lastName + photo
  // masked (1.1.2.5).
  {
    const res = await getProfile(request, m1.sessionToken, e1.id);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.firstName).toBeTruthy();
    expect(body.lastName).toBeNull();
    expect(body.profilePhoto).toBeNull();
  }

  // C2 — E1 flips its mentee profileVisibility to false.
  {
    const res = await patchMenteeVisibility(request, e1.sessionToken, false);
    expect(res.status()).toBe(200);
  }

  // C3 — M1 re-reads E1's profile. 403 "Profile is private".
  {
    const res = await getProfile(request, m1.sessionToken, e1.id);
    expect(res.status()).toBe(403);
  }

  // C4 — Owner self-view bypasses the gate.
  {
    const res = await getProfile(request, e1.sessionToken, e1.id);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.profileVisibility).toBe(false);
  }

  // C5 — M1 flips its mentor visibility to false; M2 (another mentor)
  // then gets 403.
  {
    const patchRes = await patchMentorVisibility(request, m1.sessionToken, false);
    expect(patchRes.status()).toBe(200);
    const viewRes = await getProfile(request, m2.sessionToken, m1.id);
    expect(viewRes.status()).toBe(403);
  }

  // C6 — Private M1 is absent from E2's mentor list + matching results.
  {
    const mentorIds = await listMentors(request, e2.sessionToken);
    expect(mentorIds).toContain(m2.id);
    expect(mentorIds).not.toContain(m1.id);

    const matchIds = await listMatchingMentors(request, e2.sessionToken);
    expect(matchIds).not.toBeNull();
    expect(matchIds).not.toContain(m1.id);
  }

  // C7 — Admin bypass: the same call from the admin token still returns the
  // private mentor.
  {
    const mentorIds = await listMentors(request, admin.sessionToken);
    expect(mentorIds).toContain(m1.id);
    expect(mentorIds).toContain(m2.id);
  }
});
