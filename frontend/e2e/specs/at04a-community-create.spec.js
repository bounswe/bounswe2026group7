import { test, expect } from '@playwright/test';

/**
 * AT-04a — community create + join + leave.
 *
 * BLOCKED. The community/group concept does not exist on dev: there is no
 * `Community` (or `Group`) entity, no controller, no migration, and no
 * frontend page. Issue #316's Notes pin this on the Blog & Content System
 * issue (#281), which has not landed.
 *
 * When #281 ships:
 *   1. Replace `test.fixme` below with a real test body.
 *   2. Add a `CommunityPage` POM under `e2e/pages/`.
 *   3. Extend `e2e/fixtures/apiClient.js` with `seedCommunities(request, n)`
 *      so the create + join + leave flow has predictable starting state.
 *
 * Acceptance check from #316: "create, join/leave" all hit the UI and assert
 * post-state via the same UI. Order-independence requires resetting via
 * `/api/test/reset` at the top of each test.
 */
test.fixme('AT-04a community create + join + leave (blocked by #281)', async () => {
  // Intentionally empty until the community feature lands.
  expect.fail('Unblock by removing test.fixme once community endpoints exist.');
});
