import { test, expect } from '@playwright/test';
import { seedMentor, seedMentee } from '../fixtures/apiClient.js';
import {
  createMentorshipRequest,
  acceptMentorshipRequest,
  listActiveMentorships,
  createMeeting,
  confirmMeeting,
  triggerMeetingReminders,
  listNotifications,
} from '../fixtures/mentorshipApi.js';
import { installServiceWorkerStub } from '../fixtures/sw-stub.js';
import { MessagesPage } from '../pages/MessagesPage.js';
import { NotificationsPage } from '../pages/NotificationsPage.js';

/**
 * AT-06 (#317): real-time messaging across two browser contexts + scheduled
 * meeting-reminder delivery surfacing as an in-app notification.
 *
 * Push delivery via a registered service worker is *not* asserted here —
 * the frontend doesn't yet ship a `firebase-messaging-sw.js` or call
 * `POST /api/users/me/devices`, so there's no production code path to drive
 * end-to-end. The SW stub fixture is in place
 * (`e2e/fixtures/sw-stub.js`) so unfixming the push leg is a small change
 * once the frontend lands those pieces. The in-app reminder leg covers
 * the same NotificationService → DB → /api/notifications path that any
 * future push pipeline will branch from.
 */

/**
 * Inject auth credentials directly into a fresh context's localStorage so
 * the SPA mounts already authenticated, skipping the UI login leg that
 * AT-01/AT-02 already cover. Mirrors the pattern in `adminFixture.js`.
 */
async function applyAuth(context, user) {
  await context.addInitScript(({ token, role, userId }) => {
    window.localStorage.setItem('auth_token', token);
    window.localStorage.setItem('auth_role', role);
    window.localStorage.setItem('auth_user_id', String(userId));
  }, { token: user.sessionToken, role: user.role, userId: user.id });
}

test('AT-06 mentor↔mentee messaging in real time + meeting reminder', async ({ browser, request }) => {
  // No resetDb here on purpose — Faker emails already make every seeded
  // user unique, and resetting would race with any other spec running
  // against the same backend. Each AT-* spec uses fresh users so they
  // don't conflict on data, only on bucket budgets (which AT-07 manages).

  // 1. Seed the two participants and stand up an ACTIVE mentorship via API
  //    — the request/accept UI flow already has its own coverage in AT-02.
  const mentor = await seedMentor(request);
  const mentee = await seedMentee(request);

  const requestRow = await createMentorshipRequest(
    request,
    mentee.sessionToken,
    { mentorId: mentor.id, message: 'AT-06 fixture request' },
  );
  await acceptMentorshipRequest(request, mentor.sessionToken, requestRow.id, 3);

  const mentorships = await listActiveMentorships(request, mentor.sessionToken);
  expect(mentorships, 'mentor should see exactly one active mentorship').toHaveLength(1);
  const mentorshipId = mentorships[0].id;

  // 2. Two contexts so both sides stay open simultaneously. SW stub on each
  //    so the page registers cleanly even if it tries.
  const mentorCtx = await browser.newContext();
  const menteeCtx = await browser.newContext();
  await Promise.all([
    installServiceWorkerStub(mentorCtx),
    installServiceWorkerStub(menteeCtx),
    applyAuth(mentorCtx, mentor),
    applyAuth(menteeCtx, mentee),
  ]);
  const mentorPage = await mentorCtx.newPage();
  const menteePage = await menteeCtx.newPage();

  try {
    const mentorMessages = new MessagesPage(mentorPage);
    const menteeMessages = new MessagesPage(menteePage);

    await Promise.all([
      mentorMessages.gotoMentorshipThread(mentorshipId),
      menteeMessages.gotoMentorshipThread(mentorshipId),
    ]);

    // Wait for both threads to mount — STOMP subscription is set up in
    // useConversationSubscription on mount.
    await mentorMessages.thread().waitFor({ state: 'visible' });
    await menteeMessages.thread().waitFor({ state: 'visible' });

    // 3. Mentee sends a message; assert it appears in mentor's DOM via
    //    the WebSocket subscription, not via a refresh.
    const menteeMsg = `Hello from mentee — ${Date.now()}`;
    await menteeMessages.sendMessage(menteeMsg);
    await expect(mentorMessages.bubbleByText(menteeMsg)).toBeVisible({ timeout: 10_000 });

    // 4. Mentor replies; same assertion the other way.
    const mentorMsg = `Reply from mentor — ${Date.now()}`;
    await mentorMessages.sendMessage(mentorMsg);
    await expect(menteeMessages.bubbleByText(mentorMsg)).toBeVisible({ timeout: 10_000 });

    // 5. Schedule + confirm a meeting roughly 1 hour out, then trigger the
    //    reminder scheduler manually. Reminder offsets are 24h + 1h, so a
    //    meeting at +1h+2min should land in the 1-hour window's 5-min slot.
    const startTime = new Date(Date.now() + 60 * 60 * 1000 + 2 * 60 * 1000).toISOString();
    const meeting = await createMeeting(request, mentor.sessionToken, mentorshipId, {
      title: 'AT-06 reminder probe',
      date: startTime,
      durationMin: 30,
    });
    await confirmMeeting(request, mentee.sessionToken, meeting.id);

    await triggerMeetingReminders(request);

    // 6. Both mentor and mentee should now have a MEETING_REMINDER row.
    //    Poll the API rather than just the UI so a slow scheduler doesn't
    //    cause flake — assertion is the same shape on both sides.
    const reminderEventually = async (token) => {
      await expect.poll(
        async () => {
          const list = await listNotifications(request, token);
          return list.some((n) => n.type === 'MEETING_REMINDER');
        },
        { timeout: 10_000, intervals: [500, 1000, 2000] },
      ).toBe(true);
    };
    await Promise.all([
      reminderEventually(mentor.sessionToken),
      reminderEventually(mentee.sessionToken),
    ]);

    // 7. Mentee navigates to /notifications and sees the reminder rendered.
    const menteeNotifications = new NotificationsPage(menteePage);
    await menteeNotifications.goto();
    await expect(menteeNotifications.itemsByType('MEETING_REMINDER').first()).toBeVisible();
  } finally {
    await mentorCtx.close();
    await menteeCtx.close();
  }
});

/**
 * The push-delivery leg is intentionally not asserted today (see file
 * header). When the frontend ships an FCM service worker + device token
 * registration, replace this fixme with an assertion that the SW stub's
 * `__pushReceived` window array contains an entry after a backend dispatch.
 */
test.fixme('AT-06 push notification delivery via service worker (blocked: no frontend SW + no device registration)', async () => {});
