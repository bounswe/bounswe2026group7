package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SharedInterestsSignalTest {

    private static final ScoringContext EMPTY_CTX = new ScoringContext(List.of(), List.of());

    private static MentorRecommendationProperties propsEnabled(double weight) {
        return new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, weight, 0, 0, 0, 0, 0),
                new MentorRecommendationProperties.Signals(false, true, false, false, false, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                null);
    }

    @Test
    void killSwitchOff_reportsDisabled() {
        var props = new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, 1, 0, 0, 0, 0, 0),
                new MentorRecommendationProperties.Signals(false, false, false, false, false, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                null);
        assertThat(new SharedInterestsSignal(props).isEnabled()).isFalse();
    }

    @Test
    void nullSignalsRecord_reportsDisabled() {
        var props = new MentorRecommendationProperties(null, null, null, null, null);
        var signal = new SharedInterestsSignal(props);
        assertThat(signal.isEnabled()).isFalse();
        assertThat(signal.getWeight()).isZero();
    }

    @Test
    void emptyEitherSide_returnsNone() {
        var signal = new SharedInterestsSignal(propsEnabled(1.0));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setInterests(List.of("AI"));
        // mentee.interests left null
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);

        mentee.setInterests(List.of("AI"));
        mentor.setInterests(null);
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void noOverlap_returnsNone() {
        var signal = new SharedInterestsSignal(propsEnabled(1.0));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setInterests(List.of("AI"));
        mentee.setInterests(List.of("Databases"));
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void overlap_scoresJaccardAndEmitsFactors() {
        var signal = new SharedInterestsSignal(propsEnabled(1.0));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setInterests(List.of("AI", "Systems"));
        mentee.setInterests(List.of("ai", "Databases")); // case-insensitive match

        SignalContribution out = signal.compute(mentor, mentee, EMPTY_CTX);
        // mentor={ai,systems}, mentee={ai,databases}, ∩={ai}, ∪={ai,systems,databases} → 1/3
        assertThat(out.normalizedScore()).isCloseTo(1.0 / 3.0, offset(1e-9));
        // mentee's original casing preserved in the factor label.
        assertThat(out.factors()).containsExactly("shared-interest:ai");
    }

    @Test
    void factorListCappedAtThree() {
        var signal = new SharedInterestsSignal(propsEnabled(1.0));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setInterests(List.of("a", "b", "c", "d", "e"));
        mentee.setInterests(List.of("a", "b", "c", "d", "e"));
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.factors()).hasSize(3);
    }

    @Test
    void nullEntriesInListsAreSkippedNotCrashes() {
        var signal = new SharedInterestsSignal(propsEnabled(1.0));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setInterests(java.util.Arrays.asList("AI", null));
        mentee.setInterests(java.util.Arrays.asList(null, "ai"));
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.factors()).containsExactly("shared-interest:ai");
    }
}
