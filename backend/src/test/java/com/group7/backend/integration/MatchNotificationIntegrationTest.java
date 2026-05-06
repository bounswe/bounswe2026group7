package com.group7.backend.integration;

import com.group7.backend.entity.LastMatchNotification;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.repository.LastMatchNotificationRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.scheduler.MatchNotificationScheduler;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the scheduled match-notification path (#273) against
 * a real Postgres. Overrides the default test profile (which disables the
 * scheduler) to instantiate the scheduler bean while keeping the cron disabled
 * via {@code cron="-"} — tests invoke {@code scheduler.notifyChangedMatches()}
 * directly and never wait for an actual cron firing.
 *
 * <p>The {@code AFTER_COMMIT} listener that persists Notification rows runs
 * on a different thread (it's {@code @Async}), so assertions on the
 * notifications table are wrapped in {@link Awaitility} to wait briefly for
 * the listener to catch up.
 */
@SpringBootTest(properties = {
        "app.matching.notification.enabled=true",
        "app.matching.notification.cron=-"
})
@ActiveProfiles("test")
class MatchNotificationIntegrationTest {

    @Autowired private MatchNotificationScheduler scheduler;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private LastMatchNotificationRepository stateRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;

    @BeforeEach
    void cleanDb() {
        notificationRepository.deleteAll();
        stateRepository.deleteAll();
        menteeAvailabilitySlotRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Mentor newMentor(String suffix, String firstName) {
        Mentor m = new Mentor();
        m.setFirstName(firstName);
        m.setLastName("L");
        m.setEmail("mn273_m_" + suffix + "@example.com");
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        m.setInterests(List.of("AI", "Systems"));
        m.setPreferredMenteeSkills(List.of("Java", "Python"));
        m.setField("Computer Science");
        return m;
    }

    private Mentee newMentee(String suffix, String firstName) {
        Mentee m = new Mentee();
        m.setFirstName(firstName);
        m.setLastName("L");
        m.setEmail("mn273_me_" + suffix + "@example.com");
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        m.setInterests(List.of("AI"));
        m.setSkills(List.of("Java"));
        m.setMajor("Computer Science");
        return m;
    }

    private long countMatchFoundFor(Long recipientId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(recipientId))
                .filter(n -> n.getType() == NotificationType.MATCH_FOUND)
                .count();
    }

    private void awaitNotificationsFor(Long recipientId, long expected) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(countMatchFoundFor(recipientId)).isEqualTo(expected));
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void firstRun_publishesAndUpsertsForEachEligibleUser() {
        Mentee mentee = menteeRepository.save(newMentee("a", "Alice"));
        Mentor mentor = mentorRepository.save(newMentor("a", "Bob"));

        scheduler.notifyChangedMatches();

        // Mentee got notified about the only mentor; mentor got notified about
        // the only mentee. Both state rows are populated with the counterpart's id.
        awaitNotificationsFor(mentee.getId(), 1);
        awaitNotificationsFor(mentor.getId(), 1);

        LastMatchNotification menteeState = stateRepository.findById(mentee.getId()).orElseThrow();
        assertThat(menteeState.getNotifiedMatchUserId()).isEqualTo(mentor.getId());
        LastMatchNotification mentorState = stateRepository.findById(mentor.getId()).orElseThrow();
        assertThat(mentorState.getNotifiedMatchUserId()).isEqualTo(mentee.getId());
    }

    @Test
    void secondRun_skipsWhenTopMatchUnchanged() {
        Mentee mentee = menteeRepository.save(newMentee("b", "Alice"));
        mentorRepository.save(newMentor("b", "Bob"));

        scheduler.notifyChangedMatches();
        awaitNotificationsFor(mentee.getId(), 1);

        scheduler.notifyChangedMatches();

        // No new notification — top match hasn't changed since the first run.
        // Wait briefly to confirm no late-firing listener bumps the count.
        Awaitility.await().pollDelay(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(countMatchFoundFor(mentee.getId())).isEqualTo(1));
    }

    @Test
    void run_publishesAgainWhenTopMatchChanges() {
        Mentee mentee = menteeRepository.save(newMentee("c", "Alice"));
        mentorRepository.save(newMentor("c1", "Bob"));

        scheduler.notifyChangedMatches();
        awaitNotificationsFor(mentee.getId(), 1);

        // A new mentor with a stronger profile registers (matches mentee on
        // both interest AND skill, which the rule-based ranker scores higher
        // than just-interest match).
        Mentor stronger = newMentor("c2", "Carol");
        stronger.setPreferredMenteeMajor("Computer Science");  // adds another match category
        mentorRepository.save(stronger);

        scheduler.notifyChangedMatches();

        awaitNotificationsFor(mentee.getId(), 2);
        LastMatchNotification state = stateRepository.findById(mentee.getId()).orElseThrow();
        assertThat(state.getNotifiedMatchUserId()).isEqualTo(stronger.getId());
    }

    @Test
    void run_skipsMenteeWithActiveMentor() {
        Mentee mentee = newMentee("d", "Alice");
        Mentor mentor = mentorRepository.save(newMentor("d", "Bob"));
        mentee.setActiveMentorId(mentor.getId());  // already partnered → ineligible
        menteeRepository.save(mentee);

        scheduler.notifyChangedMatches();

        // Mentee is excluded by findUnattachedIds; no notification, no state row.
        Awaitility.await().pollDelay(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    assertThat(countMatchFoundFor(mentee.getId())).isZero();
                    assertThat(stateRepository.findById(mentee.getId())).isEmpty();
                });
    }

    @Test
    void run_skipsMentorAtFullCapacity() {
        Mentor mentor = newMentor("e", "Bob");
        mentor.setMaxMenteeCapacity(1);
        mentor.setCurrentMenteeCount(1);  // full → ineligible
        mentorRepository.save(mentor);
        menteeRepository.save(newMentee("e", "Alice"));

        scheduler.notifyChangedMatches();

        // Mentor is excluded by findIdsWithCapacity; no notification, no state row.
        Awaitility.await().pollDelay(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    assertThat(countMatchFoundFor(mentor.getId())).isZero();
                    assertThat(stateRepository.findById(mentor.getId())).isEmpty();
                });
    }

    @Test
    void firstNotificationFiresWhenStateRowReferencesDeletedCounterpart() {
        // Simulates the "no FK on notified_match_user_id" rationale: the
        // previously-notified counterpart was deleted at some point. The
        // column carries a stale id; the next run sees stale != current_top
        // and fires (correctly treated as first-time-for-the-new-top).
        Mentee mentee = menteeRepository.save(newMentee("f", "Alice"));
        Mentor mentor = mentorRepository.save(newMentor("f", "Bob"));

        // Pre-seed state pointing at a non-existent user id.
        LastMatchNotification stale = new LastMatchNotification();
        stale.setUserId(mentee.getId());
        stale.setNotifiedMatchUserId(99_999L);  // never existed
        stale.setSentAt(java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        stateRepository.save(stale);

        scheduler.notifyChangedMatches();

        awaitNotificationsFor(mentee.getId(), 1);
        LastMatchNotification refreshed = stateRepository.findById(mentee.getId()).orElseThrow();
        assertThat(refreshed.getNotifiedMatchUserId()).isEqualTo(mentor.getId());
    }
}
