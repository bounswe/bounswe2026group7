package com.group7.backend.service.ranking;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for the aggregation contract — signal interaction tests live
 * in each per-signal test class. Specifically asserts:
 *
 * <ul>
 *   <li>disabled signals are skipped (no compute call)</li>
 *   <li>zero-weight signals are skipped</li>
 *   <li>scores are clamped to [0,1] before scaling to [0,100]</li>
 *   <li>factor lists are concatenated in signal-registration order</li>
 *   <li>empty signal list → score 0, empty factors (no NaN, no throw)</li>
 *   <li>negative normalizedScore from a misbehaving signal is clamped to 0</li>
 *   <li>over-1.0 normalizedScore is clamped to 1.0</li>
 *   <li>over-1.0 weighted sum (signals sum to >1) is clamped at top</li>
 * </ul>
 */
class AdvancedMentorRankerTest {

    private final Mentor mentor = new Mentor();
    private final Mentee mentee = new Mentee();

    @Test
    void noSignals_returnsZeroScoreAndEmptyFactors() {
        var ranker = new AdvancedMentorRanker(List.of());
        ScoreResult result = ranker.score(mentor, mentee, List.of(), List.of());
        assertThat(result.score()).isZero();
        assertThat(result.factors()).isEmpty();
    }

    @Test
    void disabledSignal_isSkipped() {
        var signal = mock(MentorScoringSignal.class);
        when(signal.isEnabled()).thenReturn(false);
        when(signal.getWeight()).thenReturn(0.5);

        var ranker = new AdvancedMentorRanker(List.of(signal));
        ScoreResult result = ranker.score(mentor, mentee, List.of(), List.of());

        assertThat(result.score()).isZero();
        verify(signal, never()).compute(any(), any(), any());
    }

    @Test
    void zeroWeightSignal_runsComputeAndPreservesInformationalFactors() {
        // weight=0 is the "score contribution is zero" path. compute() still
        // runs so the signal can emit informational factors (e.g.
        // `location-unset`, `semantic-unavailable`) that the UI surfaces. The
        // weighted score contribution drops to zero, but the user still sees
        // *why* the contribution was zero.
        var signal = mock(MentorScoringSignal.class);
        when(signal.isEnabled()).thenReturn(true);
        when(signal.getWeight()).thenReturn(0.0);
        when(signal.compute(any(), any(), any()))
                .thenReturn(new SignalContribution(1.0, List.of("location-unset")));

        var ranker = new AdvancedMentorRanker(List.of(signal));
        ScoreResult result = ranker.score(mentor, mentee, List.of(), List.of());

        assertThat(result.score()).isZero();                       // no score contribution
        assertThat(result.factors()).containsExactly("location-unset"); // informational factor preserved
        verify(signal).compute(any(), any(), any());
    }

    @Test
    void negativeWeightSignal_runsComputeButContributesZero() {
        // Same contract as weight=0: factors flow through, score doesn't.
        var signal = mock(MentorScoringSignal.class);
        when(signal.isEnabled()).thenReturn(true);
        when(signal.getWeight()).thenReturn(-0.5);
        when(signal.compute(any(), any(), any()))
                .thenReturn(new SignalContribution(1.0, List.of("informational")));

        var ranker = new AdvancedMentorRanker(List.of(signal));
        ScoreResult result = ranker.score(mentor, mentee, List.of(), List.of());
        assertThat(result.score()).isZero();
        assertThat(result.factors()).containsExactly("informational");
        verify(signal).compute(any(), any(), any());
    }

    @Test
    void enabledSignals_aggregateAndScaleToHundred() {
        var signal1 = stubSignal(0.5, 0.6, List.of("a", "b"));   // contributes 0.30
        var signal2 = stubSignal(0.5, 0.4, List.of("c"));        // contributes 0.20

        var ranker = new AdvancedMentorRanker(List.of(signal1, signal2));
        ScoreResult result = ranker.score(mentor, mentee, List.of(), List.of());

        // 0.5*0.6 + 0.5*0.4 = 0.5 → 50
        assertThat(result.score()).isEqualTo(50);
        assertThat(result.factors()).containsExactly("a", "b", "c");
        verify(signal1, times(1)).compute(any(), any(), any());
        verify(signal2, times(1)).compute(any(), any(), any());
    }

    @Test
    void negativeNormalizedScore_clampedToZero() {
        var signal = stubSignal(0.7, -1.0, List.of());
        var ranker = new AdvancedMentorRanker(List.of(signal));
        assertThat(ranker.score(mentor, mentee, List.of(), List.of()).score()).isZero();
    }

    @Test
    void overOneNormalizedScore_clampedToOne() {
        var signal = stubSignal(0.5, 2.5, List.of());
        var ranker = new AdvancedMentorRanker(List.of(signal));
        // After clamp(2.5,0,1)=1.0 → 0.5 weighted → 50
        assertThat(ranker.score(mentor, mentee, List.of(), List.of()).score()).isEqualTo(50);
    }

    @Test
    void weightedSumExceedingOne_clampedAtHundred() {
        // Two signals each with weight 0.7 and score 1.0 → naive sum 1.4
        var signal1 = stubSignal(0.7, 1.0, List.of("x"));
        var signal2 = stubSignal(0.7, 1.0, List.of("y"));
        var ranker = new AdvancedMentorRanker(List.of(signal1, signal2));
        assertThat(ranker.score(mentor, mentee, List.of(), List.of()).score()).isEqualTo(100);
    }

    @Test
    void perfectMatch_scoresOneHundred() {
        var ranker = new AdvancedMentorRanker(List.of(stubSignal(1.0, 1.0, List.of("perfect"))));
        assertThat(ranker.score(mentor, mentee, List.of(), List.of()).score()).isEqualTo(100);
    }

    @Test
    void factorOrdering_followsSignalRegistrationOrder() {
        var first = stubSignal(0.1, 1.0, List.of("first-a", "first-b"));
        var second = stubSignal(0.1, 1.0, List.of("second"));
        var ranker = new AdvancedMentorRanker(List.of(first, second));
        assertThat(ranker.score(mentor, mentee, List.of(), List.of()).factors())
                .containsExactly("first-a", "first-b", "second");
    }

    private static MentorScoringSignal stubSignal(double weight, double normalized, List<String> factors) {
        MentorScoringSignal signal = mock(MentorScoringSignal.class);
        when(signal.isEnabled()).thenReturn(true);
        when(signal.getWeight()).thenReturn(weight);
        when(signal.compute(any(), any(), any())).thenReturn(new SignalContribution(normalized, factors));
        return signal;
    }
}
