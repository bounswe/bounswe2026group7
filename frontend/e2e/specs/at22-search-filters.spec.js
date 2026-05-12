import { test, expect } from '@playwright/test';
import { resetDb, seedMentor, seedMentee } from '../fixtures/apiClient.js';

/**
 * AT-22 — Advanced mentor search filter parameters.
 *
 * Verifies #571 contract for the three new optional filters on the mentor
 * search + matching endpoints:
 *   - availabilityDays  (Set<DayOfWeek>, OR semantics)
 *   - mentorshipDuration (Set<Integer>, IN list)
 *   - minMatchScore     (Integer, applied post-rank)
 *
 * Backward-compat: every parameter is `required = false`. Calls with no
 * parameter must behave identically to the pre-#571 surface.
 *
 * Requirements covered: 1.1.1.1.10, 1.1.2.1.
 */

const apiBase = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

async function patchMentorProfile(request, token, body) {
  const res = await request.patch(`${apiBase()}/api/users/me/mentor`, {
    headers: authHeaders(token),
    data: body,
  });
  if (!res.ok()) {
    throw new Error(`PATCH /api/users/me/mentor -> ${res.status()} ${await res.text()}`);
  }
}

async function addMentorSlot(request, token, dayOfWeek) {
  // Mentor weekly availability — defaults to a 09:00-12:00 window.
  const res = await request.post(`${apiBase()}/api/availability`, {
    headers: authHeaders(token),
    data: { dayOfWeek, startTime: '09:00', endTime: '12:00', recurring: true },
  });
  // 201 OK; 409 if the mentor already has an overlapping slot for that day.
  if (![200, 201].includes(res.status())) {
    throw new Error(`POST /api/availability -> ${res.status()} ${await res.text()}`);
  }
}

async function addMenteeSlot(request, token, dayOfWeek) {
  const res = await request.post(`${apiBase()}/api/mentee-availability`, {
    headers: authHeaders(token),
    data: { dayOfWeek, startTime: '09:00', endTime: '12:00', recurring: true },
  });
  if (![200, 201].includes(res.status())) {
    throw new Error(`POST /api/mentee-availability -> ${res.status()} ${await res.text()}`);
  }
}

async function searchMentors(request, token, params = {}) {
  const url = new URL(`${apiBase()}/api/users/search`);
  url.searchParams.set('role', 'MENTOR');
  for (const [k, v] of Object.entries(params)) {
    if (Array.isArray(v)) v.forEach(item => url.searchParams.append(k, String(item)));
    else if (v != null && v !== '') url.searchParams.set(k, String(v));
  }
  return request.get(url.toString(), { headers: authHeaders(token) });
}

async function matchingMentors(request, token, params = {}) {
  const url = new URL(`${apiBase()}/api/matching/mentors`);
  for (const [k, v] of Object.entries(params)) {
    if (Array.isArray(v)) v.forEach(item => url.searchParams.append(k, String(item)));
    else if (v != null && v !== '') url.searchParams.set(k, String(v));
  }
  return request.get(url.toString(), { headers: authHeaders(token) });
}

function ids(body) {
  const content = Array.isArray(body) ? body : (body.content ?? []);
  return content.map(item => item.id);
}

test('AT-22 advanced mentor search filter parameters', async ({ request }) => {
  await resetDb(request);

  // Three mentors with distinct mentorshipDuration + availability profile.
  const m1 = await seedMentor(request);
  const m2 = await seedMentor(request);
  const m3 = await seedMentor(request);
  const e1 = await seedMentee(request);

  await patchMentorProfile(request, m1.sessionToken, { mentorshipDuration: 1 });
  await patchMentorProfile(request, m2.sessionToken, { mentorshipDuration: 3 });
  await patchMentorProfile(request, m3.sessionToken, { mentorshipDuration: 6 });

  await addMentorSlot(request, m1.sessionToken, 'MONDAY');
  await addMentorSlot(request, m2.sessionToken, 'MONDAY');
  await addMentorSlot(request, m2.sessionToken, 'WEDNESDAY');
  await addMentorSlot(request, m3.sessionToken, 'FRIDAY');

  // Mentee needs slots covering the days the test exercises so the
  // `hasAvailability=true` overlap path (when used) finds a partner.
  for (const day of ['MONDAY', 'WEDNESDAY', 'FRIDAY']) {
    await addMenteeSlot(request, e1.sessionToken, day);
  }

  const expectIds = async (res, expected) => {
    expect(res.status()).toBe(200);
    const idsList = ids(await res.json());
    expect(new Set(idsList)).toEqual(new Set(expected));
  };

  // C1 — back-compat: no new filters returns all three mentors.
  await expectIds(await searchMentors(request, e1.sessionToken), [m1.id, m2.id, m3.id]);

  // C2 — availabilityDays=MONDAY → M1 + M2 (any-day overlap).
  await expectIds(
    await searchMentors(request, e1.sessionToken, { availabilityDays: ['MONDAY'] }),
    [m1.id, m2.id],
  );

  // C3 — availabilityDays=MONDAY,WEDNESDAY → still M1 + M2 (OR semantics
  // within the category, not AND).
  await expectIds(
    await searchMentors(request, e1.sessionToken, { availabilityDays: ['MONDAY', 'WEDNESDAY'] }),
    [m1.id, m2.id],
  );

  // C4 — mentorshipDuration single and IN-list.
  await expectIds(
    await searchMentors(request, e1.sessionToken, { mentorshipDuration: [3] }),
    [m2.id],
  );
  await expectIds(
    await searchMentors(request, e1.sessionToken, { mentorshipDuration: [1, 3] }),
    [m1.id, m2.id],
  );

  // C5 — combined (AND across categories): availabilityDays=MONDAY +
  // mentorshipDuration=3 → only M2.
  await expectIds(
    await searchMentors(request, e1.sessionToken, {
      availabilityDays: ['MONDAY'],
      mentorshipDuration: [3],
    }),
    [m2.id],
  );

  // C6 — /api/matching/mentors with minMatchScore=0 returns all reachable
  // ranked mentors (i.e. equivalent to the unfiltered call).
  {
    const res = await matchingMentors(request, e1.sessionToken, { minMatchScore: 0 });
    expect(res.status()).toBe(200);
    const body = await res.json();
    const content = Array.isArray(body) ? body : (body.content ?? []);
    expect(content.length).toBeGreaterThanOrEqual(1);
  }

  // C7 — minMatchScore=999999 cuts the list to empty AND adjusts
  // totalElements (paging totals reflect the threshold).
  {
    const res = await matchingMentors(request, e1.sessionToken, { minMatchScore: 999999 });
    expect(res.status()).toBe(200);
    const body = await res.json();
    const content = Array.isArray(body) ? body : (body.content ?? []);
    expect(content.length).toBe(0);
    if (typeof body.totalElements === 'number') {
      expect(body.totalElements).toBe(0);
    }
  }
});
