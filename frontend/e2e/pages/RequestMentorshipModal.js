export class RequestMentorshipModal {
  constructor(page) {
    this.page = page;
  }

  root() {
    return this.page.getByTestId('mentorship-request-modal');
  }

  async fillAndSubmit(message = '') {
    if (message) {
      await this.page.getByTestId('mentorship-request-message').fill(message);
    }
    await this.page.getByTestId('mentorship-request-submit').click();
  }

  errorBanner() {
    return this.page.getByTestId('mentorship-request-error');
  }
}
