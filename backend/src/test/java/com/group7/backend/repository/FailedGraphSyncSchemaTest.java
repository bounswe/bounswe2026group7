package com.group7.backend.repository;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.event.FollowChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema-fit guarantee for {@link FailedGraphSync} against real Postgres.
 * Existed because the original migration shipped with two contracts that the
 * USER_DELETED event class violated: {@code followee_id NOT NULL} and a CHECK
 * constraint that excluded {@code 'USER_DELETED'}. The unit tests above
 * mocked the repository and never saw the failure; this test would have
 * caught the bug at compile-and-run time.
 *
 * <p>Uses Testcontainers Postgres (not H2) because the migration relies on
 * {@code FILTER (WHERE …)} partial indexes and CHECK constraints that H2's
 * Postgres-compat mode silently mis-renders.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class FailedGraphSyncSchemaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private FailedGraphSyncRepository repo;

    @Test
    void followedRow_persistsWithBothEndpoints() {
        FailedGraphSync row = FailedGraphSync.from(
                FollowChangedEvent.followed(1L, 2L), "transient");
        row.setFailedAt(OffsetDateTime.now(ZoneOffset.UTC));

        FailedGraphSync saved = repo.save(row);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFollowerId()).isEqualTo(1L);
        assertThat(saved.getFolloweeId()).isEqualTo(2L);
        assertThat(saved.getChangeType()).isEqualTo(FollowChangedEvent.ChangeType.FOLLOWED);
    }

    @Test
    void unfollowedRow_persistsWithBothEndpoints() {
        FailedGraphSync row = FailedGraphSync.from(
                FollowChangedEvent.unfollowed(1L, 2L), "transient");
        row.setFailedAt(OffsetDateTime.now(ZoneOffset.UTC));

        FailedGraphSync saved = repo.save(row);

        assertThat(saved.getFolloweeId()).isEqualTo(2L);
        assertThat(saved.getChangeType()).isEqualTo(FollowChangedEvent.ChangeType.UNFOLLOWED);
    }

    @Test
    void userDeletedRow_persistsWithNullFollowee() {
        // The bug this regression closes: followee_id must be nullable AND
        // the change_type CHECK must permit 'USER_DELETED' for this insert to
        // succeed. Prior to V38 it would have thrown DataIntegrityViolationException.
        FailedGraphSync row = FailedGraphSync.from(
                FollowChangedEvent.userDeleted(99L), "transient");
        row.setFailedAt(OffsetDateTime.now(ZoneOffset.UTC));

        FailedGraphSync saved = repo.save(row);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFollowerId()).isEqualTo(99L);
        assertThat(saved.getFolloweeId()).isNull();
        assertThat(saved.getChangeType()).isEqualTo(FollowChangedEvent.ChangeType.USER_DELETED);
    }

    @Test
    void followedRow_rejectedWhenFolloweeIsNull() {
        // The pair-shape CHECK enforces that FOLLOWED rows MUST carry a
        // followee — a malformed row gets rejected at the DB, not silently
        // half-written.
        FailedGraphSync row = new FailedGraphSync();
        row.setFollowerId(1L);
        row.setFolloweeId(null);
        row.setChangeType(FollowChangedEvent.ChangeType.FOLLOWED);
        row.setFailedAt(OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> repo.saveAndFlush(row))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userDeletedRow_rejectedWhenFolloweeIsPresent() {
        // Symmetric: USER_DELETED rows MUST have null followee. Defence
        // against caller errors where event is constructed wrong.
        FailedGraphSync row = new FailedGraphSync();
        row.setFollowerId(99L);
        row.setFolloweeId(2L);
        row.setChangeType(FollowChangedEvent.ChangeType.USER_DELETED);
        row.setFailedAt(OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> repo.saveAndFlush(row))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAllUnsynced_returnsOnlyRowsWithNullResyncedAt() {
        FailedGraphSync pending = FailedGraphSync.from(
                FollowChangedEvent.followed(1L, 2L), "");
        pending.setFailedAt(OffsetDateTime.now(ZoneOffset.UTC));
        FailedGraphSync done = FailedGraphSync.from(
                FollowChangedEvent.followed(3L, 4L), "");
        done.setFailedAt(OffsetDateTime.now(ZoneOffset.UTC));
        done.setResyncedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repo.saveAll(java.util.List.of(pending, done));

        assertThat(repo.findAllUnsynced())
                .extracting(FailedGraphSync::getFollowerId)
                .containsExactly(1L);
    }

    @Test
    void deleteResyncedOlderThan_deletesOnlyAgedResynced_keepsPending() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // pending row, ancient — must stay (resync hasn't happened)
        FailedGraphSync pendingOld = FailedGraphSync.from(
                FollowChangedEvent.followed(1L, 2L), "");
        pendingOld.setFailedAt(now.minusDays(60));

        // resynced 5 days ago — within retention, must stay
        FailedGraphSync recentlyResynced = FailedGraphSync.from(
                FollowChangedEvent.followed(3L, 4L), "");
        recentlyResynced.setFailedAt(now.minusDays(7));
        recentlyResynced.setResyncedAt(now.minusDays(5));

        // resynced 45 days ago — past 30-day threshold, must be deleted
        FailedGraphSync agedResynced = FailedGraphSync.from(
                FollowChangedEvent.followed(5L, 6L), "");
        agedResynced.setFailedAt(now.minusDays(50));
        agedResynced.setResyncedAt(now.minusDays(45));

        repo.saveAll(java.util.List.of(pendingOld, recentlyResynced, agedResynced));

        int deleted = repo.deleteResyncedOlderThan(now.minusDays(30));

        assertThat(deleted).isEqualTo(1);
        assertThat(repo.findAll())
                .extracting(FailedGraphSync::getFollowerId)
                .containsExactlyInAnyOrder(1L, 3L)
                .doesNotContain(5L);
    }
}
