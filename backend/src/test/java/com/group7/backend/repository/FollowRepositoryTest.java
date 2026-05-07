package com.group7.backend.repository;

import com.group7.backend.entity.Follow;
import com.group7.backend.entity.FollowId;
import com.group7.backend.entity.Mentee;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres coverage for the {@link Follow} entity, the
 * {@link FollowRepository} derived queries, the native upsert, and the
 * V21 migration's schema invariants (CASCADE on user delete, CHECK
 * preventing self-follow, PK uniqueness driving idempotency).
 *
 * <p>The cascade test deletes the parent row through
 * {@code menteeRepository.delete(...)} (which handles the JOINED-inheritance
 * dance by issuing {@code DELETE FROM mentees} followed by
 * {@code DELETE FROM users}). The thin {@link Follow} entity carries no
 * {@code @OneToMany} on {@code User}, so the {@code follows} rows are reaped
 * purely by the DB-level {@code ON DELETE CASCADE} that fires on the
 * {@code users} row delete — verified by querying through {@link JdbcTemplate}
 * after {@link jakarta.persistence.EntityManager#clear} drops the L1 cache.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FollowRepositoryTest {

    @Autowired private FollowRepository followRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        followRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── Native upsert ────────────────────────────────────────────────────────

    @Test
    void upsertFollow_returnsOneOnNewInsert() {
        Mentee a = saveMentee("follow_up_a", "A");
        Mentee b = saveMentee("follow_up_b", "B");

        int inserted = followRepository.upsertFollow(a.getId(), b.getId());

        assertThat(inserted).isEqualTo(1);
        assertThat(followRepository.existsById(new FollowId(a.getId(), b.getId()))).isTrue();
    }

    @Test
    void upsertFollow_returnsZeroOnDuplicate_andDoesNotThrow() {
        Mentee a = saveMentee("follow_dup_a", "A");
        Mentee b = saveMentee("follow_dup_b", "B");

        followRepository.upsertFollow(a.getId(), b.getId());
        int second = followRepository.upsertFollow(a.getId(), b.getId());

        assertThat(second).isZero();
        // Still exactly one row — ON CONFLICT DO NOTHING preserved the original.
        assertThat(followRepository.countByIdFolloweeId(b.getId())).isEqualTo(1);
    }

    @Test
    void upsertFollow_setsCreatedAtFromDbDefault() {
        Mentee a = saveMentee("follow_ts_a", "A");
        Mentee b = saveMentee("follow_ts_b", "B");
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(5);

        followRepository.upsertFollow(a.getId(), b.getId());
        // The native query bypasses the persistence context; clear so the
        // subsequent findById sees the actual row, not a cached projection.
        entityManager.clear();
        Follow row = followRepository.findById(new FollowId(a.getId(), b.getId())).orElseThrow();

        assertThat(row.getCreatedAt()).isAfter(before);
    }

    // ── DB-level CHECK rejecting self-follow ─────────────────────────────────

    @Test
    void selfFollow_isRejectedByDbCheckConstraint() {
        Mentee a = saveMentee("follow_self", "A");

        // Defence-in-depth: the service layer rejects self-follow with a
        // SelfFollowException, but if anything ever bypasses that check the
        // DB constraint must catch it. The native query executes synchronously
        // against the DB, so the CHECK violation surfaces during upsertFollow
        // itself — no separate flush needed.
        assertThatThrownBy(() -> followRepository.upsertFollow(a.getId(), a.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("follows_check");
    }

    // ── Derived query methods + sort ─────────────────────────────────────────

    @Test
    void findByIdFolloweeId_paginatesNewestFirstWithStableTiebreaker() {
        Mentee target = saveMentee("follow_target", "T");
        Mentee f1 = saveMentee("follow_f1", "F1");
        Mentee f2 = saveMentee("follow_f2", "F2");
        Mentee f3 = saveMentee("follow_f3", "F3");

        followRepository.upsertFollow(f1.getId(), target.getId());
        followRepository.upsertFollow(f2.getId(), target.getId());
        followRepository.upsertFollow(f3.getId(), target.getId());

        Page<Follow> page = followRepository
                .findByIdFolloweeIdOrderByCreatedAtDescIdFollowerIdDesc(
                        target.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        // Newest insert first (createdAt DESC); same-microsecond inserts fall
        // back to follower_id DESC.
        assertThat(page.getContent().stream().map(f -> f.getId().getFollowerId()))
                .containsExactly(f3.getId(), f2.getId(), f1.getId());
    }

    @Test
    void findByIdFollowerId_paginatesFollowingList() {
        Mentee follower = saveMentee("follow_actor", "Actor");
        Mentee t1 = saveMentee("follow_t1", "T1");
        Mentee t2 = saveMentee("follow_t2", "T2");

        followRepository.upsertFollow(follower.getId(), t1.getId());
        followRepository.upsertFollow(follower.getId(), t2.getId());

        Page<Follow> page = followRepository
                .findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(
                        follower.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().stream().map(f -> f.getId().getFolloweeId()))
                .containsExactly(t2.getId(), t1.getId());
    }

    // ── Counts ───────────────────────────────────────────────────────────────

    @Test
    void countsAreDirectionAware() {
        Mentee a = saveMentee("follow_cnt_a", "A");
        Mentee b = saveMentee("follow_cnt_b", "B");
        Mentee c = saveMentee("follow_cnt_c", "C");

        // a follows b and c; nobody follows a.
        followRepository.upsertFollow(a.getId(), b.getId());
        followRepository.upsertFollow(a.getId(), c.getId());

        assertThat(followRepository.countByIdFollowerId(a.getId())).isEqualTo(2);
        assertThat(followRepository.countByIdFolloweeId(a.getId())).isZero();
        assertThat(followRepository.countByIdFolloweeId(b.getId())).isEqualTo(1);
        assertThat(followRepository.countByIdFolloweeId(c.getId())).isEqualTo(1);
    }

    // ── deleteById is silent on missing ──────────────────────────────────────

    @Test
    void deleteById_isSilentOnMissingEdge() {
        // Spring Data 3.x deleteById no longer throws EmptyResultDataAccessException.
        // Pre-flight check #7 in the plan promises this; this test pins it down.
        Mentee a = saveMentee("follow_del_a", "A");
        Mentee b = saveMentee("follow_del_b", "B");

        followRepository.deleteById(new FollowId(a.getId(), b.getId())); // no-op
        entityManager.flush();

        // Inserting after the no-op delete still works.
        int inserted = followRepository.upsertFollow(a.getId(), b.getId());
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void deleteById_removesExistingEdge() {
        Mentee a = saveMentee("follow_del2_a", "A");
        Mentee b = saveMentee("follow_del2_b", "B");
        followRepository.upsertFollow(a.getId(), b.getId());

        followRepository.deleteById(new FollowId(a.getId(), b.getId()));
        entityManager.flush();

        assertThat(followRepository.existsById(new FollowId(a.getId(), b.getId()))).isFalse();
    }

    // ── DB-level cascade on user delete ──────────────────────────────────────

    @Test
    void cascade_removesIncomingAndOutgoingFollowsWhenUserDeleted() {
        // The mentees table has a NO-ACTION FK to users(id) (JOINED-inheritance
        // child row), so a raw DELETE FROM users would be blocked. JPA's
        // menteeRepository.delete(...) handles the JOINED hierarchy by
        // issuing DELETE FROM mentees + DELETE FROM users in order; the
        // latter fires the FK CASCADE on follows. The thin Follow entity
        // has no @OneToMany on User, so this cascade is purely DB-level —
        // exactly what we want to verify.
        Mentee a = saveMentee("follow_csc_a", "A");
        Mentee b = saveMentee("follow_csc_b", "B");
        Mentee c = saveMentee("follow_csc_c", "C");

        followRepository.upsertFollow(a.getId(), b.getId()); // a → b (outgoing for a)
        followRepository.upsertFollow(c.getId(), a.getId()); // c → a (incoming for a)
        assertThat(followRepository.count()).isEqualTo(2);

        Long aId = a.getId();
        menteeRepository.delete(a);
        // Flush the JPA delete so the DB-level FK CASCADE on follows actually
        // fires before the JdbcTemplate count below; clear so the post-cascade
        // state is read from the DB rather than the L1 cache.
        entityManager.flush();
        entityManager.clear();

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE follower_id = ? OR followee_id = ?",
                Integer.class, aId, aId);
        assertThat(remaining).isZero();
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Mentee saveMentee(String suffix, String firstName) {
        Mentee m = new Mentee();
        m.setFirstName(firstName);
        m.setLastName("L");
        m.setEmail(suffix + "@example.com");
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        return menteeRepository.save(m);
    }
}
