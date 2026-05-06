package com.group7.backend.repository;

import com.group7.backend.entity.LastMatchNotification;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres coverage for the eligibility queries added in #273
 * ({@link MenteeRepository#findUnattachedIds()},
 * {@link MentorRepository#findIdsWithCapacity()}) plus the
 * {@link LastMatchNotificationRepository} round-trip.
 *
 * <p>Pinned to the {@code test} profile because the migrations expect Postgres
 * (Flyway-validated). {@code @Transactional} rolls back DB writes between
 * tests; the explicit {@code cleanDb()} prevents leakage from other test
 * classes that don't roll back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MatchNotificationEligibilityQueriesTest {

    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LastMatchNotificationRepository stateRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        stateRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── findUnattachedIds (mentee eligibility) ───────────────────────────────

    @Test
    void findUnattachedIds_returnsOnlyMenteesWithoutActiveMentor() {
        // Set up: 1 attached mentee, 2 unattached. Plus a mentor (just to seed
        // a real users.id for the activeMentorId FK target).
        Mentor mentor = saveMentor("mn273_eligm@example.com");
        Mentee attached = newMentee("mn273_elig_a", "Alice");
        attached.setActiveMentorId(mentor.getId());
        menteeRepository.save(attached);
        Mentee unattached1 = menteeRepository.save(newMentee("mn273_elig_b", "Bob"));
        Mentee unattached2 = menteeRepository.save(newMentee("mn273_elig_c", "Cara"));

        List<Long> ids = menteeRepository.findUnattachedIds();

        assertThat(ids)
                .as("only mentees with activeMentorId IS NULL show up")
                .containsExactlyInAnyOrder(unattached1.getId(), unattached2.getId());
    }

    @Test
    void findUnattachedIds_isOrderedByIdAscending() {
        // Test determinism: scheduler iterates this list, so we want a stable order.
        Mentee a = menteeRepository.save(newMentee("mn273_eord_a", "A"));
        Mentee b = menteeRepository.save(newMentee("mn273_eord_b", "B"));
        Mentee c = menteeRepository.save(newMentee("mn273_eord_c", "C"));

        List<Long> ids = menteeRepository.findUnattachedIds();

        assertThat(ids).containsExactly(a.getId(), b.getId(), c.getId());
    }

    @Test
    void findUnattachedIds_returnsEmptyListWhenNoMenteesExist() {
        assertThat(menteeRepository.findUnattachedIds()).isEmpty();
    }

    // ── findIdsWithCapacity (mentor eligibility) ─────────────────────────────

    @Test
    void findIdsWithCapacity_returnsOnlyMentorsBelowCapacity() {
        Mentor empty = saveMentorWithCapacity("mn273_capa_a@example.com", 3, 0);
        Mentor partial = saveMentorWithCapacity("mn273_capa_b@example.com", 3, 2);
        saveMentorWithCapacity("mn273_capa_c@example.com", 2, 2);  // at cap → excluded

        List<Long> ids = mentorRepository.findIdsWithCapacity();

        assertThat(ids).containsExactlyInAnyOrder(empty.getId(), partial.getId());
    }

    @Test
    void findIdsWithCapacity_excludesMentorsAtExactlyMaxCapacity() {
        // Boundary: currentMenteeCount == maxMenteeCapacity → strictly excluded
        // (we use < not <=). Otherwise a mentor who *just* filled would receive
        // a confusing notification.
        saveMentorWithCapacity("mn273_capb@example.com", 3, 3);

        assertThat(mentorRepository.findIdsWithCapacity()).isEmpty();
    }

    @Test
    void findIdsWithCapacity_isOrderedByIdAscending() {
        Mentor a = saveMentorWithCapacity("mn273_cord_a@example.com", 3, 0);
        Mentor b = saveMentorWithCapacity("mn273_cord_b@example.com", 3, 0);
        Mentor c = saveMentorWithCapacity("mn273_cord_c@example.com", 3, 0);

        List<Long> ids = mentorRepository.findIdsWithCapacity();

        assertThat(ids).containsExactly(a.getId(), b.getId(), c.getId());
    }

    // ── LastMatchNotificationRepository round-trip ───────────────────────────

    @Test
    void lastMatchNotification_savesAndReloadsCleanly() {
        Mentee user = menteeRepository.save(newMentee("mn273_state_u", "Sam"));
        Mentor counterpart = saveMentor("mn273_state_c@example.com");

        LastMatchNotification row = new LastMatchNotification();
        row.setUserId(user.getId());
        row.setNotifiedMatchUserId(counterpart.getId());
        row.setSentAt(OffsetDateTime.parse("2026-05-07T09:00:00Z"));
        stateRepository.save(row);

        LastMatchNotification loaded = stateRepository.findById(user.getId()).orElseThrow();
        assertThat(loaded.getUserId()).isEqualTo(user.getId());
        assertThat(loaded.getNotifiedMatchUserId()).isEqualTo(counterpart.getId());
        assertThat(loaded.getSentAt()).isEqualTo(OffsetDateTime.parse("2026-05-07T09:00:00Z"));
        assertThat(loaded.getVersion()).isEqualTo(0L);
    }

    @Test
    void lastMatchNotification_versionIncrementsOnUpdate() {
        // Defends the @Version annotation against a future Lombok / JPA refactor
        // that accidentally drops it. Without a working @Version, two scheduler
        // instances racing on UPDATE would silently double-publish.
        Mentee user = menteeRepository.save(newMentee("mn273_ver_u", "Ver"));
        LastMatchNotification row = new LastMatchNotification();
        row.setUserId(user.getId());
        row.setNotifiedMatchUserId(99L);
        row.setSentAt(OffsetDateTime.parse("2026-05-07T09:00:00Z"));
        // saveAndFlush returns the merged/managed copy. Capturing it gives us
        // the entity Hibernate has wired into the persistence context, which
        // is what carries the post-write @Version value.
        LastMatchNotification managed = stateRepository.saveAndFlush(row);
        long initialVersion = managed.getVersion();

        managed.setNotifiedMatchUserId(100L);
        managed = stateRepository.saveAndFlush(managed);

        assertThat(managed.getVersion())
                .as("@Version increments on UPDATE")
                .isEqualTo(initialVersion + 1);
    }

    @Test
    void lastMatchNotification_cascadesOnUserDelete() {
        // ON DELETE CASCADE on user_id PK — when the recipient is deleted, the
        // state row is reaped automatically. Defends the V20 migration's FK
        // choice; if a reviewer ever drops the cascade clause, this test fails.
        //
        // Hibernate's L1 cache holds the state row after we save it; checking
        // via the repository's findById would return the cached entity even
        // after the DB-side cascade has fired. We bypass the cache by querying
        // through JdbcTemplate (raw SQL — sees actual DB state) after clearing
        // the persistence context to flush pending writes.
        Mentee user = menteeRepository.save(newMentee("mn273_casc", "Cas"));
        LastMatchNotification row = new LastMatchNotification();
        row.setUserId(user.getId());
        row.setNotifiedMatchUserId(99L);
        row.setSentAt(OffsetDateTime.parse("2026-05-07T09:00:00Z"));
        stateRepository.saveAndFlush(row);

        userRepository.delete(user);
        userRepository.flush();
        entityManager.clear();   // drop L1 cache so subsequent reads hit the DB

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM last_match_notifications WHERE user_id = ?",
                Integer.class, user.getId());
        assertThat(remaining).isZero();
    }

    @Test
    void lastMatchNotification_acceptsStaleNotifiedMatchUserId() {
        // No FK on notified_match_user_id by design — see V20 migration comment.
        // The column tolerates stale values that point at deleted users; the
        // scheduler's equality check treats stale != current as "fire as if
        // first-time". Asserting the schema actually allows it.
        Mentee user = menteeRepository.save(newMentee("mn273_stale", "Sta"));
        LastMatchNotification row = new LastMatchNotification();
        row.setUserId(user.getId());
        row.setNotifiedMatchUserId(99_999L);  // never existed
        row.setSentAt(OffsetDateTime.parse("2026-05-07T09:00:00Z"));

        // Should not throw — no FK to violate.
        assertThat(stateRepository.saveAndFlush(row)).isNotNull();
    }

    @Test
    void lastMatchNotification_userIdFkBlocksOrphanInsert() {
        // The recipient PK *does* have a FK; inserting for a non-existent user
        // must be rejected so we don't accumulate dead state rows. Defends the
        // user_id REFERENCES users(id) clause.
        LastMatchNotification orphan = new LastMatchNotification();
        orphan.setUserId(98_765L);  // never existed
        orphan.setNotifiedMatchUserId(1L);
        orphan.setSentAt(OffsetDateTime.parse("2026-05-07T09:00:00Z"));

        assertThatThrownBy(() -> stateRepository.saveAndFlush(orphan))
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("foreign key");
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Mentee newMentee(String suffix, String firstName) {
        Mentee m = new Mentee();
        m.setFirstName(firstName);
        m.setLastName("L");
        m.setEmail(suffix + "@example.com");
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        return m;
    }

    private Mentor saveMentor(String email) {
        Mentor m = new Mentor();
        m.setFirstName("M");
        m.setLastName("L");
        m.setEmail(email);
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        return mentorRepository.save(m);
    }

    private Mentor saveMentorWithCapacity(String email, int max, int current) {
        Mentor m = new Mentor();
        m.setFirstName("M");
        m.setLastName("L");
        m.setEmail(email);
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        m.setMaxMenteeCapacity(max);
        m.setCurrentMenteeCount(current);
        return mentorRepository.save(m);
    }
}
