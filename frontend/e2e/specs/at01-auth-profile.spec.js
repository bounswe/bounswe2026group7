import { test, expect } from '@playwright/test';
import { makeUser, newPassword } from '../fixtures/users.js';
import {
  resetDb,
  getVerificationToken,
  getResetToken,
} from '../fixtures/apiClient.js';
import { RegisterPage } from '../pages/RegisterPage.js';
import { LoginPage } from '../pages/LoginPage.js';
import { VerifyEmailPage } from '../pages/VerifyEmailPage.js';
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage.js';
import { ResetPasswordPage } from '../pages/ResetPasswordPage.js';
import { ProfilePage } from '../pages/ProfilePage.js';

// AT-01: register → verify email → login → profile completion → password reset.
// One sequential test; Playwright `projects` runs the whole spec on chromium,
// firefox, and webkit so per-browser duplication is unnecessary.
test('AT-01 full auth + profile + reset flow', async ({ page, request }) => {
  await resetDb(request);

  const user = makeUser({ isMentor: false });

  // 1. Register via UI; backend writes a verification_tokens row but
  //    NoOpEmailService suppresses the Resend call.
  const registerPage = new RegisterPage(page);
  await registerPage.goto();
  await registerPage.fillAndSubmit(user);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('login-registered-banner')).toBeVisible();

  // 2. Pull the token through /api/test/* and submit it via the UI URL.
  const verificationToken = await getVerificationToken(request, user.email);
  const verifyEmailPage = new VerifyEmailPage(page);
  await verifyEmailPage.gotoWithToken(verificationToken);
  await expect(verifyEmailPage.successBanner()).toBeVisible();

  // 3. Login with the original credentials.
  const loginPage = new LoginPage(page);
  await loginPage.goto();
  await loginPage.signIn({ email: user.email, password: user.password });
  await expect(page).toHaveURL(/\/home$/);

  // 4. Profile completion: edit a couple of fields and confirm save.
  const profilePage = new ProfilePage(page);
  await profilePage.goto();
  // Wait for the form to render — getOwnProfile is async and ProfilePage
  // shows a "Loading profile..." pane until the response lands.
  await page.getByTestId('profile-name').waitFor({ state: 'visible' });

  // Capture the save API response so we can surface a useful diagnostic
  // when the backend rejects the payload — the on-screen success banner
  // alone gives no signal about why save didn't succeed.
  const savePromise = page.waitForResponse(
    res => /\/api\/users\/me\/(mentor|mentee)\b/.test(res.url())
      && res.request().method() === 'PATCH',
    { timeout: 15_000 },
  );
  await profilePage.editAndSave({
    name: `${user.firstName} ${user.lastName}`,
    interests: 'Mobile Development, AI/ML',
  });
  const saveResponse = await savePromise;
  if (!saveResponse.ok()) {
    const body = await saveResponse.text().catch(() => '');
    throw new Error(`Profile save returned ${saveResponse.status()}: ${body}`);
  }
  await expect(profilePage.successBanner()).toBeVisible();

  // 5. Logout via clearing the auth token (no dedicated logout button on the
  //    profile screen; AuthContext reads token from localStorage on mount).
  await page.evaluate(() => {
    window.localStorage.removeItem('auth_token');
    window.localStorage.removeItem('auth_role');
    window.localStorage.removeItem('auth_user_id');
  });

  // 6. Forgot-password flow.
  const forgotPasswordPage = new ForgotPasswordPage(page);
  await forgotPasswordPage.goto();
  await forgotPasswordPage.submitEmail(user.email);
  await expect(forgotPasswordPage.successBanner()).toBeVisible();

  // 7. Pull the reset token, submit new password through the UI.
  const resetToken = await getResetToken(request, user.email);
  const resetPasswordPage = new ResetPasswordPage(page);
  await resetPasswordPage.gotoWithToken(resetToken);
  const replacementPassword = newPassword();
  await resetPasswordPage.submitNewPassword(replacementPassword);
  await expect(page).toHaveURL(/\/login$/);

  // 8. Login with the new password to confirm the reset took effect.
  await loginPage.signIn({ email: user.email, password: replacementPassword });
  await expect(page).toHaveURL(/\/home$/);
});
