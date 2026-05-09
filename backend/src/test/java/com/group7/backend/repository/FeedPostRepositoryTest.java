package com.group7.backend.repository;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.entity.Mentee;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres coverage for {@link FeedPost} + {@link FeedPostHashtag},
 * the {@link FeedPostRepository} finders, and the V24 migration's schema
 * invariants:
 * <ul>
 *   <li>composite PK on {@code (post_id, tag)} dedupes hashtags within a post;</li>
 *   <li>body length CHECK rejects oversize bodies;</li>
 *   <li>body non-blank CHECK rejects whitespace-only bodies;</li>
 *   <li>hashtag length CHECK rejects too-long tags;</li>
 *   <li>{@code findByIdAndDeletedAtIsNull} hides soft-deleted posts;</li>
 *   <li>raw {@code findById} still surfaces soft-deleted posts (for the
 *       PATCH/DELETE author-load path);</li>
 *   <li>{@code @Version} optimistic-locking surfaces conflict on concurrent updates;</li>
 *   <li>two-level cascade: deleting a user reaps their posts, which reap their hashtags.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedPostRepositoryTest {

    @Autowired private FeedPostRepository feedPostRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        // Children first by FK, then parents. Cascade would handle this,
        // but explicit ordering is more debuggable when something goes wrong.
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        userRepository.deleteAll();
    }

    // ── Basic persist / retrieve ────────────────────────────────────────────

    @Test
    void persistAndRetrieve_postWithHashtags_roundtrips() {
        Mentee author = saveMentee("post_basic@test.com");

        FeedPost saved = saveFreshPost(author.getId(), "Hello world", List.of("data", "ai"));

        entityManager.flush();
        entityManager.clear();

        FeedPost loaded = feedPostRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getAuthorId()).isEqualTo(author.getId());
        assertThat(loaded.getBody()).isEqualTo("Hello world");
        // @OrderBy("id.tag ASC") gives alphabetical order on read.
        assertThat(loaded.getHashtags()).extracting(h -> h.getId().getTag())
                .containsExactly("ai", "data");
        assertThat(loaded.getDeletedAt()).isNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void hashtagOrder_isAlphabeticalOnRead() {
        Mentee author = saveMentee("post_order@test.com");

        FeedPost saved = saveFreshPost(author.getId(), "Order test",
                List.of("z_first", "a_second", "m_third"));

        entityManager.flush();
        entityManager.clear();

        FeedPost loaded = feedPostRepository.findById(saved.getId()).orElseThrow();
        // @OrderBy("id.tag ASC") gives a deterministic alphabetical order
        // on read, regardless of insertion order. Insertion order via
        // LinkedHashSet doesn't survive the JPA round-trip without an
        // explicit ORDER BY.
        assertThat(loaded.getHashtags()).extracting(h -> h.getId().getTag())
                .containsExactly("a_second", "m_third", "z_first");
    }

    // ── Soft-delete: visibility split between the two finders ───────────────

    @Test
    void findByIdAndDeletedAtIsNull_hidesSoftDeletedPost() {
        Mentee author = saveMentee("post_soft@test.com");
        FeedPost post = saveFreshPost(author.getId(), "About to delete", List.of("temp"));

        post.setDeletedAt(OffsetDateTime.now());
        feedPostRepository.save(post);
        entityManager.flush();
        entityManager.clear();

        // Public-visibility finder treats deleted as gone for everyone.
        assertThat(feedPostRepository.findByIdAndDeletedAtIsNull(post.getId())).isEmpty();
        // Raw finder still surfaces the row for the PATCH/DELETE author-load path.
        assertThat(feedPostRepository.findById(post.getId())).isPresent();
    }

    // ── DB-level CHECK constraints (defence-in-depth backstops) ─────────────

    @Test
    void bodyLengthCheck_rejectsBodyOver2000Chars() {
        Mentee author = saveMentee("post_len@test.com");
        FeedPost post = new FeedPost(author.getId(), "x".repeat(2001));
        OffsetDateTime now = OffsetDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        assertThatThrownBy(() -> {
            feedPostRepository.save(post);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("feed_posts_body_length");
    }

    @Test
    void bodyNonBlankCheck_rejectsWhitespaceOnlyBody() {
        Mentee author = saveMentee("post_blank@test.com");
        FeedPost post = new FeedPost(author.getId(), "   \n  \t  ");
        OffsetDateTime now = OffsetDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        assertThatThrownBy(() -> {
            feedPostRepository.save(post);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("feed_posts_body_nonblank");
    }

    @Test
    void hashtagLengthCheck_rejectsTagOver50Chars() {
        Mentee author = saveMentee("post_taglen@test.com");
        FeedPost post = saveFreshPost(author.getId(), "Tag length test", List.of());
        // Build a 51-char tag and try to attach it directly via JdbcTemplate
        // to bypass any service-side cap and isolate the DB-level guard.
        // Postgres VARCHAR(50) actually rejects with "value too long for
        // type" before the explicit CHECK fires; the CHECK is kept as
        // defence-in-depth in case the column ever migrates to TEXT.
        // The assertion is therefore on the exception type only, not the
        // exact message — both VARCHAR overflow and CHECK violation
        // surface DataIntegrityViolationException.
        String tooLong = "a".repeat(51);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO feed_post_hashtags (post_id, tag) VALUES (?, ?)",
                post.getId(), tooLong))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void compositePk_rejectsDuplicateTagOnSamePost() {
        Mentee author = saveMentee("post_dup@test.com");
        FeedPost post = saveFreshPost(author.getId(), "Dup test", List.of("only"));
        entityManager.flush();

        // Direct JDBC insert simulating a service that didn't dedupe.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO feed_post_hashtags (post_id, tag) VALUES (?, ?)",
                post.getId(), "only"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── @Version optimistic locking ─────────────────────────────────────────

    @Test
    void version_bumpsOnSave() {
        Mentee author = saveMentee("post_ver@test.com");
        FeedPost post = saveFreshPost(author.getId(), "v0", List.of());
        assertThat(post.getVersion()).isZero();

        post.setBody("v1");
        post.setUpdatedAt(OffsetDateTime.now());
        FeedPost saved = feedPostRepository.save(post);
        entityManager.flush();

        assertThat(saved.getVersion()).isEqualTo(1L);
    }

    @Test
    void version_concurrentUpdate_raisesOptimisticLockingFailureException() {
        Mentee author = saveMentee("post_conflict@test.com");
        FeedPost post = saveFreshPost(author.getId(), "Original body", List.of());
        entityManager.flush();
        entityManager.clear();

        // Simulate a stale-version update: hand-craft a FeedPost reference
        // pinned to version 0, while the DB row has been bumped to v1 by a
        // direct UPDATE (the racing call's effect).
        jdbcTemplate.update("UPDATE feed_posts SET body = ?, updated_at = NOW(), version = version + 1 WHERE id = ?",
                "Concurrent edit", post.getId());

        FeedPost stale = new FeedPost();
        stale.setId(post.getId());
        stale.setAuthorId(author.getId());
        stale.setBody("My edit");
        stale.setCreatedAt(post.getCreatedAt());
        stale.setUpdatedAt(OffsetDateTime.now());
        stale.setVersion(0L);  // stale!

        // Hibernate sees stale.version=0 but the DB row is at v1; the
        // version-aware UPDATE updates 0 rows → optimistic-lock failure.
        assertThatThrownBy(() -> {
            feedPostRepository.save(stale);
            entityManager.flush();
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── Two-level cascade on user delete ────────────────────────────────────

    @Test
    void cascade_deletingUser_reapsPostsAndHashtags() {
        Mentee author = saveMentee("post_csc@test.com");
        FeedPost p1 = saveFreshPost(author.getId(), "First", List.of("a", "b"));
        FeedPost p2 = saveFreshPost(author.getId(), "Second", List.of("c"));
        entityManager.flush();

        Long authorId = author.getId();
        Integer postsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_posts WHERE author_id = ?", Integer.class, authorId);
        Integer tagsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_hashtags WHERE post_id IN (?, ?)",
                Integer.class, p1.getId(), p2.getId());
        assertThat(postsBefore).isEqualTo(2);
        assertThat(tagsBefore).isEqualTo(3);

        // Mentee uses JOINED inheritance — delete the subtype row first
        // so the FK from mentees(id) → users(id) doesn't block the
        // parent row, then delete the user row to fire the cascade.
        menteeRepository.delete(author);
        entityManager.flush();
        entityManager.clear();

        Integer postsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_posts WHERE author_id = ?", Integer.class, authorId);
        Integer tagsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_hashtags WHERE post_id IN (?, ?)",
                Integer.class, p1.getId(), p2.getId());
        assertThat(postsAfter).isZero();   // FK cascade on users → feed_posts
        assertThat(tagsAfter).isZero();    // cascade-of-cascade on feed_posts → feed_post_hashtags
    }

    // ── orphan-removal on hashtag collection mutation ───────────────────────

    @Test
    void orphanRemoval_removesTagFromCollection_deletesRow() {
        Mentee author = saveMentee("post_orphan@test.com");
        FeedPost post = saveFreshPost(author.getId(), "Orphan test", List.of("keep", "drop"));
        entityManager.flush();

        // Remove one tag from the collection and save.
        post.getHashtags().removeIf(h -> "drop".equals(h.getId().getTag()));
        feedPostRepository.save(post);
        entityManager.flush();
        entityManager.clear();

        FeedPost loaded = feedPostRepository.findById(post.getId()).orElseThrow();
        assertThat(loaded.getHashtags()).extracting(h -> h.getId().getTag())
                .containsExactly("keep");
    }

    // ── findByIdAndDeletedAtIsNull empty result on missing id ──────────────

    @Test
    void findByIdAndDeletedAtIsNull_returnsEmpty_forNonexistentId() {
        Optional<FeedPost> result = feedPostRepository.findByIdAndDeletedAtIsNull(9_999_999L);
        assertThat(result).isEmpty();
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private Mentee saveMentee(String email) {
        Mentee m = new Mentee();
        m.setFirstName("Test");
        m.setLastName("User");
        m.setEmail(email);
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        return menteeRepository.save(m);
    }

    /**
     * Builds and saves a fresh post, then attaches each tag string as a
     * {@link FeedPostHashtag} (using the two-phase pattern: parent first,
     * then children with the now-known parent id). Returns the parent
     * after the children have been added; cascade flushes them on
     * subsequent {@code entityManager.flush()}.
     */
    private FeedPost saveFreshPost(Long authorId, String body, List<String> tags) {
        FeedPost post = new FeedPost(authorId, body);
        OffsetDateTime now = OffsetDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        FeedPost saved = feedPostRepository.save(post);
        // Two-phase: parent saved (id generated), now children with that id.
        for (String tag : tags) {
            saved.getHashtags().add(new FeedPostHashtag(saved, tag));
        }
        // Stash the LinkedHashSet so order survives.
        saved.setHashtags(new LinkedHashSet<>(saved.getHashtags()));
        return feedPostRepository.save(saved);
    }
}
