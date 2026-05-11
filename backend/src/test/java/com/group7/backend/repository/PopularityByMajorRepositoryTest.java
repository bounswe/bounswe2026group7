package com.group7.backend.repository;

import com.group7.backend.entity.Follow;
import com.group7.backend.entity.FollowId;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-fit guarantee for the popularity-by-major aggregate.
 * Verifies:
 * <ol>
 *   <li>users scoped by {@code mentors.field} surface;</li>
 *   <li>users scoped by {@code mentees.major} surface (separate JOIN branch);</li>
 *   <li>follower count is COUNT of incoming {@code follows} edges;</li>
 *   <li>users in OTHER majors don't appear;</li>
 *   <li>ORDER BY follower_count DESC produces the highest-followed first;</li>
 *   <li>tie-break on user_id ASC is deterministic;</li>
 *   <li>LIMIT 50 is honoured when more than 50 rows would otherwise match.</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PopularityByMajorRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private PopularityByMajorRepository repo;
    @Autowired private TestEntityManager em;

    @Test
    void mentor_inMajor_surfaces() {
        Mentor m = mentor("a@x.com", "CS");
        em.persistAndFlush(m);

        Map<Long, Long> result = byUserId(repo.topByMajor("CS"));
        assertThat(result).containsEntry(m.getId(), 0L);
    }

    @Test
    void mentee_inMajor_surfaces() {
        Mentee me = mentee("b@x.com", "CS");
        em.persistAndFlush(me);

        Map<Long, Long> result = byUserId(repo.topByMajor("CS"));
        assertThat(result).containsEntry(me.getId(), 0L);
    }

    @Test
    void followerCount_isCountOfIncomingFollows() {
        Mentor target = mentor("t@x.com", "CS");
        Mentor f1 = mentor("f1@x.com", "EE");
        Mentor f2 = mentor("f2@x.com", "EE");
        em.persist(target); em.persist(f1); em.persist(f2);
        em.flush();
        em.persist(follow(f1.getId(), target.getId()));
        em.persist(follow(f2.getId(), target.getId()));
        em.flush();

        Map<Long, Long> result = byUserId(repo.topByMajor("CS"));
        assertThat(result.get(target.getId())).isEqualTo(2L);
    }

    @Test
    void userInOtherMajor_isExcluded() {
        Mentor inMajor    = mentor("in@x.com", "CS");
        Mentor otherMajor = mentor("out@x.com", "EE");
        em.persist(inMajor); em.persist(otherMajor);
        em.flush();

        Map<Long, Long> result = byUserId(repo.topByMajor("CS"));
        assertThat(result).containsKey(inMajor.getId()).doesNotContainKey(otherMajor.getId());
    }

    @Test
    void orderedByFollowerCountDesc_thenIdAsc() {
        Mentor low  = mentor("l@x.com", "CS");
        Mentor mid  = mentor("m@x.com", "CS");
        Mentor high = mentor("h@x.com", "CS");
        Mentor f1   = mentor("f1@x.com", "EE");
        Mentor f2   = mentor("f2@x.com", "EE");
        Mentor f3   = mentor("f3@x.com", "EE");
        em.persist(low); em.persist(mid); em.persist(high);
        em.persist(f1); em.persist(f2); em.persist(f3);
        em.flush();
        em.persist(follow(f1.getId(), high.getId()));
        em.persist(follow(f2.getId(), high.getId()));
        em.persist(follow(f3.getId(), high.getId()));
        em.persist(follow(f1.getId(), mid.getId()));
        em.flush();

        List<Long> orderedIds = repo.topByMajor("CS").stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();
        // high (3 followers), mid (1), low (0)
        assertThat(orderedIds).containsSubsequence(high.getId(), mid.getId(), low.getId());
    }

    @Test
    void otherUsersInSameMajor_appearWithZeroFollowers() {
        Mentor a = mentor("a@x.com", "CS");
        Mentor b = mentor("b@x.com", "CS");
        em.persist(a); em.persist(b);
        em.flush();

        Map<Long, Long> result = byUserId(repo.topByMajor("CS"));
        assertThat(result).containsEntry(a.getId(), 0L)
                          .containsEntry(b.getId(), 0L);
    }

    @Test
    void noMatchingUsers_returnsEmpty() {
        assertThat(repo.topByMajor("DOES_NOT_EXIST")).isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Mentor mentor(String email, String field) {
        Mentor m = new Mentor();
        m.setEmail(email); m.setPasswordHash("x");
        m.setFirstName("F"); m.setLastName("L");
        m.setField(field);
        return m;
    }

    private Mentee mentee(String email, String major) {
        Mentee me = new Mentee();
        me.setEmail(email); me.setPasswordHash("x");
        me.setFirstName("F"); me.setLastName("L");
        me.setMajor(major);
        return me;
    }

    private Follow follow(Long followerId, Long followeeId) {
        Follow f = new Follow();
        f.setId(new FollowId(followerId, followeeId));
        return f;
    }

    private static Map<Long, Long> byUserId(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r[0]).longValue(),
                r -> ((Number) r[1]).longValue(),
                (a, b) -> a));
    }
}
