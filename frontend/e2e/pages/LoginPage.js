export class LoginPage {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/login');
  }

  async signIn({ email, password }) {
    await this.page.getByTestId('login-email').fill(email);
    await this.page.getByTestId('login-password').fill(password);
    await this.page.getByTestId('login-submit').click();
  }

  forgotPasswordLink() {
    return this.page.getByTestId('login-forgot-link');
  }

  errorBanner() {
    return this.page.getByTestId('login-error');
  }
}
