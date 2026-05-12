package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SharedSkillsSignalTest {

    private static final ScoringContext EMPTY_CTX = new ScoringContext(List.of(), List.of());

    private static MentorRecommendationProperties propsEnabled() {
        return new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, 0, 0.5, 0, 0, 0, 0),
                new MentorRecommendationProperties.Signals(false, false, true, false, false, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                null);
    }

    @Test
    void disabledSignal() {
        var props = new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, 0, 0.5, 0, 0, 0, 0),
                new MentorRecommendationProperties.Signals(false, false, false, false, false, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                null);
        assertThat(new SharedSkillsSignal(props).isEnabled()).isFalse();
    }

    @Test
    void nullRecords_safeDefaults() {
        var signal = new SharedSkillsSignal(new MentorRecommendationProperties(null, null, null, null, null));
        assertThat(signal.isEnabled()).isFalse();
        assertThat(signal.getWeight()).isZero();
    }

    @Test
    void emptyEitherSide_returnsNone() {
        var signal = new SharedSkillsSignal(propsEnabled());
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setPreferredMenteeSkills(List.of("Java"));
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
        mentee.setSkills(List.of("Java"));
        mentor.setPreferredMenteeSkills(null);
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void noOverlap_returnsNone() {
        var signal = new SharedSkillsSignal(propsEnabled());
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setPreferredMenteeSkills(List.of("Kotlin"));
        mentee.setSkills(List.of("Python"));
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void overlap_emitsCappedFactorsPreservingMenteeCase() {
        var signal = new SharedSkillsSignal(propsEnabled());
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setPreferredMenteeSkills(List.of("java", "Kotlin", "go", "rust", "scala"));
        mentee.setSkills(List.of("Java", "GO", "Rust", "Scala", "Kotlin"));

        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.normalizedScore()).isCloseTo(1.0, offset(1e-9));
        assertThat(out.factors()).hasSize(3);                       // capped
        assertThat(out.factors().get(0)).isEqualTo("shared-skill:Java"); // mentee case
    }

    @Test
    void nullEntriesAreSkipped() {
        var signal = new SharedSkillsSignal(propsEnabled());
        var mentor = new Mentor();
        var mentee = new Mentee();
        mentor.setPreferredMenteeSkills(Arrays.asList("Java", null));
        mentee.setSkills(Arrays.asList(null, "java"));
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.factors()).containsExactly("shared-skill:java");
    }
}
