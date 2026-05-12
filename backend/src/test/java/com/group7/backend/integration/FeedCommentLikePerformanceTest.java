package com.group7.backend.integration;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostComment;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.FeedPostCommentRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.FeedInteractionService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query-budget guard for the comment-listing path with batched comment-like
 * state (#483). Asserts that {@code FeedInteractionService.listComments}
 * issues a small, page-size-independent number of JPQL queries — the
 * structural N+1 invariant most likely to silently regress when a future
 * refactor moves per-comment loads inside the page mapping loop.
 *
 * <p>Mirrors {@code SearchPerformanceTest}'s pattern: opt into Hibernate's
 * {@code generate_statistics} via {@code @SpringBootTest(properties=...)}
 * (the default test profile leaves it off to keep the bulk suite cheap),
 * inject {@link EntityManagerFactory}, unwrap to {@link SessionFactory},
 * and snapshot {@link Statistics#getQueryExecutionCount()} before/after
 * the call.
 *
 * <p>{@code Statistics.getQueryExecutionCount()} counts JPQL/HQL/native
 * query executions but excludes L1-cache hits and collection fetches.
 * The native {@code INSERT … ON CONFLICT} upsert from the toggle path
 * is also counted; we do not toggle inside this test, only list, so the
 * counted shape is purely the read path.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class FeedCommentLikePerformanceTest {

    /** Page size for the listComments call. The query budget is independent of this. */
    private static final int PAGE_SIZE = 20;

    /**
     * Documented expected JPQL queries on a populated 20-comment page:
     * <ol>
     *   <li>1 × parent post visibility check ({@code findByIdAndDeletedAtIsNull})</li>
     *   <li>1 × paged comment fetch ({@code findByPostIdOrderByCreatedAtAscIdAsc})</li>
     *   <li>1 × paged comment count (Spring Data's auto-count for the Page)</li>
     *   <li>1 × author-name batch fetch ({@code userRepository.findAllById})</li>
     *   <li>1 × batch comment-like count ({@code countByCommentIdIn})</li>
     *   <li>1 × batch viewer-liked ids ({@code findLikedCommentIdsForViewer})</li>
     * </ol>
     * That's six JPQL queries. The bound is 7 to give a one-query buffer
     * for any incidental fetch (mentor/mentee subtype lookup) and to keep
     * the test from going flaky on minor refactors that stay structurally
     * O(1) per interaction type.
     */
    private static final int QUERY_BUDGET = 7;

    @Autowired private FeedInteractionService interactionService;
    @Autowired private FeedPostRepository feedPostRepository;
    @Autowired private FeedPostCommentRepository commentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM feed_post_comment_likes");
        jdbcTemplate.update("DELETE FROM feed_post_comments");
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        userRepository.deleteAll();
    }

    @Test
    void listComments_isBoundedByConstantQueryCount_regardlessOfCommentCount() {
        // Seed an author + a post + PAGE_SIZE comments. We don't seed any
        // comment-like rows because the batch repos still issue their query
        // even on an empty result set — that's what we're budgeting for.
        Long authorId = createMentor("perf_author@test.com");
        long postId = createPost(authorId);
        for (int i = 0; i < PAGE_SIZE; i++) {
            createComment(postId, authorId, "comment " + i);
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        Page<?> page = interactionService.listComments(
                postId, authorId, PageRequest.of(0, PAGE_SIZE));

        long queries = stats.getQueryExecutionCount();
        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        assertThat(queries)
                .as("listComments must batch comment-like state — exceeding the budget "
                        + "indicates per-comment N+1 SQL re-introduced")
                .isLessThanOrEqualTo(QUERY_BUDGET);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Long createMentor(String email) {
        Mentor m = new Mentor();
        m.setEmail(email);
        m.setPasswordHash(new BCryptPasswordEncoder().encode("Password1"));
        m.setFirstName("Perf");
        m.setLastName("Author");
        m.setIsEmailVerified(true);
        return userRepository.save(m).getId();
    }

    private long createPost(Long authorId) {
        FeedPost post = new FeedPost(authorId, "perf body");
        OffsetDateTime now = OffsetDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        return feedPostRepository.save(post).getId();
    }

    private long createComment(long postId, Long authorId, String body) {
        FeedPostComment c = new FeedPostComment(postId, authorId, body);
        OffsetDateTime now = OffsetDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return commentRepository.save(c).getId();
    }
}
