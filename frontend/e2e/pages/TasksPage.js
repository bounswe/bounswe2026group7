/**
 * Read-only POM for /tasks. Same caveat as SchedulePage — the current
 * TasksPage renders mock data, so AT-02's task assign/submit/feedback leg
 * goes through /api/tasks directly. This POM exists so the spec can still
 * smoke-check that /tasks renders and so it's already in place once the page
 * is wired to the real backend.
 */
export class TasksPage {
  constructor(page) {
    this.page = page;
  }

  async goto({ mentorshipId } = {}) {
    const path = mentorshipId ? `/tasks?mentorshipId=${mentorshipId}` : '/tasks';
    await this.page.goto(path);
  }

  list() {
    return this.page.getByTestId('tasks-list');
  }

  emptyState() {
    return this.page.getByTestId('tasks-empty');
  }

  taskItem(taskId) {
    return this.page.getByTestId(`tasks-item-${taskId}`);
  }
}
