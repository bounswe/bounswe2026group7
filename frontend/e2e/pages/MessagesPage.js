/**
 * POM for /messages?mentorshipId=...&peerId=... — the unified mentee↔mentor
 * thread view that AT-06 (#317) drives end-to-end across two browser contexts.
 *
 * The page mounts a STOMP subscription on `/topic/conversation/{id}`, so a
 * message sent by one context appears in the other context's DOM without
 * a manual refresh; the spec asserts that exact propagation.
 */
export class MessagesPage {
  constructor(page) {
    this.page = page;
  }

  async gotoMentorshipThread(mentorshipId) {
    await this.page.goto(`/messages?mentorshipId=${mentorshipId}`);
  }

  thread() {
    return this.page.getByTestId('messages-thread');
  }

  emptyState() {
    return this.page.getByTestId('messages-empty');
  }

  /**
   * `data-testid="messages-bubble-{id}"` — id is unknown ahead of send, so
   * tests typically locate by text via `bubbleByText` instead.
   */
  bubbleById(id) {
    return this.page.getByTestId(`messages-bubble-${id}`);
  }

  /**
   * Locate a bubble by its rendered text. Useful for cross-context assertions.
   *
   * The thread also contains an inner `messages-bubble-text` div per bubble,
   * which would match a naive `[data-testid^="messages-bubble-"]` and trigger
   * a strict-mode violation when there are visible bubbles. Excluding that
   * specific testid keeps the matcher pinned to the outer bubble container
   * (`messages-bubble-{id}`).
   */
  bubbleByText(text) {
    return this.thread()
      .locator('[data-testid^="messages-bubble-"]:not([data-testid="messages-bubble-text"])')
      .filter({ hasText: text });
  }

  async sendMessage(text) {
    await this.page.getByTestId('messages-composer-input').fill(text);
    await this.page.getByTestId('messages-composer-send').click();
  }
}
