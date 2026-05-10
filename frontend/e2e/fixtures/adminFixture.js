/**
 * Admin auth helpers for AT-05.
 *
 * Issue #316 calls for an admin role accessed via Playwright `storageState`,
 * so the login step doesn't repeat in every test. Storage state in Playwright
 * is a JSON snapshot of cookies + localStorage; the existing AuthContext
 * keeps tokens in localStorage (auth_token / auth_role / auth_user_id), so
 * the cleanest way to ship "logged-in admin" without per-test login is to
 * inject those keys into a fresh browser context before any navigation.
 *
 * `applyAdminAuth(context, admin)` does exactly that and is intended to be
 * called once per test (or once in a fixture/beforeAll) after seeding the
 * admin via `seedAdmin(request)` from apiClient.js.
 *
 * AT-05 is currently `test.fixme`'d pending #135 (reporting/moderation merge)
 * and the admin frontend pages, but the helper is in place so unfixming is a
 * one-line change.
 */
export async function applyAdminAuth(context, admin) {
  await context.addInitScript(({ token, role, userId }) => {
    window.localStorage.setItem('auth_token', token);
    window.localStorage.setItem('auth_role', role);
    window.localStorage.setItem('auth_user_id', String(userId));
  }, {
    token: admin.sessionToken,
    role: admin.role,
    userId: admin.id,
  });
}

/**
 * Same shape, applied to an already-open page rather than a fresh context.
 * Useful when a spec wants to swap roles mid-test (rare, but happens in
 * AT-05 toxicity → admin review flows).
 */
export async function injectAdminAuthIntoPage(page, admin) {
  await page.evaluate(({ token, role, userId }) => {
    window.localStorage.setItem('auth_token', token);
    window.localStorage.setItem('auth_role', role);
    window.localStorage.setItem('auth_user_id', String(userId));
  }, {
    token: admin.sessionToken,
    role: admin.role,
    userId: admin.id,
  });
}
