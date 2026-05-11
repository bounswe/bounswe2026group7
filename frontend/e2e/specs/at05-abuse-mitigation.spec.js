import { test, expect } from '@playwright/test';

/**
 * AT-05 — toxic post auto-flag → user report flow → admin escalation review
 * (ban / dismiss).
 *
 * BLOCKED, but only partially:
 *
 * - **Toxicity auto-flagging** is *not built* anywhere in the codebase. There
 *   is no Perspective/OpenAI/manual-corpus pipeline, no "flagged" column on
 *   FeedPost, no moderation worker. Issue text says "auto-flag" but the
 *   product has only manual user reports. Either narrow the spec to
 *   manual-only, or block on a new feature issue for auto-flagging.
 *
 * - **User report flow** is built on `feature/135-reporting-moderation` but
 *   not merged to dev. The endpoints (`POST /api/reports`,
 *   `GET /api/admin/reports`, `PATCH /api/admin/reports/{id}`) and the
 *   state machine OPEN → UNDER_REVIEW → RESOLVED/DISMISSED exist there.
 *
 * - **Admin frontend** does not exist — no AdminPage, no
 *   /admin/reports route, no role-conditional rendering. Even after #135
 *   merges, AT-05 needs a UI before it can run UI-driven assertions.
 *
 * Infrastructure that *is* ready (so unfixming is fast):
 *   - `seedAdmin(request)` from apiClient.js returns an admin + JWT.
 *   - `applyAdminAuth(context, admin)` from adminFixture.js injects the
 *     admin tokens into a Playwright context's localStorage so the admin
 *     login step doesn't repeat per test (the issue's storageState ask).
 *   - The toxicity corpus mentioned in the issue is also TODO; once
 *     auto-flag lands, seed under `e2e/fixtures/toxicCorpus.js`.
 *
 * Once unblocked, the spec should cover three independent tests:
 *   1. Toxic content auto-flag: post a known-toxic body, assert it lands
 *      under admin review without any user action.
 *   2. User report → admin queue: a user reports a post via UI, an admin
 *      sees it in the queue and transitions OPEN → UNDER_REVIEW.
 *   3. Admin resolves: ban the offending user (#134) or dismiss the
 *      report; assert state machine + downstream side effects.
 */
test.fixme('AT-05 abuse mitigation (blocked by auto-flag missing + #135 merge + admin UI)', async () => {
  expect.fail('Unblock by removing test.fixme once reporting + admin UI exist.');
});
