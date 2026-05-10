export class ProfilePage {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/profile');
  }

  async editAndSave({ name, interests }) {
    if (name !== undefined) {
      const nameField = this.page.getByTestId('profile-name');
      await nameField.fill('');
      await nameField.fill(name);
    }
    if (interests !== undefined) {
      const interestsField = this.page.getByTestId('profile-interests');
      await interestsField.fill('');
      await interestsField.fill(interests);
    }
    await this.page.getByTestId('profile-save').click();
  }

  successBanner() {
    return this.page.getByTestId('profile-save-success');
  }
}
