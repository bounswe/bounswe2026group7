/**
 * POM for /notifications. AT-06 (#317) lands on this page after a meeting
 * reminder fires through the backend scheduler, then asserts that a row of
 * type MEETING_REMINDER appears in the list.
 */
export class NotificationsPage {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/notifications');
  }

  list() {
    return this.page.getByTestId('notifications-list');
  }

  emptyState() {
    return this.page.getByTestId('notifications-empty');
  }

  /**
   * Filter notification rows by their data-notification-type attribute. Used
   * to find a MEETING_REMINDER among other rows that may be present.
   */
  itemsByType(type) {
    return this.list().locator(`[data-notification-type="${type}"]`);
  }
}
