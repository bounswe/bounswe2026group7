import { test, expect } from '@playwright/test';

/**
 * AT-04c — feed sort / filter / pagination / infinite scroll.
 *
 * BLOCKED. Feed surfaces (#350: For-You, Following, Search controllers and
 * the Schwartzian-sorted ranker) live on `feature/350-feed-surfaces` and
 * have not merged to dev. The frontend has no feed page either.
 *
 * Spec scope when unblocked (per #316 acceptance criteria):
 *   - "For-You" tab returns ranked, paginated results; assert page boundary
 *     behavior (size 20 default, last page truncates).
 *   - "Following" tab returns chronological posts only from followed users.
 *   - Infinite scroll appends a fresh page when the sentinel enters
 *     viewport; existing items are not duplicated.
 *   - Hashtag and keyword search filters narrow the set as expected.
 *
 * The seed data strategy for ranking assertions is NetworkX-distributed
 * votes, called out explicitly in the issue. That helper will need to live
 * under `e2e/fixtures/feedSeed.js` and call a yet-to-exist
 * `/api/test/feed-seed` endpoint that posts N posts + simulated interactions
 * matching a configurable graph.
 */
test.fixme('AT-04c feed ranking + pagination + infinite scroll (blocked by #350 merge + feed UI)', async () => {
  expect.fail('Unblock by removing test.fixme once feed surfaces ship + UI exists.');
});
