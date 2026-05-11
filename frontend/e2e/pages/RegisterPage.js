export class RegisterPage {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/register');
  }

  async fillAndSubmit({ firstName, lastName, email, password, isMentor }) {
    await this.page.getByTestId('register-first-name').fill(firstName);
    await this.page.getByTestId('register-last-name').fill(lastName);
    await this.page.getByTestId('register-email').fill(email);
    await this.page.getByTestId('register-password').fill(password);
    if (isMentor) {
      await this.page.getByTestId('register-role-mentor').click();
    } else {
      await this.page.getByTestId('register-role-mentee').click();
    }
    await this.page.getByTestId('register-submit').click();
  }
}
