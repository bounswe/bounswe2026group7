export class ExplorePage {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/explore');
  }

  mentorCard(mentorId) {
    return this.page.getByTestId(`explore-mentor-card-${mentorId}`);
  }

  /** Open the Request Mentorship modal for a specific mentor by id. */
  async openRequestForMentor(mentorId) {
    await this.page.getByTestId(`explore-send-request-${mentorId}`).click();
  }

  /**
   * Convenience for tests that don't pin a specific mentor id — useful when the
   * spec just needs to discover *some* mentor and there's only one in the seed.
   */
  async openFirstAvailableRequest() {
    await this.page.getByTestId('explore-mentor-grid').waitFor();
    await this.page.locator('[data-testid^="explore-send-request-"]:not([disabled])').first().click();
  }
}
