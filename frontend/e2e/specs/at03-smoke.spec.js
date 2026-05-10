import { test, expect, request as pwRequest } from '@playwright/test';

const backendUrl = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

// AT-03 smoke: side-effect-free "system answers the door" checks. Chromium-only
// because cross-browser coverage is AT-01's job and smoke is for fast PR
// feedback. The describe stays serial so a single worker handles all four
// tests inside the 30-second budget without spin-up overhead.
test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ browserName }) => {
  test.skip(browserName !== 'chromium', '@smoke is chromium-only by design');
});

test('home loads @smoke', { tag: '@smoke' }, async ({ page }) => {
  const response = await page.goto('/');
  expect(response?.status(), 'GET / should be 2xx').toBeLessThan(400);
  await expect(page.locator('body')).toBeVisible();
});

test('login form renders @smoke', { tag: '@smoke' }, async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByTestId('login-email')).toBeVisible();
  await expect(page.getByTestId('login-password')).toBeVisible();
  await expect(page.getByTestId('login-submit')).toBeVisible();
});

test('register form renders @smoke', { tag: '@smoke' }, async ({ page }) => {
  await page.goto('/register');
  await expect(page.getByTestId('register-email')).toBeVisible();
  await expect(page.getByTestId('register-submit')).toBeVisible();
});

test('backend health is up @smoke', { tag: '@smoke' }, async () => {
  const ctx = await pwRequest.newContext();
  try {
    const res = await ctx.get(`${backendUrl}/actuator/health`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe('UP');
  } finally {
    await ctx.dispose();
  }
});
