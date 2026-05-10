export class ResetPasswordPage {
  constructor(page) {
    this.page = page;
  }

  async gotoWithToken(token) {
    await this.page.goto(`/reset-password?token=${encodeURIComponent(token)}`);
  }

  async submitNewPassword(password) {
    await this.page.getByTestId('reset-password-new-password').fill(password);
    await this.page.getByTestId('reset-password-submit').click();
  }
}
