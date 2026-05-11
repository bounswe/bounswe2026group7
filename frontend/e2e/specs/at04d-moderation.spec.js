import { test, expect } from '@playwright/test';

/**
 * AT-04d — moderator mute / remove / pin.
 *
 * BLOCKED. There is no community-moderator role, no mute endpoint, no pin
 * endpoint, and no remove-by-moderator endpoint on dev. The closest existing
 * surfaces are platform-admin actions (#135, also unmerged) which are
 * different — moderator authority is community-scoped, admin is platform-
 * wide. AT-04d depends on the community feature (#281) shipping a moderator
 * role first, then dedicated moderation endpoints + UI.
 *
 * Once unblocked, three discrete tests:
 *   - Moderator mutes a user; muted user's subsequent post in the same
 *     community is hidden from the feed (or 403'd at write).
 *   - Moderator removes a post; the post's `deletedAt` is set (or
 *     equivalent), and it disappears from the feed for non-mods.
 *   - Moderator pins a post; the post sorts to the top of the community
 *     feed regardless of timestamp / score.
 */
test.fixme('AT-04d moderation: mute / remove / pin (blocked by #281 community + missing mod endpoints)', async () => {
  expect.fail('Unblock when community moderator role + endpoints + UI exist.');
});
