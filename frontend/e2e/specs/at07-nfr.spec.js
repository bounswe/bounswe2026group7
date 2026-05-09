import { test, expect, request as pwRequest } from '@playwright/test';
import {
  seedMentor,
  seedMentee,
  login,
} from '../fixtures/apiClient.js';
import {
  resetRateLimits,
  triggerMeetingReminders,
  createMentorshipRequest,
  acceptMentorshipRequest,
  listActiveMentorships,
  createMeeting,
  confirmMeeting,
  listNotifications,
} from '../fixtures/mentorshipApi.js';

/**
 * AT-07 (#317) — non-functional acceptance criteria. Three independent
 * tests, all tagged `@nfr` so they can be filtered as a smoke for the
 * non-functional layer:
 *
 *   1. Public-API rate limit returns 429 with Retry-After + X-RateLimit-*
 *      headers. Hits the real middleware (#270 / #302), not a mock.
 *   2. Security headers — CSP, HSTS, X-Frame-Options, X-Content-Type-Options
 *      — present on a representative cross-section of routes.
 *   3. Notification delivery budget — a meeting reminder fired by the
 *      scheduler is visible via /api/notifications within the SLA.
 *
 * The workflow keeps `APP_RATELIMIT_ENABLED=true` for the whole run so
 * the middleware is genuinely active. Test 1 below calls
 * /api/test/reset-ratelimits at the start so its 11-attempt probe doesn't
 * leak into the bucket budgets that AT-01 / AT-02 / AT-06 share.
 */

const backendUrl = () => process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

test.describe.configure({ mode: 'serial' });

test('AT-07 rate-limit middleware emits 429 with documented headers @nfr', async ({ request }) => {
  // Clear the bucket so prior specs (or CI noise) don't pre-consume budget.
  await resetRateLimits(request);

  // The auth-login rule is IP-keyed at 10/min; 11 sequential POSTs from
  // the same runner IP should yield a 429 on the last attempt. Keep the
  // payload INVALID so we don't accidentally create real sessions for a
  // user that doesn't exist either.
  const payload = { email: 'rate-limit-probe@example.com', password: 'NotARealPassword!1' };
  const responses = [];
  for (let i = 0; i < 11; i++) {
    const res = await request.post(`${backendUrl()}/api/auth/login`, { data: payload });
    responses.push(res);
  }

  // First 10 should be 401 (invalid creds, bucket has budget); 11th 429.
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

  // Reset again so we don't leak a saturated bucket into the next test in
  // this spec (security-headers / delivery-time both call /api/auth/*).
  await resetRateLimits(request);
});

test('AT-07 security headers cover CSP, HSTS, XFO, X-Content-Type-Options @nfr', async ({ request }) => {
  await resetRateLimits(request);

  // Unauthenticated public route — every Spring response should carry the
  // Spring Security defaults plus the explicit CSP/HSTS we added.
  const ctx = await pwRequest.newContext();
  try {
    const authProbe = await ctx.post(`${backendUrl()}/api/auth/login`, {
      data: { email: 'header-probe@example.com', password: 'no-auth-attempt-needed' },
    });
    const authHeaders = authProbe.headers();

    // The lower-case lookup matches Playwright's normalised header map.
    expect(authHeaders['x-frame-options']).toBe('DENY');
    expect(authHeaders['x-content-type-options']).toBe('nosniff');
    expect(
      authHeaders['strict-transport-security'],
      'HSTS header should declare a 1-year max-age and includeSubDomains',
    ).toMatch(/max-age=31536000.*includeSubDomains/);
    expect(
      authHeaders['content-security-policy'],
      'CSP should at least declare a default-src and frame-ancestors',
    ).toMatch(/default-src 'self'/);
    expect(authHeaders['content-security-policy']).toMatch(/frame-ancestors 'none'/);

    // A second, authenticated route confirms headers aren't endpoint-scoped.
    // We don't need a real account — the unauth response from /api/users/me
    // (a 401 from JwtAuthenticationFilter) still carries the security
    // headers because the filter chain runs them before authz.
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
  // Faker keeps seeded users unique, so we don't truncate the DB here —
  // resetDb would race other specs running on the same backend. We do
  // reset rate-limit buckets so a previous saturating test (the 11-login
  // probe above) doesn't bleed into the seedMentor/login calls below.
  await resetRateLimits(request);

  const mentor = await seedMentor(request);
  const mentee = await seedMentee(request);
  const requestRow = await createMentorshipRequest(
    request,
    mentee.sessionToken,
    { mentorId: mentor.id, message: 'AT-07 SLA probe' },
  );
  await acceptMentorshipRequest(request, mentor.sessionToken, requestRow.id, 3);
  const [{ id: mentorshipId }] = await listActiveMentorships(request, mentor.sessionToken);

  const startTime = new Date(Date.now() + 60 * 60 * 1000 + 2 * 60 * 1000).toISOString();
  const meeting = await createMeeting(request, mentor.sessionToken, mentorshipId, {
    title: 'NFR delivery probe',
    date: startTime,
    durationMin: 30,
  });
  await confirmMeeting(request, mentee.sessionToken, meeting.id);

  // Lab 9 doesn't pin a numeric value for the notification SLA; we use 5s
  // as a defensible budget for an in-process scheduler invocation + JPA
  // write + REST round-trip on a CI runner. Tighten when a Lab 9 number
  // lands and update this comment.
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
      intervals: [200, 400, 800],
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

  // Use login to ensure the API surface is reachable end-to-end with the
  // active rate limit — if AT-07's rate-limit test left us saturated, this
  // would 429.
  const auth = await login(request, mentee.email, mentee.password);
  expect(auth.sessionToken, 'login should still succeed; bucket not saturated').toBeTruthy();
});
