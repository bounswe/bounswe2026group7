package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.MeetingType;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.testsupport.AbstractE2ETest;
import com.group7.backend.testsupport.UserHandle;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end (cross-feature) workflow suite (issue #254).
 *
 * <p>Each {@code @Test} drives a complete user journey across multiple
 * services through the same Spring context. Boot once (~15s), four tests
 * at <15s each → well under the 2-minute budget the issue calls for.
 *
 * <p>Anti-flake measures already in place:
 * <ul>
 *   <li>Application clock pinned by {@link com.group7.backend.testsupport.E2EClockConfig}.</li>
 *   <li>Database wiped between tests by {@link AbstractE2ETest#cleanDb()}.</li>
 *   <li>Email side effect mocked; no scheduler beans run in the test profile.</li>
 *   <li>No {@code Thread.sleep}, no {@code @Transactional} (production code
 *       commits across multiple endpoints — Spring rollback would not unwind
 *       cleanly).</li>
 * </ul>
 *
 * <p>Adding a new scenario? See {@code backend/src/test/README.md} for the
 * checklist.
 */
class EndToEndWorkflowTest extends AbstractE2ETest {

    /**
     * Drives every step in the issue's "full mentorship lifecycle":
     * register → verify → login → match → request → accept → goal →
     * meeting (schedule + confirm) → task (assign + submit + review) →
     * rate. Asserts persisted side-effects at the end so a regression in
     * any step surfaces here.
     */
    @Test
    void fullMentorshipLifecycle() throws Exception {
        UserHandle mentor = api.users().asMentor()
                .email("e2e_lifecycle_mentor@test.com")
                .firstName("Mira")
                .registerVerifyAndLogin();
        setMentorCapacity(mentor, 3);

        UserHandle mentee = api.users().asMentee()
                .email("e2e_lifecycle_mentee@test.com")
                .firstName("Mehmet")
                .registerVerifyAndLogin();

        // Match — endpoint is reachable by a mentee with no active mentor.
        // Match scoring depends on profile fields we don't populate here, so
        // we only assert the call succeeds (the response shape is covered by
        // MatchingControllerTest).
        JsonNode matchPage = api.listMentorMatches(mentee);
        assertThat(matchPage.has("content")).isTrue();

        Long requestId = api.requests().from(mentee).to(mentor).message("Hi").create();
        Long mentorshipId = api.acceptRequest(mentor, requestId, 3);

        // Shared goal must be defined before scheduling meetings or tasks
        // (production guards both with GoalRequiredException → 409).
        api.setSharedGoal(mentor, mentorshipId, "Build a portfolio project");

        // Meeting — schedule the day after the pinned clock to satisfy the
        // "start ≥ now − 1 minute" guard in MeetingService.
        OffsetDateTime meetingStart = OffsetDateTime
                .ofInstant(now().plus(Duration.ofDays(1)), ZoneOffset.UTC);
        OffsetDateTime meetingEnd = meetingStart.plusHours(1);
        JsonNode meetingResult = api.meetings().in(mentorshipId)
                .title("Kickoff")
                .startsAt(meetingStart)
                .endsAt(meetingEnd)
                .type(MeetingType.ONLINE)
                .schedule(mentor);
        Long meetingId = meetingResult.get("meetings").get(0).get("id").asLong();
        api.confirmMeeting(mentee, meetingId);

        // Task — due date must satisfy @Future (relative to system clock,
        // not the injected Clock — pick any week-out date).
        OffsetDateTime taskDue = OffsetDateTime
                .ofInstant(now().plus(Duration.ofDays(7)), ZoneOffset.UTC);
        Long taskId = api.tasks().in(mentorshipId)
                .title("Read chapter 1")
                .description("Skim the intro and bring questions.")
                .dueAt(taskDue)
                .assignBy(mentor);
        api.submitTask(mentee, taskId, "Done — chapter notes attached.");
        api.reviewTask(mentor, taskId, TaskStatus.COMPLETED, "Solid summary, well done.");

        // End the mentorship first — #237's rating endpoint requires
        // COMPLETED/CANCELLED status before a mentee can rate.
        api.endMentorship(mentor, mentorshipId, "Goal achieved");

        Long ratingId = api.rateMentor(mentee, mentorshipId,
                5, "Great mentor — supportive and clear.");

        // ── Persisted side-effect assertions ────────────────────────────
        // endMentorship transitioned the mentorship from ACTIVE → COMPLETED.
        assertThat(mentorshipRepository.findById(mentorshipId))
                .isPresent()
                .hasValueSatisfying(m -> assertThat(m.getStatus()).isEqualTo(MentorshipStatus.COMPLETED));
        assertThat(taskRepository.findById(taskId))
                .isPresent()
                .hasValueSatisfying(t -> assertThat(t.getStatus()).isEqualTo(TaskStatus.COMPLETED));
        assertThat(mentorRatingRepository.findById(ratingId))
                .isPresent()
                .hasValueSatisfying(r -> {
                    assertThat(r.getScore()).isEqualTo(5);
                    assertThat(r.getMenteeId()).isEqualTo(mentee.id());
                    assertThat(r.getMentorId()).isEqualTo(mentor.id());
                });
        assertThat(meetingRepository.findById(meetingId)).isPresent();
    }

    /**
     * Three pending-request cancellations cross the default ban threshold
     * ({@code app.bans.cancellationThreshold = 3}); the third cancel should
     * persist a ban row for the mentee. Uses three separate mentors so each
     * pending request is to a distinct counterpart (the production rule
     * forbids two pending requests to the same mentor).
     */
    @Test
    void cancellationPathTriggersBanWhenThresholdExceeded() throws Exception {
        UserHandle mentor1 = api.users().asMentor()
                .email("e2e_ban_mentor1@test.com").firstName("Alice").registerVerifyAndLogin();
        setMentorCapacity(mentor1, 1);
        UserHandle mentor2 = api.users().asMentor()
                .email("e2e_ban_mentor2@test.com").firstName("Bob").registerVerifyAndLogin();
        setMentorCapacity(mentor2, 1);
        UserHandle mentor3 = api.users().asMentor()
                .email("e2e_ban_mentor3@test.com").firstName("Cem").registerVerifyAndLogin();
        setMentorCapacity(mentor3, 1);

        UserHandle mentee = api.users().asMentee()
                .email("e2e_ban_mentee@test.com").firstName("Deniz").registerVerifyAndLogin();

        Long r1 = api.requests().from(mentee).to(mentor1).create();
        api.cancelRequest(mentee, r1);

        Long r2 = api.requests().from(mentee).to(mentor2).create();
        api.cancelRequest(mentee, r2);

        // Third cancel crosses the threshold and persists a ban row.
        Long r3 = api.requests().from(mentee).to(mentor3).create();
        api.cancelRequest(mentee, r3);

        List<Ban> bans = banRepository.findByUser_IdOrderByCreatedAtDesc(mentee.id());
        assertThat(bans)
                .as("third cancellation must trigger an active ban row")
                .isNotEmpty();
        OffsetDateTime now = OffsetDateTime.ofInstant(now(), ZoneOffset.UTC);
        assertThat(bans.get(0).isActive(now)).isTrue();
        assertThat(bans.get(0).getBanCount()).isEqualTo(1);
    }

    /**
     * Mentor rejects a pending request → in-app notification with type
     * {@link NotificationType#REQUEST_REJECTED} appears for the mentee.
     */
    @Test
    void rejectionPathSendsNotification() throws Exception {
        UserHandle mentor = api.users().asMentor()
                .email("e2e_reject_mentor@test.com").firstName("Ela").registerVerifyAndLogin();
        setMentorCapacity(mentor, 1);
        UserHandle mentee = api.users().asMentee()
                .email("e2e_reject_mentee@test.com").firstName("Fatih").registerVerifyAndLogin();

        Long requestId = api.requests().from(mentee).to(mentor).message("Please?").create();
        api.rejectRequest(mentor, requestId);

        JsonNode notes = api.getNotifications(mentee);
        boolean hasRejectionNote = false;
        for (JsonNode n : notes) {
            if (NotificationType.REQUEST_REJECTED.name().equals(n.get("type").asText())) {
                hasRejectionNote = true;
                break;
            }
        }
        assertThat(hasRejectionNote)
                .as("mentee should receive a REQUEST_REJECTED notification; got %s", notes)
                .isTrue();
    }

    /**
     * Scheduling a meeting outside the mentor's recurring availability is
     * not blocked but yields a non-empty {@code warnings[]} array. Asserts
     * the literal warning string from {@code MeetingService:532}.
     */
    @Test
    void meetingOutsideAvailabilityReturnsWarning() throws Exception {
        UserHandle mentor = api.users().asMentor()
                .email("e2e_warn_mentor@test.com").firstName("Gül").registerVerifyAndLogin();
        setMentorCapacity(mentor, 1);
        UserHandle mentee = api.users().asMentee()
                .email("e2e_warn_mentee@test.com").firstName("Hakan").registerVerifyAndLogin();

        // Pinned clock is 2026-05-11T09:00Z (Monday). Mentor is available
        // Mondays 09:00–11:00; we'll schedule outside that window.
        api.availability()
                .slot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))
                .save(mentor);

        Long requestId = api.requests().from(mentee).to(mentor).create();
        Long mentorshipId = api.acceptRequest(mentor, requestId, 3);
        api.setSharedGoal(mentor, mentorshipId, "Practice presentations");

        // Same Monday, 14:00–15:00 UTC → outside the 09:00–11:00 slot.
        OffsetDateTime start = OffsetDateTime.parse("2026-05-11T14:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-05-11T15:00:00Z");
        JsonNode response = api.meetings().in(mentorshipId)
                .title("Out-of-window check-in")
                .startsAt(start)
                .endsAt(end)
                .type(MeetingType.ONLINE)
                .schedule(mentor);

        JsonNode warnings = response.get("warnings");
        assertThat(warnings)
                .as("availability warning array should be present and non-empty")
                .isNotNull();
        assertThat(warnings.isArray()).isTrue();
        assertThat(warnings.size())
                .as("at least one warning expected for an out-of-window meeting; got %s", warnings)
                .isGreaterThanOrEqualTo(1);
        boolean matched = false;
        for (JsonNode w : warnings) {
            if (w.asText().contains("outside the mentor's availability window")) {
                matched = true;
                break;
            }
        }
        assertThat(matched)
                .as("expected literal availability warning string; got %s", warnings)
                .isTrue();
    }
}
