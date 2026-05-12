import { test, expect } from '@playwright/test';
import { resetDb, seedMentor, seedMentee } from '../fixtures/apiClient.js';
import {
  resetRateLimits,
  createMeeting,
  confirmMeeting,
  createTask,
  submitTask,
  reviewTask,
  publishBlogPost,
  getBlogPost,
  listIncomingRequests,
  listActiveMentorships,
  setSharedGoal,
} from '../fixtures/mentorshipApi.js';
import { newAuthenticatedContext } from '../fixtures/session.js';
import { ExplorePage } from '../pages/ExplorePage.js';
import { RequestMentorshipModal } from '../pages/RequestMentorshipModal.js';
import { MentorshipInbox } from '../pages/MentorshipInbox.js';
import { SchedulePage } from '../pages/SchedulePage.js';
import { TasksPage } from '../pages/TasksPage.js';

/**
 * AT-02 — Mentor runs a complete mentorship lifecycle and publishes blog
 * content. Per the wiki spec (Acceptance-Tests.md § AT-02), the prerequisite
 * is "A verified mentor account ... The mentor is logged in" — login is
 * NOT a test step. We therefore authenticate via the seed endpoint's
 * pre-minted JWT and inject it into storage, which is the Playwright-
 * recommended pattern for tests where login is a prerequisite, not the
 * feature under test. See https://playwright.dev/docs/auth.
 *
 * Coverage map (wiki steps -> implementation):
 *   1  availability       — backend has no availability-write API surfaced
 *                           through the test seed yet; skipped with TODO
 *   2  candidate review   — mentee opens /explore, mentor card visible
 *   3  accept + duration  — UI (mentor inbox)
 *   4  schedule recurring — API (no create UI on /schedule yet)
 *   5  shared goal + ms.  — API (goal); milestone create UI not yet on the
 *                           detail page in a stable form, so we set the
 *                           shared goal which is the goal contract step
 *   6  task + feedback    — API (assign / submit / review)
 *   7  progress tracking  — smoke-render /tasks for mentee
 *   8  end mentorship     — out-of-scope until termination UI lands
 *   9  publish blog       — API (no blog-publish UI yet)
 *   10 like + comment     — covered by feed engagement specs, not duplicated
 *                           here; we assert the blog post is fetchable as the
 *                           "visible to authenticated users" contract
 */

test('AT-02 mentorship lifecycle + blog publish', async ({ browser, request }) => {
  // ---------------------------------------------------------------------------
  // Setup — API only. Each run starts from a clean DB so the order of
  // browser projects (chromium / firefox / webkit) doesn't matter; the
  // shared rate-limit bucket is scrubbed so AT-07's prior 11-login probe
  // can't leak a 429 into our seed calls.
  // ---------------------------------------------------------------------------
  await resetDb(request);
  await resetRateLimits(request);

  const mentor = await seedMentor(request);
  const mentee = await seedMentee(request);

  const mentorCtx = await newAuthenticatedContext(browser, {
    sessionToken: mentor.sessionToken,
    role: mentor.role,
    id: mentor.id,
  });
  const menteeCtx = await newAuthenticatedContext(browser, {
    sessionToken: mentee.sessionToken,
    role: mentee.role,
    id: mentee.id,
  });
  const mentorPage = await mentorCtx.newPage();
  const menteePage = await menteeCtx.newPage();

  try {
    // -------------------------------------------------------------------------
    // Step 2 — Mentee opens /explore and sees the mentor card.
    // No /login round-trip: AuthContext reads the injected `auth_token` on
    // mount and routes directly to the authenticated tree.
    // -------------------------------------------------------------------------
    const explore = new ExplorePage(menteePage);
    await explore.goto();
    await expect(explore.mentorCard(mentor.id)).toBeVisible();

    // -------------------------------------------------------------------------
    // Step 3 (mentee half) — Mentee submits a mentorship request via UI.
    // -------------------------------------------------------------------------
    await explore.openRequestForMentor(mentor.id);
    const modal = new RequestMentorshipModal(menteePage);
    await expect(modal.root()).toBeVisible();
    await modal.fillAndSubmit('Hi, I would love your guidance on backend systems.');
    await expect(menteePage.getByTestId(`explore-send-request-${mentor.id}`))
      .toContainText(/sent/i);

    // -------------------------------------------------------------------------
    // Step 3 (mentor half) — Mentor accepts the pending request with 3 mo.
    // The page-object's click sequence resolves as soon as the click events
    // are dispatched; the backend PUT /api/mentorship-requests/{id}/accept
    // it triggers is still in flight at that point. Without an explicit
    // wait, the subsequent listActiveMentorships GETs can race the accept
    // and observe pre-accept state, which surfaced as a deterministic
    // `toHaveLength(1)` failure when the spec was rewritten to skip the
    // slow UI-login leg that previously masked this race.
    const inbox = new MentorshipInbox(mentorPage);
    await inbox.goto();
    const [acceptResponse] = await Promise.all([
      mentorPage.waitForResponse(
        (res) =>
          /\/api\/mentorship-requests\/\d+\/accept$/.test(res.url())
          && res.request().method() === 'PUT',
        { timeout: 15_000 },
      ),
      inbox.acceptFirstRequest({ months: 3 }),
    ]);
    if (!acceptResponse.ok()) {
      const body = await acceptResponse.text().catch(() => '');
      throw new Error(`accept returned ${acceptResponse.status()}: ${body}`);
    }

    // Both sides should see the new ACTIVE mentorship; pull through API so
    // we can drive the remaining backend-only legs.
    const [mentorMentorships, menteeMentorships] = await Promise.all([
      listActiveMentorships(request, mentor.sessionToken),
      listActiveMentorships(request, mentee.sessionToken),
    ]);
    expect(mentorMentorships).toHaveLength(1);
    expect(menteeMentorships).toHaveLength(1);
    const mentorshipId = mentorMentorships[0].id;
    expect(menteeMentorships[0].id).toBe(mentorshipId);

    // Confirm the inbox no longer shows the pending request.
    const remaining = await listIncomingRequests(request, mentor.sessionToken);
    expect(remaining.filter((r) => r.status === 'PENDING')).toHaveLength(0);

    // -------------------------------------------------------------------------
    // Step 5 — Shared goal precondition. The #335 goal gate refuses meeting /
    // task / milestone creation until the mentorship has a non-blank goal.
    // -------------------------------------------------------------------------
    await setSharedGoal(
      request,
      mentor.sessionToken,
      mentorshipId,
      'Build a portfolio project together end-to-end',
    );

    // -------------------------------------------------------------------------
    // Step 4 — Schedule a meeting (API; create UI is not yet wired).
    // -------------------------------------------------------------------------
    const inOneWeek = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
    const meeting = await createMeeting(request, mentor.sessionToken, mentorshipId, {
      title: 'Kickoff: project goals',
      date: inOneWeek,
      durationMin: 45,
    });
    expect(meeting.id).toBeDefined();

    const confirmed = await confirmMeeting(request, mentee.sessionToken, meeting.id);
    expect(confirmed.status).toBe('CONFIRMED');

    // Smoke-render /schedule on the mentee side. Promote to a hard
    // visibility assertion once the page consumes /api/mentorships/{id}/meetings.
    const schedulePage = new SchedulePage(menteePage);
    await schedulePage.goto();
    await expect(menteePage.locator('h1, .page-title').first()).toBeVisible();

    // -------------------------------------------------------------------------
    // Step 6 — Task assign / submit / review (API).
    // -------------------------------------------------------------------------
    const task = await createTask(request, mentor.sessionToken, mentorshipId, {
      title: 'Read the architecture doc',
      description: 'Skim the high-level overview and note three questions.',
      dueDate: inOneWeek,
    });
    expect(task.status).toBe('PENDING');

    const submitted = await submitTask(request, mentee.sessionToken, task.id, {
      submissionText: 'Done. Three questions noted in the comment thread.',
    });
    expect(submitted.status).toBe('SUBMITTED');

    const reviewed = await reviewTask(request, mentor.sessionToken, task.id, {
      status: 'COMPLETED',
      feedback: 'Solid questions, well-framed. Marking complete.',
    });
    expect(reviewed.status).toBe('COMPLETED');

    // Step 7 — Smoke-render /tasks for the mentee.
    const tasksPage = new TasksPage(menteePage);
    await tasksPage.goto();
    await expect(menteePage.locator('h1, .page-title').first()).toBeVisible();

    // -------------------------------------------------------------------------
    // Step 9 — Publish blog content (API; closest surface is feed-post create).
    // -------------------------------------------------------------------------
    const post = await publishBlogPost(request, mentor.sessionToken, {
      body: 'Reflections from a kickoff session — first mentee onboarded today.',
      hashtags: ['mentorship', 'reflection'],
    });
    expect(post.id).toBeDefined();

    const fetched = await getBlogPost(request, mentor.sessionToken, post.id);
    expect(fetched.body).toContain('Reflections from a kickoff session');
    expect(fetched.hashtags).toEqual(expect.arrayContaining(['mentorship', 'reflection']));

    // Step 10 (light) — the mentee (a different authenticated user) can also
    // fetch the post, satisfying the "visible to authenticated users" half of
    // wiki step 10. Like/comment/profile-listing UI verification is owned by
    // the feed engagement specs, not duplicated here.
    const visibleToMentee = await getBlogPost(request, mentee.sessionToken, post.id);
    expect(visibleToMentee.id).toBe(post.id);
  } finally {
    await mentorCtx.close();
    await menteeCtx.close();
  }
});
