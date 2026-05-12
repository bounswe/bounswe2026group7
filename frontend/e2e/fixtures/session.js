/**
 * Programmatic session injection — the Playwright-recommended way to skip
 * UI login in tests that aren't testing the login flow itself.
 *
 * Why this exists: the LoginPage uses a framer-motion entrance animation
 * and React 18 hydration; Playwright `fill()` + `click()` can land before
 * React commits the initial controlled-input state and the submit handler
 * is bound. The race surfaces as "POST never fires" (verified in trace)
 * or "POST fires with empty body" (verified in backend log). Best practice
 * per Playwright's auth docs is to authenticate once via API and inject
 * the resulting token directly into storage — see
 * https://playwright.dev/docs/auth.
 *
 * The seed endpoints (`POST /api/test/users`, `POST /api/test/admin`)
 * already mint a `sessionToken` server-side, so no real `/api/auth/login`
 * round-trip is needed here either.
 */

/**
 * Returns a new Playwright BrowserContext with the given session pre-installed
 * in localStorage. Mirrors what `AuthContext.login()` writes after a real
 * UI login (auth_token / auth_role / auth_userId) so AuthContext picks up
 * the session on mount without going near the login form.
 *
 * @param {import('@playwright/test').Browser} browser
 * @param {{ sessionToken: string, role: string, id: number|string }} session
 * @param {string} [origin] — the frontend origin to seed storage under.
 */
export async function newAuthenticatedContext(browser, session, origin) {
  const baseOrigin = origin ?? process.env.E2E_BASE_URL ?? 'http://localhost:8000';
  return browser.newContext({
    storageState: {
      cookies: [],
      origins: [
        {
          origin: baseOrigin,
          localStorage: [
            { name: 'auth_token', value: session.sessionToken },
            { name: 'auth_role', value: session.role },
            { name: 'auth_userId', value: String(session.id) },
          ],
        },
      ],
    },
  });
}
