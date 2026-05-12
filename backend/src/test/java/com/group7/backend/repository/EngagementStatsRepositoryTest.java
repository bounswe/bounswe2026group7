package com.group7.backend.repository;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostComment;
import com.group7.backend.entity.FeedPostShare;
import com.group7.backend.entity.Mentor;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-fit guarantee for the engagement aggregate query against real
 * Postgres. The query uses {@code COUNT(*) FILTER (WHERE …)} which H2's
 * Postgres-compat mode silently mis-parses, so Testcontainers is
 * mandatory here.
 *
 * <p>Verifies all five behaviour clauses:
 * <ol>
 *   <li>per-source aggregation (posts / comments / shares counted separately);</li>
 *   <li>per-author grouping (multiple events per author roll up correctly);</li>
 *   <li>{@code last_active = MAX(created_at)} across all three sources;</li>
 *   <li>{@code WHERE deleted_at IS NULL} excludes soft-deleted posts/comments;</li>
 *   <li>{@code WHERE created_at >= :since} excludes events outside the window.</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class EngagementStatsRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private EngagementStatsRepository repo;
    @Autowired private TestEntityManager em;

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-05-11T12:00:00Z");
    private static final OffsetDateTime SINCE = NOW.minusDays(30);

    @Test
    void perAuthor_aggregatesAllThreeSources() {
        Mentor alice = saveMentor("alice@x.com");
        Mentor bob   = saveMentor("bob@x.com");

        FeedPost alicePost1 = savePost(alice.getId(), NOW.minusDays(5), false);
        FeedPost alicePost2 = savePost(alice.getId(), NOW.minusDays(1), false);
        saveComment(alice.getId(), alicePost1.getId(), NOW.minusDays(3), false);
        saveShare(alice.getId(),   alicePost2.getId(), NOW.minusDays(2));
        saveShare(alice.getId(),   alicePost1.getId(), NOW.minusHours(6));   // most recent
        savePost(bob.getId(),      NOW.minusDays(10), false);

        em.flush();

        Map<Long, Object[]> byId = indexById(repo.aggregateByAuthor(
                Set.of(alice.getId(), bob.getId()), SINCE));

        assertThat(((Number) byId.get(alice.getId())[1]).longValue()).isEqualTo(2L);  // posts
        assertThat(((Number) byId.get(alice.getId())[2]).longValue()).isEqualTo(1L);  // comments
        assertThat(((Number) byId.get(alice.getId())[3]).longValue()).isEqualTo(2L);  // shares

        assertThat(((Number) byId.get(bob.getId())[1]).longValue()).isEqualTo(1L);
        assertThat(((Number) byId.get(bob.getId())[2]).longValue()).isEqualTo(0L);
        assertThat(((Number) byId.get(bob.getId())[3]).longValue()).isEqualTo(0L);
    }

    @Test
    void lastActive_isMaxAcrossAllSources() {
        Mentor alice = saveMentor("la@x.com");
        FeedPost p = savePost(alice.getId(), NOW.minusDays(10), false);
        saveComment(alice.getId(), p.getId(), NOW.minusDays(5),  false);
        saveShare(alice.getId(),   p.getId(), NOW.minusHours(1));   // most recent

        em.flush();

        Map<Long, Object[]> byId = indexById(repo.aggregateByAuthor(Set.of(alice.getId()), SINCE));
        OffsetDateTime lastActive = toOdt(byId.get(alice.getId())[4]);
        // tolerate timestamp truncation but require it lands within the last hour
        assertThat(lastActive).isAfter(NOW.minusHours(2));
    }

    @Test
    void softDeletedPosts_areExcluded() {
        Mentor alice = saveMentor("sd1@x.com");
        savePost(alice.getId(), NOW.minusDays(5), false);
        savePost(alice.getId(), NOW.minusDays(3), true);    // soft-deleted

        em.flush();

        Map<Long, Object[]> byId = indexById(repo.aggregateByAuthor(Set.of(alice.getId()), SINCE));
        assertThat(((Number) byId.get(alice.getId())[1]).longValue()).isEqualTo(1L);
    }

    @Test
    void softDeletedComments_areExcluded() {
        Mentor alice = saveMentor("sd2@x.com");
        FeedPost p = savePost(alice.getId(), NOW.minusDays(5), false);
        saveComment(alice.getId(), p.getId(), NOW.minusDays(3), false);
        saveComment(alice.getId(), p.getId(), NOW.minusDays(2), true);   // soft-deleted

        em.flush();

        Map<Long, Object[]> byId = indexById(repo.aggregateByAuthor(Set.of(alice.getId()), SINCE));
        assertThat(((Number) byId.get(alice.getId())[2]).longValue()).isEqualTo(1L);
    }

    @Test
    void eventsBeforeSinceWindow_areExcluded() {
        Mentor alice = saveMentor("win@x.com");
        savePost(alice.getId(), NOW.minusDays(60), false);   // outside 30d window
        savePost(alice.getId(), NOW.minusDays(10), false);   // inside
        saveComment(alice.getId(), savePost(alice.getId(), NOW.minusDays(60), false).getId(),
                    NOW.minusDays(40), false);               // outside

        em.flush();

        Map<Long, Object[]> byId = indexById(repo.aggregateByAuthor(Set.of(alice.getId()), SINCE));
        // posts: 1 (the in-window post, plus another savePost(60d-old) for the comment FK
        // but THAT one is outside since-window so not counted). Comment is 40d old → out.
        assertThat(((Number) byId.get(alice.getId())[1]).longValue()).isEqualTo(1L);
        assertThat(((Number) byId.get(alice.getId())[2]).longValue()).isEqualTo(0L);
    }

    @Test
    void candidatesOutsideIdList_areExcluded() {
        Mentor alice = saveMentor("oid1@x.com");
        Mentor mallory = saveMentor("oid2@x.com");
        savePost(alice.getId(),   NOW.minusDays(2), false);
        savePost(mallory.getId(), NOW.minusDays(2), false);

        em.flush();

        Map<Long, Object[]> byId = indexById(repo.aggregateByAuthor(Set.of(alice.getId()), SINCE));
        assertThat(byId).containsOnlyKeys(alice.getId());
    }

    @Test
    void candidatesWithNoActivity_areAbsentFromResult() {
        Mentor alice = saveMentor("dorm1@x.com");
        Mentor dormant = saveMentor("dorm2@x.com");
        savePost(alice.getId(), NOW.minusDays(2), false);

        em.flush();

        Map<Long, Object[]> byId = indexById(repo.aggregateByAuthor(
                Set.of(alice.getId(), dormant.getId()), SINCE));
        // dormant author returns no row — service maps absence to EngagementStats.empty()
        assertThat(byId).containsOnlyKeys(alice.getId());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Mentor saveMentor(String email) {
        Mentor m = new Mentor();
        m.setEmail(email);
        m.setPasswordHash("x"); m.setFirstName("F"); m.setLastName("L");
        return em.persistAndFlush(m);
    }

    private FeedPost savePost(Long authorId, OffsetDateTime when, boolean softDeleted) {
        FeedPost p = new FeedPost();
        p.setAuthorId(authorId);
        p.setBody("post by " + authorId);
        p.setCreatedAt(when);
        p.setUpdatedAt(when);
        if (softDeleted) p.setDeletedAt(when.plusMinutes(1));
        return em.persistAndFlush(p);
    }

    private void saveComment(Long authorId, Long postId, OffsetDateTime when, boolean softDeleted) {
        FeedPostComment c = new FeedPostComment();
        c.setPostId(postId);
        c.setAuthorId(authorId);
        c.setBody("c");
        c.setCreatedAt(when);
        c.setUpdatedAt(when);
        if (softDeleted) c.setDeletedAt(when.plusMinutes(1));
        em.persistAndFlush(c);
    }

    private void saveShare(Long sharerId, Long postId, OffsetDateTime when) {
        FeedPostShare s = new FeedPostShare();
        s.setPostId(postId);
        s.setSharerId(sharerId);
        s.setCreatedAt(when);
        em.persistAndFlush(s);
    }

    private static Map<Long, Object[]> indexById(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r[0]).longValue(), r -> r));
    }

    private static OffsetDateTime toOdt(Object raw) {
        if (raw instanceof OffsetDateTime odt) return odt;
        if (raw instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        if (raw instanceof java.time.Instant inst) return inst.atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("bad type: " + raw.getClass());
    }
}
