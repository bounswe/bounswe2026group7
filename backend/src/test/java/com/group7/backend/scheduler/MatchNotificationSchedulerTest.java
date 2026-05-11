package com.group7.backend.scheduler;

import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.scheduler.MatchNotificationScheduler.BatchResult;
import com.group7.backend.service.MatchNotificationProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link MatchNotificationScheduler}'s loop semantics.
 * Asserts: (a) every eligible mentee id from the repo reaches the processor;
 * (b) per-user exceptions are caught + logged + the loop continues;
 * (c) an eligibility-query failure is logged once and doesn't propagate
 * out of the @Scheduled method; (d) the {@link BatchResult} surfaced for
 * the run-summary log carries the eligibility-list size alongside the
 * publish count.
 *
 * <p>We don't try to assert that the cron fires on time — that's Spring's
 * responsibility. Tests invoke the package-private {@code notifyMentees()}
 * directly, matching the
 * {@link com.group7.backend.scheduler.AttachmentOrphanCleanupScheduler}
 * test idiom.
 */
@ExtendWith(MockitoExtension.class)
class MatchNotificationSchedulerTest {

    @Mock private MatchNotificationProcessor processor;
    @Mock private MenteeRepository menteeRepository;

    private MatchNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MatchNotificationScheduler(
                processor, menteeRepository,
                "0 0 9 * * *", "UTC");
    }

    @Test
    void notifyMentees_iteratesAllEligibleIds() {
        when(menteeRepository.findUnattachedIds()).thenReturn(List.of(1L, 2L, 3L));
        when(processor.processMentee(1L)).thenReturn(true);
        when(processor.processMentee(2L)).thenReturn(false);
        when(processor.processMentee(3L)).thenReturn(true);

        BatchResult result = scheduler.notifyMentees();

        assertThat(result.eligible()).isEqualTo(3);
        assertThat(result.published()).isEqualTo(2);
        verify(processor).processMentee(1L);
        verify(processor).processMentee(2L);
        verify(processor).processMentee(3L);
    }

    @Test
    void notifyMentees_swallowsPerUserExceptionsAndContinues() {
        when(menteeRepository.findUnattachedIds()).thenReturn(List.of(1L, 2L, 3L));
        when(processor.processMentee(1L)).thenReturn(true);
        when(processor.processMentee(2L)).thenThrow(new RuntimeException("transient DB error"));
        when(processor.processMentee(3L)).thenReturn(true);

        BatchResult result = scheduler.notifyMentees();

        // Eligible count is still the full list — failures don't subtract.
        // Published count reflects only the successful publishes.
        assertThat(result.eligible()).isEqualTo(3);
        assertThat(result.published()).isEqualTo(2);
        verify(processor).processMentee(1L);
        verify(processor).processMentee(2L);
        verify(processor).processMentee(3L);
    }

    @Test
    void notifyMentees_logsIllegalStateAtErrorButContinues() {
        when(menteeRepository.findUnattachedIds()).thenReturn(List.of(1L, 2L));
        // IllegalStateException = precondition violation (programmer bug).
        // Different log path from a transient exception, but both are caught
        // and the loop proceeds.
        when(processor.processMentee(1L)).thenThrow(new IllegalStateException("precondition broken"));
        when(processor.processMentee(2L)).thenReturn(true);

        BatchResult result = scheduler.notifyMentees();

        assertThat(result.eligible()).isEqualTo(2);
        assertThat(result.published()).isEqualTo(1);
        verify(processor).processMentee(2L);
    }

    @Test
    void notifyMentees_emptyEligibilityListIsNoOp() {
        when(menteeRepository.findUnattachedIds()).thenReturn(List.of());

        BatchResult result = scheduler.notifyMentees();

        assertThat(result.eligible()).isZero();
        assertThat(result.published()).isZero();
        verify(processor, never()).processMentee(anyLong());
    }

    // ── Run-summary semantics ────────────────────────────────────────────────

    @Test
    void notifyChangedMatches_runsMenteeBatchAndCompletesEvenWithZeroPublishes() {
        when(menteeRepository.findUnattachedIds()).thenReturn(List.of());

        scheduler.notifyChangedMatches();

        // Eligibility query ran exactly once; processor never invoked.
        verify(menteeRepository).findUnattachedIds();
        verify(processor, never()).processMentee(anyLong());
    }

    @Test
    void notifyChangedMatches_swallowsEligibilityQueryFailure() {
        // runSafely wraps the batch so a DB outage on findUnattachedIds
        // is logged once and the @Scheduled method returns cleanly.
        when(menteeRepository.findUnattachedIds()).thenThrow(new RuntimeException("DB outage"));

        scheduler.notifyChangedMatches();   // must not throw

        verify(processor, never()).processMentee(anyLong());
    }

    // ── Startup config log ───────────────────────────────────────────────────

    @Test
    void logStartupConfig_doesNotThrowWithProvidedConfig() {
        // Constructor-injected cron + zone — proves the @PostConstruct line
        // doesn't NPE when invoked outside Spring (it would if cron/zone
        // were field-injected and tests forgot to set them).
        scheduler.logStartupConfig();
    }
}
