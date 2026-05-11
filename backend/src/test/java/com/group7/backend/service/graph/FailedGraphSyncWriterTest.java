package com.group7.backend.service.graph;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.FailedGraphSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for {@link FailedGraphSyncWriter}. Covers the
 * reason-truncation cap, the null-failure factory path, and the stamp
 * path that the {@code @TransactionalEventListener(AFTER_COMMIT)} retry
 * loop depends on.
 *
 * <p>Schema-level invariants (the {@code resynced_at IS NULL} unsync
 * predicate, FK shape) live in {@code FailedGraphSyncSchemaTest}.
 */
@ExtendWith(MockitoExtension.class)
class FailedGraphSyncWriterTest {

    @Mock private FailedGraphSyncRepository failedLog;
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-11T12:00:00Z"), ZoneOffset.UTC);
    private FailedGraphSyncWriter writer;

    @BeforeEach
    void setUp() {
        writer = new FailedGraphSyncWriter(failedLog, FIXED_CLOCK);
    }

    @Test
    void enqueue_withFailure_setsToStringAsReason() {
        RuntimeException failure = new RuntimeException("neo4j unreachable");
        FollowChangedEvent event = FollowChangedEvent.followed(7L, 8L);

        writer.enqueue(event, failure);

        FailedGraphSync saved = capture();
        assertThat(saved.getFollowerId()).isEqualTo(7L);
        assertThat(saved.getFolloweeId()).isEqualTo(8L);
        assertThat(saved.getChangeType()).isEqualTo(FollowChangedEvent.ChangeType.FOLLOWED);
        assertThat(saved.getFailureReason()).isEqualTo(failure.toString());
        assertThat(saved.getFailedAt())
                .isEqualTo(OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC));
    }

    @Test
    void enqueue_withNullFailure_setsUnknownReason() {
        FollowChangedEvent event = FollowChangedEvent.unfollowed(7L, 8L);

        writer.enqueue(event, null);

        FailedGraphSync saved = capture();
        assertThat(saved.getFailureReason()).isEqualTo("unknown");
        assertThat(saved.getChangeType()).isEqualTo(FollowChangedEvent.ChangeType.UNFOLLOWED);
    }

    @Test
    void enqueue_truncatesLongReason_atMaxReasonLen() {
        // 10000-char message — must be truncated to MAX_REASON_LEN=4000
        String massive = "x".repeat(10_000);
        RuntimeException failure = new RuntimeException(massive);

        writer.enqueue(FollowChangedEvent.followed(1L, 2L), failure);

        FailedGraphSync saved = capture();
        assertThat(saved.getFailureReason()).hasSize(FailedGraphSyncWriter.MAX_REASON_LEN);
    }

    @Test
    void enqueue_userDeletedEvent_propagatesNullFollowee() {
        FollowChangedEvent event = FollowChangedEvent.userDeleted(99L);

        writer.enqueue(event, new RuntimeException("x"));

        FailedGraphSync saved = capture();
        assertThat(saved.getFollowerId()).isEqualTo(99L);
        assertThat(saved.getFolloweeId()).isNull();
        assertThat(saved.getChangeType())
                .isEqualTo(FollowChangedEvent.ChangeType.USER_DELETED);
    }

    @Test
    void markResynced_setsResyncedAtFromClock_andSaves() {
        FailedGraphSync row = new FailedGraphSync();

        writer.markResynced(row);

        assertThat(row.getResyncedAt())
                .isEqualTo(OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC));
        verify(failedLog).save(row);
    }

    private FailedGraphSync capture() {
        ArgumentCaptor<FailedGraphSync> cap = ArgumentCaptor.forClass(FailedGraphSync.class);
        verify(failedLog).save(cap.capture());
        return cap.getValue();
    }
}
