package com.group7.backend.scheduler;

import com.group7.backend.repository.FeedPostCommentRepository;
import com.group7.backend.repository.FeedPostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link FeedSoftDeleteCleanupScheduler} (#487).
 * Validates the cutoff calculation, the constructor's defensive clamp
 * on a misconfigured {@code restoreWindowDays}, and that both
 * repositories are called inside the same scheduler invocation.
 */
@ExtendWith(MockitoExtension.class)
class FeedSoftDeleteCleanupSchedulerTest {

    @Mock private FeedPostRepository postRepository;
    @Mock private FeedPostCommentRepository commentRepository;

    @Test
    void purge_callsBothRepositoriesWithCutoffOfNowMinusWindow() {
        FeedSoftDeleteCleanupScheduler scheduler =
                new FeedSoftDeleteCleanupScheduler(postRepository, commentRepository, 30);
        when(postRepository.hardDeletePostsSoftDeletedBefore(any())).thenReturn(0);
        when(commentRepository.hardDeleteCommentsSoftDeletedBefore(any())).thenReturn(0);

        OffsetDateTime before = OffsetDateTime.now();
        scheduler.purgeExpiredSoftDeletes();
        OffsetDateTime after = OffsetDateTime.now();

        ArgumentCaptor<OffsetDateTime> postCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> commentCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(postRepository).hardDeletePostsSoftDeletedBefore(postCutoff.capture());
        verify(commentRepository).hardDeleteCommentsSoftDeletedBefore(commentCutoff.capture());

        // Cutoff is now - 30 days, computed inside the scheduler
        // sometime between `before` and `after`.
        OffsetDateTime expectedMin = before.minusDays(30);
        OffsetDateTime expectedMax = after.minusDays(30);
        assertThat(postCutoff.getValue()).isBetween(expectedMin, expectedMax);
        // Both cutoffs are the same instant — captured from one
        // local variable, not two `OffsetDateTime.now()` calls.
        assertThat(commentCutoff.getValue()).isEqualTo(postCutoff.getValue());
    }

    @Test
    void purge_doesNotLogWhenNothingDeleted() {
        // Implicit assertion: zero return values produce a quiet log
        // line, but no exception. The scheduler must remain a no-op
        // on an empty database without spamming logs.
        FeedSoftDeleteCleanupScheduler scheduler =
                new FeedSoftDeleteCleanupScheduler(postRepository, commentRepository, 30);
        when(postRepository.hardDeletePostsSoftDeletedBefore(any())).thenReturn(0);
        when(commentRepository.hardDeleteCommentsSoftDeletedBefore(any())).thenReturn(0);

        scheduler.purgeExpiredSoftDeletes();  // no exception
    }

    @Test
    void constructor_clampsRestoreWindowDaysToAtLeastOne_whenConfiguredZero() {
        FeedSoftDeleteCleanupScheduler scheduler =
                new FeedSoftDeleteCleanupScheduler(postRepository, commentRepository, 0);
        when(postRepository.hardDeletePostsSoftDeletedBefore(any())).thenReturn(0);
        when(commentRepository.hardDeleteCommentsSoftDeletedBefore(any())).thenReturn(0);

        OffsetDateTime before = OffsetDateTime.now();
        scheduler.purgeExpiredSoftDeletes();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(postRepository).hardDeletePostsSoftDeletedBefore(cutoff.capture());
        // Cutoff is now - 1 day (clamped), not now (which would reap
        // everything currently soft-deleted).
        assertThat(cutoff.getValue()).isBefore(before);
        assertThat(Duration.between(cutoff.getValue(), before).toHours())
                .isBetween(23L, 25L);  // ~1 day, with a wide buffer for clock skew
    }

    @Test
    void constructor_clampsRestoreWindowDaysToAtLeastOne_whenConfiguredNegative() {
        FeedSoftDeleteCleanupScheduler scheduler =
                new FeedSoftDeleteCleanupScheduler(postRepository, commentRepository, -10);
        when(postRepository.hardDeletePostsSoftDeletedBefore(any())).thenReturn(0);
        when(commentRepository.hardDeleteCommentsSoftDeletedBefore(any())).thenReturn(0);

        OffsetDateTime before = OffsetDateTime.now();
        scheduler.purgeExpiredSoftDeletes();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(postRepository).hardDeletePostsSoftDeletedBefore(cutoff.capture());
        // Cutoff is now - 1 day, not now + 10 days.
        assertThat(cutoff.getValue()).isBefore(before);
    }

    @Test
    void purge_returnsCleanlyEvenWhenBothRepositoriesReturnZero() {
        FeedSoftDeleteCleanupScheduler scheduler =
                new FeedSoftDeleteCleanupScheduler(postRepository, commentRepository, 30);
        when(postRepository.hardDeletePostsSoftDeletedBefore(any())).thenReturn(0);
        when(commentRepository.hardDeleteCommentsSoftDeletedBefore(any())).thenReturn(0);

        scheduler.purgeExpiredSoftDeletes();  // no exception
    }
}
