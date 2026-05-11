import { test, expect } from '@playwright/test';

/**
 * AT-04b — posts + comments + upvote/downvote.
 *
 * BLOCKED on two fronts:
 *   - Backend: feed interactions (#347 — likes, comments, bookmarks,
 *     FeedInteractionController) is on `feature/347-feed-interactions` and
 *     not merged to dev. The product's voting model is binary "like", not
 *     reddit-style up/down — when this spec is unblocked, decide whether to
 *     reinterpret AT-04b's "upvote/downvote" as like-toggle or to push back
 *     on the issue text.
 *   - Frontend: no feed UI exists; #316 will need a FeedPage + PostDetail +
 *     comment thread before this spec can drive anything.
 *
 * Once unblocked, the spec should cover:
 *   - Mentee creates a post (UI), assert it appears in the feed.
 *   - Another user comments (UI), assert it appears under the post.
 *   - Toggle like (or upvote, depending on resolution above) and assert
 *     interaction state echoes back via FeedPostInteractionState.
 */
test.fixme('AT-04b post + comment + vote (blocked by #347 merge + feed UI)', async () => {
  expect.fail('Unblock by removing test.fixme once feed interactions ship + UI exists.');
});
