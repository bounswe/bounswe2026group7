import { test, expect } from '@playwright/test';
import {
  resetDb,
  seedMentor,
  seedMentee,
  login,
} from '../fixtures/apiClient.js';
import {
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
import { LoginPage } from '../pages/LoginPage.js';
import { ExplorePage } from '../pages/ExplorePage.js';
import { RequestMentorshipModal } from '../pages/RequestMentorshipModal.js';
import { MentorshipInbox } from '../pages/MentorshipInbox.js';
import { SchedulePage } from '../pages/SchedulePage.js';
import { TasksPage } from '../pages/TasksPage.js';

/**
 * AT-02: discover mentor → request → accept → schedule → meeting → task
 * assign / submit / feedback → blog publish.
 *
 * Hybrid spec by necessity:
 *   - Discover / request / accept run through the real UI (the only legs that
 *     actually have interactive screens today).
 *   - Schedule, task assign/submit/feedback, and blog publish go through the
 *     backend HTTP API directly — the corresponding pages are either
 *     mock-driven (SchedulePage, TasksPage source data from
 *     `services/mentorshipMocks.js`) or unbuilt (no blog/feed publish UI).
 *     The backend endpoints exist and are exercised end-to-end; only the UI
 *     verb is missing.
 *
 * Two browser contexts are used so the mentor and mentee can be active
 * simultaneously without juggling logout/login on a single context. Each
 * spec resets the DB up front so the run is order-independent across the
 * three browser projects.
 */

async function loginViaUi(page, { email, password }) {
  const loginPage = new LoginPage(page);
  await loginPage.goto();
  // Surface the underlying /api/auth/login outcome — without this, a 401
  // from the backend just leaves us stuck on /login and the test only
  // reports "URL didn't change" with no signal as to why.
  const loginResponsePromise = page.waitForResponse(
    res => res.url().endsWith('/api/auth/login') && res.request().method() === 'POST',
    { timeout: 10_000 },
  );
  await loginPage.signIn({ email, password });
  const loginResponse = await loginResponsePromise;
  if (!loginResponse.ok()) {
    const body = await loginResponse.text().catch(() => '');
    throw new Error(`UI login for ${email} returned ${loginResponse.status()}: ${body}`);
  }
  await expect(page).toHaveURL(/\/home$/);
}

test('AT-02 mentorship lifecycle + blog publish', async ({ browser, request }) => {
  await resetDb(request);

  const mentor = await seedMentor(request);
  const mentee = await seedMentee(request);

  // Two isolated browser contexts so both sides stay logged in concurrently.
  const mentorCtx = await browser.newContext();
  const menteeCtx = await browser.newContext();
  const mentorPage = await mentorCtx.newPage();
  const menteePage = await menteeCtx.newPage();

  try {
    // 1. Mentee logs in via UI, opens /explore.
    await loginViaUi(menteePage, mentee);
    const explore = new ExplorePage(menteePage);
    await explore.goto();

    // 2. Send mentorship request through the modal.
    await explore.openRequestForMentor(mentor.id);
    const modal = new RequestMentorshipModal(menteePage);
    await expect(modal.root()).toBeVisible();
    await modal.fillAndSubmit('Hi, I would love your guidance on backend systems.');
    // Modal closes on success; the "Request Sent" label appears on the card.
    await expect(menteePage.getByTestId(`explore-send-request-${mentor.id}`)).toContainText(/sent/i);

    // 3. Mentor logs in via UI and accepts the pending request.
    await loginViaUi(mentorPage, mentor);
    const inbox = new MentorshipInbox(mentorPage);
    await inbox.goto();
    await inbox.acceptFirstRequest({ months: 3 });

    // After accept, the active mentorship exists. Pull tokens for direct API work.
    const mentorAuth = await login(request, mentor.email, mentor.password);
    const menteeAuth = await login(request, mentee.email, mentee.password);

    // Sanity: both sides see the new ACTIVE mentorship.
    const [mentorMentorships, menteeMentorships] = await Promise.all([
      listActiveMentorships(request, mentorAuth.sessionToken),
      listActiveMentorships(request, menteeAuth.sessionToken),
    ]);
    expect(mentorMentorships.length).toBe(1);
    expect(menteeMentorships.length).toBe(1);
    const mentorshipId = mentorMentorships[0].id;
    expect(menteeMentorships[0].id).toBe(mentorshipId);

    // (Side check: there are no longer any incoming pending requests for the mentor.)
    const remainingRequests = await listIncomingRequests(request, mentorAuth.sessionToken);
    expect(remainingRequests.filter(r => r.status === 'PENDING')).toHaveLength(0);

    // Shared goal is the precondition for any meeting / task / milestone write
    // since #335 added the gate; set it as the mentor before scheduling.
    await setSharedGoal(
      request,
      mentorAuth.sessionToken,
      mentorshipId,
      'Build a portfolio project together end-to-end',
    );

    // 4. Mentor schedules a meeting (API leg — no create UI on /schedule yet).
    const inOneWeek = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
    const meeting = await createMeeting(request, mentorAuth.sessionToken, mentorshipId, {
      title: 'Kickoff: project goals',
      date: inOneWeek,
      durationMin: 45,
    });
    expect(meeting.id).toBeDefined();

    // 5. Mentee confirms the meeting.
    const confirmed = await confirmMeeting(request, menteeAuth.sessionToken, meeting.id);
    expect(confirmed.status).toBe('CONFIRMED');

    // /schedule is mock-driven today, so we only smoke-check it renders for the
    // mentee. Once the page is wired to /api/mentorships/{id}/meetings, the
    // assertion below should be promoted to `meetingCard(meeting.id).toBeVisible()`.
    const schedulePage = new SchedulePage(menteePage);
    await schedulePage.goto();
    await expect(menteePage.locator('h1, .page-title').first()).toBeVisible();

    // 6. Mentor assigns a task; mentee submits; mentor reviews.
    const task = await createTask(request, mentorAuth.sessionToken, mentorshipId, {
      title: 'Read the architecture doc',
      description: 'Skim the high-level overview and note three questions.',
      dueDate: inOneWeek,
    });
    expect(task.status).toBe('PENDING');

    const submitted = await submitTask(request, menteeAuth.sessionToken, task.id, {
      submissionText: 'Done. Three questions noted in the comment thread.',
    });
    expect(submitted.status).toBe('SUBMITTED');

    const reviewed = await reviewTask(request, mentorAuth.sessionToken, task.id, {
      status: 'COMPLETED',
      feedback: 'Solid questions, well-framed. Marking complete.',
    });
    expect(reviewed.status).toBe('COMPLETED');

    // /tasks is also mock-driven; smoke-render only for now. Same upgrade
    // path as /schedule once the page consumes the real backend.
    const tasksPage = new TasksPage(menteePage);
    await tasksPage.goto();
    await expect(menteePage.locator('h1, .page-title').first()).toBeVisible();

    // 7. Mentor publishes a blog post (POST /api/feed/posts is the closest
    // surface to "blog publish" today; there's no dedicated blog UI yet).
    const post = await publishBlogPost(request, mentorAuth.sessionToken, {
      body: 'Reflections from a kickoff session — first mentee onboarded today.',
      hashtags: ['mentorship', 'reflection'],
    });
    expect(post.id).toBeDefined();

    const fetched = await getBlogPost(request, mentorAuth.sessionToken, post.id);
    expect(fetched.body).toContain('Reflections from a kickoff session');
    expect(fetched.hashtags).toEqual(expect.arrayContaining(['mentorship', 'reflection']));
  } finally {
    await mentorCtx.close();
    await menteeCtx.close();
  }
});
