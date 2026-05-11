package com.group7.backend.repository;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.Mentor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-fit guarantee for the viewer-interaction UNION query.
 * Verifies:
 * <ol>
 *   <li>liking a post surfaces that post's author;</li>
 *   <li>commenting on a post surfaces that post's author;</li>
 *   <li>like + comment on the same author dedupes (UNION not UNION ALL);</li>
 *   <li>soft-deleted parent posts hide both the like and the comment;</li>
 *   <li>soft-deleted comments are excluded;</li>
 *   <li>activity outside the {@code since} window is excluded;</li>
 *   <li>activity by a different viewer is excluded.</li>
 * </ol>
 *
 * <p>Uses {@link JdbcTemplate} to seed {@code feed_post_likes} and
 * {@code feed_post_comments} directly so the test can fix
 * {@code created_at} per row — {@code FeedPostLike.createdAt} is mapped
 * {@code insertable=false} via JPA, so the entity path cannot control
 * its timestamp.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ViewerInteractionRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private ViewerInteractionRepository repo;
    @Autowired private TestEntityManager em;
    @Autowired private JdbcTemplate jdbc;

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-05-11T12:00:00Z");
    private static final OffsetDateTime SINCE = NOW.minusDays(90);

    @Test
    void liking_aPost_surfacesItsAuthor() {
        Mentor viewer = saveMentor("v1@x.com");
        Mentor author = saveMentor("a1@x.com");
        FeedPost p = savePost(author.getId(), NOW.minusDays(10), false);

        insertLike(p.getId(), viewer.getId(), NOW.minusDays(2));
        em.flush();

        assertThat(repo.authorsInteractedWith(viewer.getId(), SINCE))
                .containsExactly(author.getId());
    }

    @Test
    void commenting_onAPost_surfacesItsAuthor() {
        Mentor viewer = saveMentor("v2@x.com");
        Mentor author = saveMentor("a2@x.com");
        FeedPost p = savePost(author.getId(), NOW.minusDays(10), false);

        insertComment(p.getId(), viewer.getId(), NOW.minusDays(1), false);
        em.flush();

        assertThat(repo.authorsInteractedWith(viewer.getId(), SINCE))
                .containsExactly(author.getId());
    }

    @Test
    void likePlusComment_onSameAuthor_dedupes() {
        Mentor viewer = saveMentor("v3@x.com");
        Mentor author = saveMentor("a3@x.com");
        FeedPost p1 = savePost(author.getId(), NOW.minusDays(10), false);
        FeedPost p2 = savePost(author.getId(), NOW.minusDays(8), false);

        insertLike(p1.getId(), viewer.getId(), NOW.minusDays(2));
        insertComment(p2.getId(), viewer.getId(), NOW.minusDays(1), false);
        em.flush();

        assertThat(repo.authorsInteractedWith(viewer.getId(), SINCE))
                .containsExactly(author.getId());
    }

    @Test
    void softDeletedParentPost_hidesBothLikeAndComment() {
        Mentor viewer = saveMentor("v4@x.com");
        Mentor author = saveMentor("a4@x.com");
        FeedPost p = savePost(author.getId(), NOW.minusDays(10), true);  // soft-deleted

        insertLike(p.getId(), viewer.getId(), NOW.minusDays(2));
        insertComment(p.getId(), viewer.getId(), NOW.minusDays(1), false);
        em.flush();

        assertThat(repo.authorsInteractedWith(viewer.getId(), SINCE)).isEmpty();
    }

    @Test
    void softDeletedComment_isExcluded_butLiveLikeStillCounts() {
        Mentor viewer = saveMentor("v5@x.com");
        Mentor a = saveMentor("a5@x.com");
        Mentor b = saveMentor("b5@x.com");
        FeedPost pa = savePost(a.getId(), NOW.minusDays(10), false);
        FeedPost pb = savePost(b.getId(), NOW.minusDays(10), false);

        insertLike(pa.getId(),    viewer.getId(), NOW.minusDays(2));
        insertComment(pb.getId(), viewer.getId(), NOW.minusDays(1), true);   // soft-deleted comment
        em.flush();

        assertThat(repo.authorsInteractedWith(viewer.getId(), SINCE))
                .containsExactly(a.getId())
                .doesNotContain(b.getId());
    }

    @Test
    void activityBeforeSinceWindow_isExcluded() {
        Mentor viewer = saveMentor("v6@x.com");
        Mentor author = saveMentor("a6@x.com");
        FeedPost p = savePost(author.getId(), NOW.minusDays(120), false);

        insertLike(p.getId(), viewer.getId(), NOW.minusDays(100));   // outside 90d
        em.flush();

        assertThat(repo.authorsInteractedWith(viewer.getId(), SINCE)).isEmpty();
    }

    @Test
    void activityByAnotherViewer_isExcluded() {
        Mentor viewer = saveMentor("v7@x.com");
        Mentor other  = saveMentor("o7@x.com");
        Mentor author = saveMentor("a7@x.com");
        FeedPost p = savePost(author.getId(), NOW.minusDays(10), false);

        insertLike(p.getId(), other.getId(), NOW.minusDays(2));   // wrong viewer
        em.flush();

        assertThat(repo.authorsInteractedWith(viewer.getId(), SINCE)).isEmpty();
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

    private void insertLike(Long postId, Long userId, OffsetDateTime when) {
        jdbc.update(
                "INSERT INTO feed_post_likes (post_id, user_id, created_at) VALUES (?, ?, ?)",
                postId, userId, Timestamp.from(when.toInstant()));
    }

    private void insertComment(Long postId, Long authorId, OffsetDateTime when, boolean softDeleted) {
        jdbc.update("""
                INSERT INTO feed_post_comments
                    (post_id, author_id, body, created_at, updated_at, deleted_at, version)
                VALUES (?, ?, 'c', ?, ?, ?, 0)
                """,
                postId, authorId,
                Timestamp.from(when.toInstant()),
                Timestamp.from(when.toInstant()),
                softDeleted ? Timestamp.from(when.plusMinutes(1).toInstant()) : null);
    }
}
