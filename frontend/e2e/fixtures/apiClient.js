/**
 * Thin client over the profile-gated TestSupportController endpoints. Only used
 * by E2E specs to fetch verification/reset tokens without going through real
 * email, and to wipe user state between runs.
 *
 * The backend must be started with APP_TEST_ENDPOINTS_ENABLED=true and
 * APP_EMAIL_ENABLED=false; otherwise these calls will 404.
 */

const apiBase = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

async function expectOk(res, label) {
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`${label} failed: ${res.status} ${body}`);
  }
  return res;
}

export async function resetDb(request) {
  const res = await request.post(`${apiBase}/api/test/reset`);
  await expectOk(res, 'POST /api/test/reset');
}

export async function getVerificationToken(request, email) {
  const res = await request.get(`${apiBase}/api/test/verification-token`, {
    params: { email },
  });
  await expectOk(res, 'GET /api/test/verification-token');
  const body = await res.json();
  return body.token;
}

export async function getResetToken(request, email) {
  const res = await request.get(`${apiBase}/api/test/password-reset-token`, {
    params: { email },
  });
  await expectOk(res, 'GET /api/test/password-reset-token');
  const body = await res.json();
  return body.token;
}

export async function seedUser(request, { isMentor = false, preVerified = true } = {}) {
  const res = await request.post(`${apiBase}/api/test/users`, {
    data: { isMentor, preVerified },
  });
  await expectOk(res, 'POST /api/test/users');
  return res.json(); // { id, email, password, role, firstName, lastName, sessionToken }
}

/** Sugar for the two roles AT-02 cares about. */
export const seedMentor = (request) => seedUser(request, { isMentor: true, preVerified: true });
export const seedMentee = (request) => seedUser(request, { isMentor: false, preVerified: true });

/**
 * Seeds an Admin user via /api/test/admin and returns the same shape as
 * seedUser plus a pre-minted sessionToken. AT-05 will persist this as a
 * Playwright `storageState` so the admin login step doesn't repeat per test.
 */
export async function seedAdmin(request) {
  const res = await request.post(`${apiBase}/api/test/admin`);
  await expectOk(res, 'POST /api/test/admin');
  return res.json();
}

/**
 * POST /api/auth/login as a fallback for specs that already hold credentials
 * (e.g., logging back in with a new password during AT-01). Returns the
 * full AuthResponse: { sessionToken, role, userId }.
 */
export async function login(request, email, password) {
  const res = await request.post(`${apiBase}/api/auth/login`, {
    data: { email, password },
  });
  await expectOk(res, 'POST /api/auth/login');
  return res.json();
}
