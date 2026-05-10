export class ForgotPasswordPage {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/forgot-password');
  }

  async submitEmail(email) {
    await this.page.getByTestId('forgot-password-email').fill(email);
    await this.page.getByTestId('forgot-password-submit').click();
  }

  successBanner() {
    return this.page.getByTestId('forgot-password-success');
  }
}
