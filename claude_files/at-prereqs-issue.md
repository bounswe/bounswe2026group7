# Acceptance-Test Infrastructure Prerequisites (AT-13 / AT-14 / AT-15 / AT-16 unblock)

## Summary

The AT plan reviews for AT-13–16 surfaced six cross-cutting infrastructure
gaps. None of them ship a user-visible feature on their own; together they
unblock four otherwise-unbuildable acceptance tests by:

- Letting Playwright drive scheduler-dependent flows without 65-minute cron
  waits (AT-13, AT-14).
- Replacing fragile selectors with stable `data-testid` / `testID` hooks
  across web and mobile (AT-08, AT-10, AT-11, AT-14, AT-15, AT-16).
- Adding the small UI surfaces the spec actually exercises (Past
  mentorships list, admin DM console, banned-state banner) for AT-15.
- Making the Following feed reflect new posts in real time so the AT-09
  Layer-B assertion stops sitting under `test.fixme()`.
- Auto-refreshing the mentorship timeline after an in-page mutation so
  AT-14 Step 3 / 4 can assert "without refresh" honestly.

Single PR, single review cycle. Each section is isolated to its own file
set so reviewers can scan them independently.

## Scope

### 1. TestSupportController scheduler triggers (backend)
- `POST /api/test/trigger-meeting-auto-decline`
- `POST /api/test/trigger-mentorship-auto-completion`
- `POST /api/test/trigger-reminder-scheduler`

All three follow the existing `Optional<>`-wrapped exemplar
(`triggerMeetingReminders` in `TestSupportController.java`), inherit the
`@ConditionalOnProperty(name = "app.test-endpoints.enabled")` gate, and
return `{processedCount|completedCount, triggeredAt}` JSON shapes.

Unblocks: AT-13 Steps 8 + 10, AT-14 Step 9.

### 2. STOMP feed subscription hook (web)
- New `frontend/src/hooks/useFeedSubscription.js` mirroring
  `useConversationSubscription`.
- Wired into `FeedPage.jsx` only when the Following tab is active.
- Subscribes to `/topic/feed.{userId}` (payload `FeedPostPushPayload`),
  fetches full `FeedPostResponse` via `GET /api/feed/posts/{id}` and
  prepends with dedup-by-`postId`.

Unblocks: AT-09 Step 11 (lifts the `test.fixme`).

### 3. Mentorship timeline auto-refresh (web)
- `refreshKey` state lifted into `MentorshipDetailPage.jsx`, passed as
  prop into `MentorshipProgressTimeline` and included in its `reload`
  dependency list (no React `key=` re-mount).
- `MentorshipMilestones` accepts an `onMutate` callback and fires it
  after every existing local `reload()`.

Unblocks: AT-14 Steps 3 + 4 strict "without refresh" assertions.

### 4. AT-15 UI surfaces (web)
- **MyMentorshipsPage** at `/mentorships`: Active / Past tabs using
  `?status=ALL` from `listMentorships(...)`.
- **AdminConsolePage** at `/admin` gated by an `AdminRoute` redirect
  (server still enforces via `@PreAuthorize`). Send-Direct-Message form
  hits `POST /api/admin/messages/direct/{userId}`.
- **BannedStateBanner**: global error interceptor in `services/api.js`
  detects `403 + body.code === 'BANNED_UNTIL'`, dispatches a
  `CustomEvent('auth:banned')`. `AuthContext` listens, `MainLayout`
  renders the banner.

Unblocks: AT-15 Steps covering past-mentorship browse, admin DM, banned
login.

### 5. Mobile testID sweep
~96 mechanical `testID` additions across 9 screens
(`app/login.tsx`, `app/(tabs)/feed.tsx`, `app/(tabs)/index.tsx`,
`app/(tabs)/explore.tsx`, `app/connection-request.tsx`,
`app/connection-profile.tsx`, `app/milestones.tsx`,
`app/meetings-sessions.tsx`, `app/notifications.tsx`) plus one
`testID` prop on `components/ActionModal.tsx`.

Naming convention: `screen.element[.subkey]`. Dynamic suffixes always
use stable backend IDs (e.g. `feed.post-card.${postId}`).

Unblocks: AT-16.

### 6. Web data-testid sweep
Adds `data-testid` attributes to feed / explore / mentorship /
profile / messages surfaces that AT-08, AT-10, AT-11, AT-14, AT-15
specs need to locate. New constants module `frontend/e2e/testids.js`
keeps spec selectors greppable. Naming convention extends existing
`explore-mentor-card-${id}` pattern.

## Out of scope

- Implementing AT-08 through AT-16 specs themselves (lands in
  follow-up PRs once this batch is merged).
- Maestro CI integration (mobile testIDs land; Maestro pipeline
  configuration is its own PR).
- Polling fallback on the timeline (designed but gated behind
  `VITE_TIMELINE_POLL_MS`, default-off).

## Acceptance criteria

1. `mvn -B verify` is green in `backend/`.
2. `npm test` is green in `frontend/`.
3. `npx playwright test` is green against the full local stack
   (postgres + backend jar + frontend preview).
4. No regressions in existing specs (`at02-mentorship-blog.spec.js` in
   particular).
5. `app.test-endpoints.enabled=false` keeps all three new endpoints
   404 (verified by the existing
   `TestSupportControllerSecurityTest` extension).
6. Banner renders only when `body.code === 'BANNED_UNTIL'`; existing
   403 callsites (e.g. private-profile fetches) are unaffected.

## Risks + mitigations

- **AT-15 admin route reachable by direct URL.** Frontend gate only;
  backend `@PreAuthorize` is the backstop. Page renders an "Access
  denied" state on first 403 instead of a blank shell.
- **Privacy regression on Past tab.** Backend strips
  `menteeLastName` for mentor callers regardless of `status` filter;
  add an integration test asserting this for `?status=ALL`.
- **Two AFTER_COMMIT listeners (sync + cache eviction).** Already
  ordered via `@Order(1)/@Order(2)` from PR #437; not amplified here.
- **`data-testid` in production bundle.** Vite default keeps them;
  no current size/SEO concern. Documented in PR description so a
  later stripper plugin doesn't break AT-* specs silently.

## Rollout

1. Merge with `algorithm=legacy` defaults and
   `app.test-endpoints.enabled` unchanged — zero behavioural change in
   production.
2. AT spec PRs land subsequently and consume the new hooks.
