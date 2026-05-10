/**
 * The mentor's "Incoming Requests" panel lives on /home, so this POM wraps
 * just that section rather than introducing a dedicated /mentorship page.
 * AT-02 uses it to accept the pending request from the mentee with a chosen
 * mentorship duration.
 */
export class MentorshipInbox {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/home');
  }

  inbox() {
    return this.page.getByTestId('mentor-inbox');
  }

  requestRow(requestId) {
    return this.page.getByTestId(`mentor-inbox-request-${requestId}`);
  }

  /**
   * Click "Accept" on the (only) pending request row, choose a duration, then
   * confirm. The spec doesn't pin the request id ahead of time because the id
   * comes from the backend after the mentee submits via UI.
   */
  async acceptFirstRequest({ months = 3 } = {}) {
    await this.inbox().waitFor();
    const row = this.page
      .locator('[data-testid^="mentor-inbox-request-"]')
      .first();
    const requestId = await row.getAttribute('data-testid');
    const id = requestId?.replace('mentor-inbox-request-', '');
    if (!id) throw new Error('mentor-inbox: no pending request row found');

    await this.page.getByTestId(`mentor-inbox-accept-${id}`).click();
    await this.page.getByTestId(`mentor-inbox-duration-${months}`).click();
    await this.page.getByTestId(`mentor-inbox-confirm-${id}`).click();
    return id;
  }
}
