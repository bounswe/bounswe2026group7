export class LoginPage {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/login');
  }

  /**
   * Hydration-race-safe login.
   *
   * The login form has a framer-motion entrance animation and React commits
   * its controlled-input state in the first effect tick. If Playwright's
   * fill() lands during that tick, the controlled state can overwrite the
   * input with '' and the subsequent submit silently fails validation, so
   * the POST is never sent and the page sits on /login until timeout.
   *
   * LoginPage.jsx now emits `<form data-hydrated="true">` after its first
   * useEffect. We gate the fill on that signal so the React commit cannot
   * preempt our writes. As a belt-and-braces guard against any other
   * transient remount, we read the values back after filling and retype if
   * they came up empty.
   */
  async signIn({ email, password }) {
    const form = this.page.locator('[data-testid="login-form"][data-hydrated="true"]');
    await form.waitFor({ state: 'attached', timeout: 15_000 });

    const emailInput = this.page.getByTestId('login-email');
    const passwordInput = this.page.getByTestId('login-password');
    const submit = this.page.getByTestId('login-submit');

    await emailInput.fill(email);
    await passwordInput.fill(password);

    // Verify the controlled state actually took the value. Up to two
    // retries if a stray re-render dropped it; in practice the hydrated
    // gate above is enough, this is paranoid coverage.
    for (let attempt = 0; attempt < 3; attempt += 1) {
      const [v1, v2] = await Promise.all([
        emailInput.inputValue(),
        passwordInput.inputValue(),
      ]);
      if (v1 === email && v2 === password) break;
      if (v1 !== email) await emailInput.fill(email);
      if (v2 !== password) await passwordInput.fill(password);
    }

    await submit.click();
  }

  forgotPasswordLink() {
    return this.page.getByTestId('login-forgot-link');
  }

  errorBanner() {
    return this.page.getByTestId('login-error');
  }
}
