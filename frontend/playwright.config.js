import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:8000';

export default defineConfig({
  testDir: './e2e/specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // CI is serialized to 1 worker because /api/test/reset TRUNCATEs the whole
  // DB and parallel workers race-stomp on each other's seeded users — a
  // mid-test reset wipes the other worker's session. True isolation needs
  // schema-per-worker or per-test data namespacing; tracked as a follow-up.
  workers: process.env.CI ? 1 : undefined,
  // AT-02 has ~15 sequential UI+API steps including two UI logins that each
  // wait up to 20s for /home navigation (`expect(page).toHaveURL(/\/home$/,
  // { timeout: 20_000 })`). The spec's own per-action timeouts already
  // permit ~40s of login budget alone, exceeding Playwright's default 30s
  // testTimeout. 60s aligns the framework ceiling with the spec's
  // arithmetic. Doesn't mask real regressions — passing tests exit on
  // assertions, not on the ceiling.
  timeout: 60_000,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL,
    trace: 'on-first-retry',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
    // Framer-motion's LoginPage entrance animation (opacity + y + scale)
    // is the dominant variance source for AT-02's Firefox login flake on
    // GitHub Actions: the runner schedules the click during the tail of
    // the entrance tween, the submit-button click is delayed past the 40s
    // navigation budget, and the page stays on /login. Asking Playwright
    // to set prefers-reduced-motion: reduce on every browser context
    // tells framer-motion to skip the tween entirely — production
    // behaviour for users with the OS-level reduced-motion preference is
    // unaffected.
    reducedMotion: 'reduce',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox',  use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit',   use: { ...devices['Desktop Safari'] } },
  ],
  // CI brings up backend + frontend out-of-band so we don't double-start. For
  // local runs Playwright spins up `vite dev` itself unless one is already up.
  webServer: process.env.CI ? undefined : {
    command: 'npm run dev',
    url: baseURL,
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
