/**
 * Read-only POM for /schedule. The current SchedulePage implementation reads
 * mock data from `src/services/mentorshipMocks.js` rather than the real
 * /api/mentorships/{id}/meetings endpoint, so AT-02's meeting-create leg goes
 * through the API directly. This POM is kept for the smoke-level "page loads"
 * assertion and to be filled out once the page is wired to the real backend.
 */
export class SchedulePage {
  constructor(page) {
    this.page = page;
  }

  async goto({ mentorshipId } = {}) {
    const path = mentorshipId ? `/schedule?mentorshipId=${mentorshipId}` : '/schedule';
    await this.page.goto(path);
  }

  list() {
    return this.page.getByTestId('schedule-list');
  }

  emptyState() {
    return this.page.getByTestId('schedule-empty');
  }

  meetingCard(meetingId) {
    return this.page.getByTestId(`schedule-meeting-${meetingId}`);
  }
}
