export class VerifyEmailPage {
  constructor(page) {
    this.page = page;
  }

  async gotoWithToken(token) {
    await this.page.goto(`/verify-email?token=${encodeURIComponent(token)}`);
  }

  successBanner() {
    return this.page.getByTestId('verify-email-success');
  }

  errorBanner() {
    return this.page.getByTestId('verify-email-error');
  }
}
