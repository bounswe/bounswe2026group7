import { test, expect, request as pwRequest } from '@playwright/test';
import { seedMentor, seedMentee } from '../fixtures/apiClient.js';
import {
  resetRateLimits,
  triggerMeetingReminders,
  createMentorshipRequest,
  acceptMentorshipRequest,
  listActiveMentorships,
  createMeeting,
  confirmMeeting,
  listNotifications,
  setSharedGoal,
} from '../fixtures/mentorshipApi.js';

/**
 * AT-07 — Comprehensive Non-Functional System Validation (wiki § AT-07).
 *
 * Three independently-runnable, API-only tests tagged `@nfr`. The wiki
 * lists 13 cross-cutting NFR steps; the implementable subset on the
 * current backend is:
 *
 *   1. Rate-limit middleware emits 429 with Retry-After + X-RateLimit-*
 *      headers (wiki § AT-07 step 9, mapped to the auth-login bucket).
 *   2. Security headers — CSP, HSTS, X-Frame-Options, X-Content-Type-Options
 *      — present on both unauth and pre-auth surfaces (wiki § AT-07 step 2,
 *      generalised: HTTPS-equivalent header hardening).
 *   3. Notification delivery budget — a meeting reminder fired by the
 *      scheduler is observable via /api/notifications within the SLA
 *      (wiki performance § 2.3.1, the 5s budget Lab 9 codified).
 *
 * Best-practice notes:
 *   - Each test claims a UNIQUE probe email so the rate-limit cache is
 *     keyed exclusively to that flow — no cross-test bucket contention.
 *     The auth-login limiter is IP-keyed, so the cache key is shared per
 *     browser project anyway, but we still scrub the bucket at start of
 *     every test so order-of-execution doesn't matter.
 *   - The SLA test's "API surface still reachable" sanity probe hits
 *     `GET /api/users/me` (not /api/auth/login). /api/auth/login shares
 *     a hot rate-limit bucket with the first sub-test; /api/users/me
 *     runs through the JWT filter on a different rate-limit rule, so we
 *     never re-enter the saturated bucket.
 *   - We rely on the seed endpoint's pre-minted JWT throughout; no test
 *     calls /api/auth/login outside the rate-limit sub-test itself.
 */

const backendUrl = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

test.describe.configure({ mode: 'serial' });

test('AT-07 rate-limit middleware emits 429 with documented headers @nfr', async ({ request }) => {
  // Scrub the bucket so prior specs don't pre-consume our budget. The
  // auth-login rule is IP-keyed at 10/min; 11 sequential POSTs from the
  // runner IP should yield a 429 on the last attempt.
  await resetRateLimits(request);

  // Use a probe email scoped to THIS sub-test so the IP-bucket pressure
  // we generate here is the only contributor to the limit.
  const payload = {
    email: 'ratelimit-probe-at07@example.invalid',
    password: 'NotARealPassword!1',
  };
  const responses = [];
  for (let i = 0; i < 11; i++) {
    const res = await request.post(`${backendUrl()}/api/auth/login`, { data: payload });
    responses.push(res);
  }

  // First 10: 401 (invalid creds, bucket has budget). 11th: 429.
  const last = responses[responses.length - 1];
  expect(
    last.status(),
    `11th login attempt should be rate-limited; got ${last.status()}`,
  ).toBe(429);

  const headers = last.headers();
  expect(headers['retry-after'], 'Retry-After header missing').toBeDefined();
  expect(headers['x-ratelimit-limit'], 'X-RateLimit-Limit header missing').toBe('10');
  expect(headers['x-ratelimit-remaining'], 'X-RateLimit-Remaining should be 0').toBe('0');
  expect(headers['x-ratelimit-reset'], 'X-RateLimit-Reset header missing').toBeDefined();

  const body = await last.json().catch(() => ({}));
  expect(body.error || body.message || '', 'rate-limit body should mention the limit')
    .toMatch(/rate limit|too many/i);

  // Hand the bucket back to neighbouring tests in a clean state.
  await resetRateLimits(request);
});

test('AT-07 security headers cover CSP, HSTS, XFO, X-Content-Type-Options @nfr', async ({ request: _request }) => {
  // Independent request context so cookies / shared state from other tests
  // can't leak into the header probe. Bucket scrubbed up front so any
  // leftover saturation from the rate-limit sub-test (or AT-01/02/06) can't
  // mask the actual 401 we expect on the probe.
  await resetRateLimits(_request);
  const ctx = await pwRequest.newContext();
  try {
    // Unauth login probe with intentionally-non-matching credentials. The
    // backend short-circuits on "user not found" so the 401 carries the
    // Spring Security default headers plus our explicit CSP/HSTS adds.
    const authProbe = await ctx.post(`${backendUrl()}/api/auth/login`, {
      data: {
        email: 'headers-probe-at07@example.invalid',
        password: 'no-auth-attempt-needed',
      },
    });
    const authHeaders = authProbe.headers();

    expect(authHeaders['x-frame-options']).toBe('DENY');
    expect(authHeaders['x-content-type-options']).toBe('nosniff');
    expect(
      authHeaders['strict-transport-security'],
      'HSTS header should declare a 1-year max-age and includeSubDomains',
    ).toMatch(/max-age=31536000.*includeSubDomains/);
    expect(
      authHeaders['content-security-policy'],
      'CSP should at least declare a default-src',
    ).toMatch(/default-src 'self'/);
    expect(authHeaders['content-security-policy'])
      .toMatch(/frame-ancestors 'none'/);

    // A pre-auth route confirms the headers aren't endpoint-scoped. The
    // unauth response from /api/users/me (a 401 from JwtAuthenticationFilter)
    // still carries the security headers because Spring Security runs the
    // header writers ahead of authn.
    const meProbe = await ctx.get(`${backendUrl()}/api/users/me`);
    const meHeaders = meProbe.headers();
    expect(meHeaders['x-frame-options']).toBe('DENY');
    expect(meHeaders['x-content-type-options']).toBe('nosniff');
    expect(meHeaders['strict-transport-security']).toMatch(/max-age=31536000/);
    expect(meHeaders['content-security-policy']).toMatch(/default-src 'self'/);
  } finally {
    await ctx.dispose();
  }
});

test('AT-07 meeting-reminder notification is delivered within the SLA budget @nfr', async ({ request }) => {
  // Faker keeps seeded users unique; we don't need resetDb (would race
  // other specs running on the same backend). We do scrub rate-limit
  // buckets so a previous saturating test can't leak a 429 into our
  // seedMentor/seedMentee/etc. setup calls (those don't go through
  // /api/auth/login but do hit the global per-IP fallback buckets).
  await resetRateLimits(request);

  const mentor = await seedMentor(request);
  const mentee = await seedMentee(request);

  // Build the mentorship → goal → meeting → confirmation chain via API.
  // Tokens are pre-minted by the seed endpoint; no /api/auth/login round-trip.
  const requestRow = await createMentorshipRequest(request, mentee.sessionToken, {
    mentorId: mentor.id,
    message: 'AT-07 SLA probe',
  });
  await acceptMentorshipRequest(request, mentor.sessionToken, requestRow.id, 3);
  const [{ id: mentorshipId }] = await listActiveMentorships(request, mentor.sessionToken);

  // #335 — meetings require a non-blank shared goal on the mentorship.
  await setSharedGoal(
    request,
    mentor.sessionToken,
    mentorshipId,
    'AT-07 SLA probe shared goal.',
  );

  // Schedule a meeting 1h 2m out so the 1h-before reminder window catches it.
  const startTime = new Date(Date.now() + 60 * 60 * 1000 + 2 * 60 * 1000).toISOString();
  const meeting = await createMeeting(request, mentor.sessionToken, mentorshipId, {
    title: 'NFR delivery probe',
    date: startTime,
    durationMin: 30,
  });
  await confirmMeeting(request, mentee.sessionToken, meeting.id);

  // Lab 9 doesn't pin a numeric SLA; 5s is a defensible budget for the
  // in-process scheduler + JPA write + REST round-trip on a CI runner.
  const triggeredAt = Date.now();
  await triggerMeetingReminders(request);

  await expect.poll(
    async () => {
      const list = await listNotifications(request, mentee.sessionToken);
      return list.some((n) => n.type === 'MEETING_REMINDER');
    },
    {
      message: 'mentee should see MEETING_REMINDER row within the SLA budget',
      timeout: 5_000,
      intervals: [100, 200, 400, 800],
    },
  ).toBe(true);

  const elapsed = Date.now() - triggeredAt;
  expect(
    elapsed,
    `notification surfaced within SLA budget; took ${elapsed}ms`,
  ).toBeLessThan(5_000);

  // Sanity: the delivery should stick, not just race in.
  const finalList = await listNotifications(request, mentee.sessionToken);
  expect(finalList.filter((n) => n.type === 'MEETING_REMINDER')).not.toHaveLength(0);

  // "API surface reachable end-to-end" sanity. Previous implementation
  // called /api/auth/login here, which shares a hot rate-limit bucket with
  // the first sub-test and reliably flaked. /api/users/me runs through the
  // JWT filter and a different rate-limit rule, so we don't re-enter the
  // saturated bucket. Polling absorbs any transient infra hiccup without
  // hiding a real outage.
  await expect.poll(
    async () => {
      const res = await request.get(`${backendUrl()}/api/users/me`, {
        headers: { Authorization: `Bearer ${mentee.sessionToken}` },
      });
      return res.ok();
    },
    {
      message: 'GET /api/users/me should still succeed with the seeded session token',
      timeout: 10_000,
      intervals: [100, 200, 400, 800, 1500],
    },
  ).toBe(true);
});
