package com.group7.backend.repository;

import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.BanSource;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-fit guarantee for {@code findFollowRecommendationCandidates}'s JPQL
 * against real Postgres. Covers all five filter clauses:
 * <ol>
 *   <li>admin exclusion ({@code type(u) <> Admin})</li>
 *   <li>self exclusion ({@code u.id <> :viewerId})</li>
 *   <li>already-followed exclusion ({@code not exists ... Follow})</li>
 *   <li>banned-user exclusion ({@code not exists ... Ban with lifted_at IS NULL AND expires_at > :now})</li>
 *   <li>private-mentee exclusion ({@code treat(u as Mentee).profileVisibility = true})</li>
 * </ol>
 *
 * <p>The TREAT(u AS Mentee) clause is the load-bearing piece. Hibernate 6.x
 * has had bugs with TREAT under @Inheritance(JOINED) (see HHH-15969). This
 * test verifies our specific schema is unaffected; if a future Hibernate
 * upgrade flips the behaviour, this test fires first.
 *
 * <p>Uses Testcontainers Postgres so the migration's CHECK constraints and
 * column nullability match production. H2's PostgreSQL compatibility mode
 * silently mis-renders {@code TREAT()} under JOINED.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryFollowCandidatesTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private UserRepository userRepository;
    @Autowired private TestEntityManager em;

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-05-12T00:00:00Z");

    @Test
    void admin_excluded_fromCandidateWindow() {
        Mentor viewer = mentor("viewer@test.com");
        Mentor mentor = mentor("mentor@test.com");
        Admin admin = admin("admin@test.com");
        persistAll(viewer, mentor, admin);

        List<User> candidates = userRepository.findFollowRecommendationCandidates(
                viewer.getId(), NOW, PageRequest.of(0, 200));

        assertThat(candidates).extracting(User::getId)
                .containsExactly(mentor.getId())
                .doesNotContain(admin.getId(), viewer.getId());
    }

    @Test
    void mentee_with_profileVisibilityFalse_excluded() {
        // The TREAT(u AS Mentee).profileVisibility load-bearing clause.
        Mentor viewer = mentor("viewer@test.com");
        Mentor publicMentor = mentor("public-mentor@test.com");
        Mentee publicMentee = mentee("public-mentee@test.com", true);
        Mentee privateMentee = mentee("private-mentee@test.com", false);
        persistAll(viewer, publicMentor, publicMentee, privateMentee);

        List<User> candidates = userRepository.findFollowRecommendationCandidates(
                viewer.getId(), NOW, PageRequest.of(0, 200));

        assertThat(candidates).extracting(User::getId)
                .containsExactlyInAnyOrder(
                        publicMentor.getId(),
                        publicMentee.getId())
                .doesNotContain(privateMentee.getId(), viewer.getId());
    }

    @Test
    void banned_user_excluded_whileActiveBan() {
        Mentor viewer = mentor("viewer@test.com");
        Mentor freshMentor = mentor("fresh@test.com");
        Mentor bannedMentor = mentor("banned@test.com");
        persistAll(viewer, freshMentor, bannedMentor);
        persistActiveBan(bannedMentor, NOW.plusDays(7));

        List<User> candidates = userRepository.findFollowRecommendationCandidates(
                viewer.getId(), NOW, PageRequest.of(0, 200));

        assertThat(candidates).extracting(User::getId)
                .containsExactly(freshMentor.getId())
                .doesNotContain(bannedMentor.getId());
    }

    @Test
    void banned_user_reappears_afterBanLifted() {
        Mentor viewer = mentor("viewer@test.com");
        Mentor reformed = mentor("reformed@test.com");
        persistAll(viewer, reformed);
        Ban lifted = persistActiveBan(reformed, NOW.plusDays(7));
        lifted.setLiftedAt(NOW.minusDays(1));
        em.persistAndFlush(lifted);
        em.clear();

        List<User> candidates = userRepository.findFollowRecommendationCandidates(
                viewer.getId(), NOW, PageRequest.of(0, 200));

        assertThat(candidates).extracting(User::getId)
                .containsExactly(reformed.getId());
    }

    @Test
    void banned_user_reappears_afterBanExpired() {
        Mentor viewer = mentor("viewer@test.com");
        Mentor expiredBan = mentor("expired@test.com");
        persistAll(viewer, expiredBan);
        persistActiveBan(expiredBan, NOW.minusDays(1));   // expired in the past

        List<User> candidates = userRepository.findFollowRecommendationCandidates(
                viewer.getId(), NOW, PageRequest.of(0, 200));

        assertThat(candidates).extracting(User::getId)
                .containsExactly(expiredBan.getId());
    }

    @Test
    void admin_is_not_filteredByProfileVisibility_clause() {
        // Defence: the TREAT() clause must NOT accidentally filter admins
        // (they were already rejected by type(u) <> Admin upstream).
        Mentor viewer = mentor("viewer@test.com");
        Mentor someMentor = mentor("m@test.com");
        Admin a = admin("admin@test.com");
        persistAll(viewer, someMentor, a);

        List<User> candidates = userRepository.findFollowRecommendationCandidates(
                viewer.getId(), NOW, PageRequest.of(0, 200));

        assertThat(candidates).extracting(User::getId).containsExactly(someMentor.getId());
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    private Mentor mentor(String email) {
        Mentor m = new Mentor();
        m.setFirstName("F-" + email);
        m.setLastName("L-" + email);
        m.setEmail(email);
        m.setPasswordHash("$2a$10$dummy");
        m.setIsEmailVerified(true);
        m.setMaxMenteeCapacity(5);
        m.setCurrentMenteeCount(0);
        return m;
    }

    private Mentee mentee(String email, boolean visible) {
        Mentee m = new Mentee();
        m.setFirstName("F-" + email);
        m.setLastName("L-" + email);
        m.setEmail(email);
        m.setPasswordHash("$2a$10$dummy");
        m.setIsEmailVerified(true);
        m.setProfileVisibility(visible);
        return m;
    }

    private Admin admin(String email) {
        Admin a = new Admin();
        a.setFirstName("Adm");
        a.setLastName("In");
        a.setEmail(email);
        a.setPasswordHash("$2a$10$dummy");
        a.setIsEmailVerified(true);
        return a;
    }

    private void persistAll(User... users) {
        for (User u : users) {
            em.persistAndFlush(u);
        }
        em.clear();
    }

    private Ban persistActiveBan(User u, OffsetDateTime expiresAt) {
        Ban b = new Ban();
        b.setUser(u);
        b.setReason("test ban");
        b.setSource(BanSource.ADMIN);
        b.setBanCount(1);
        b.setExpiresAt(expiresAt.truncatedTo(ChronoUnit.SECONDS));
        return em.persistAndFlush(b);
    }
}
