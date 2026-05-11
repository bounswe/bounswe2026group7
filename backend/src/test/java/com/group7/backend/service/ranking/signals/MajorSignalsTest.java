package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers both {@link MajorExactSignal} and {@link MajorFieldSignal} —
 * the two halves of the major-match family. They share a kill switch
 * ({@code signals.major.enabled}) but tune their weights independently.
 */
class MajorSignalsTest {

    private static final ScoringContext EMPTY_CTX = new ScoringContext(List.of(), List.of());

    private static MentorRecommendationProperties props(boolean majorEnabled,
                                                        double majorExactW, double majorFieldW) {
        return new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, 0, 0, majorExactW, majorFieldW, 0, 0),
                new MentorRecommendationProperties.Signals(false, false, false, majorEnabled, false, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                null);
    }

    // ── MajorExactSignal ────────────────────────────────────────────────

    @Test
    void exact_killSwitch_off() {
        var p = props(false, 0.2, 0.05);
        assertThat(new MajorExactSignal(p).isEnabled()).isFalse();
    }

    @Test
    void exact_nullSignalsRecord_disabled() {
        var p = new MentorRecommendationProperties(null, null, null, null, null);
        assertThat(new MajorExactSignal(p).isEnabled()).isFalse();
        assertThat(new MajorExactSignal(p).getWeight()).isZero();
    }

    @Test
    void exact_nullEitherSide_returnsNone() {
        var signal = new MajorExactSignal(props(true, 0.2, 0.05));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setPreferredMenteeMajor("Computer Science");
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
        mentee.setMajor("Computer Science");
        mentor.setPreferredMenteeMajor(null);
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void exact_match_caseInsensitive_emitsFactor() {
        var signal = new MajorExactSignal(props(true, 0.2, 0.05));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setPreferredMenteeMajor("Computer Science");
        mentee.setMajor("COMPUTER SCIENCE");
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("major-exact-match");
    }

    @Test
    void exact_mismatch_returnsNone() {
        var signal = new MajorExactSignal(props(true, 0.2, 0.05));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setPreferredMenteeMajor("Computer Science");
        mentee.setMajor("Electrical Engineering");
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
    }

    // ── MajorFieldSignal ────────────────────────────────────────────────

    @Test
    void field_killSwitch_off() {
        assertThat(new MajorFieldSignal(props(false, 0.2, 0.05)).isEnabled()).isFalse();
    }

    @Test
    void field_nullSignalsRecord_disabled() {
        var p = new MentorRecommendationProperties(null, null, null, null, null);
        assertThat(new MajorFieldSignal(p).isEnabled()).isFalse();
        assertThat(new MajorFieldSignal(p).getWeight()).isZero();
    }

    @Test
    void field_nullEitherSide_returnsNone() {
        var signal = new MajorFieldSignal(props(true, 0.2, 0.05));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setField("Computer Science");
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
        mentee.setMajor("Computer Science");
        mentor.setField(null);
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void field_match_caseInsensitive_emitsFactor() {
        var signal = new MajorFieldSignal(props(true, 0.2, 0.05));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setField("Computer Science");
        mentee.setMajor("computer science");
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("major-field-overlap");
    }

    @Test
    void field_mismatch_returnsNone() {
        var signal = new MajorFieldSignal(props(true, 0.2, 0.05));
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setField("Software Engineering");
        mentee.setMajor("Mathematics");
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void code_returnsCorrectIdentifiers() {
        var p = props(true, 0.2, 0.05);
        assertThat(new MajorExactSignal(p).code()).isEqualTo("major-exact-match");
        assertThat(new MajorFieldSignal(p).code()).isEqualTo("major-field-overlap");
    }
}
