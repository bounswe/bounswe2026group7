package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.service.ranking.ScoringContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticMatchSignalTest {

    private static final ScoringContext EMPTY_CTX = new ScoringContext(List.of(), List.of());

    private static MentorRecommendationProperties props(boolean enabled, double weight) {
        return new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(weight, 0, 0, 0, 0, 0, 0),
                new MentorRecommendationProperties.Signals(enabled, false, false, false, false, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                null);
    }

    @Test
    void killSwitchOff() {
        var signal = new SemanticMatchSignal(mock(SemanticSimilarityService.class), props(false, 0.3));
        assertThat(signal.isEnabled()).isFalse();
    }

    @Test
    void nullSignalsRecord_disabledAndZeroWeight() {
        var sim = mock(SemanticSimilarityService.class);
        var p = new MentorRecommendationProperties(null, null, null, null, null);
        var signal = new SemanticMatchSignal(sim, p);
        assertThat(signal.isEnabled()).isFalse();
        assertThat(signal.getWeight()).isZero();
    }

    @Test
    void embeddingUnavailable_mentorSide_returnsSemanticUnavailable() {
        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f}, new float[0]);
        var mentor = new Mentor(); mentor.setExpertise("react");
        var mentee = new Mentee(); mentee.setGoals("learn react");

        var out = new SemanticMatchSignal(sim, props(true, 0.3)).compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).containsExactly("semantic-unavailable");
    }

    @Test
    void embeddingUnavailable_menteeSide_returnsSemanticUnavailable() {
        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenReturn(new float[0], new float[]{0.1f});
        var mentor = new Mentor(); mentor.setExpertise("react");
        var mentee = new Mentee(); mentee.setGoals("learn react");
        var out = new SemanticMatchSignal(sim, props(true, 0.3)).compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.factors()).containsExactly("semantic-unavailable");
    }

    @Test
    void highCosine_emitsFactorAndScore() {
        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenReturn(new float[]{1, 0});
        when(sim.cosineSimilarity(any(), any())).thenReturn(0.84);

        var mentor = new Mentor(); mentor.setExpertise("react");
        var mentee = new Mentee(); mentee.setGoals("learn react");
        var out = new SemanticMatchSignal(sim, props(true, 0.3)).compute(mentor, mentee, EMPTY_CTX);

        assertThat(out.normalizedScore()).isEqualTo(0.84);
        assertThat(out.factors()).containsExactly("semantic-match:0.84");
    }

    @Test
    void lowCosineBelowThreshold_scoresButEmitsNoFactor() {
        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenReturn(new float[]{1, 0});
        when(sim.cosineSimilarity(any(), any())).thenReturn(0.3);

        var mentor = new Mentor(); mentor.setExpertise("react");
        var mentee = new Mentee(); mentee.setGoals("learn react");
        var out = new SemanticMatchSignal(sim, props(true, 0.3)).compute(mentor, mentee, EMPTY_CTX);

        assertThat(out.normalizedScore()).isEqualTo(0.3);
        assertThat(out.factors()).isEmpty();
    }

    @Test
    void negativeCosine_clampedToZero() {
        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenReturn(new float[]{1, 0});
        when(sim.cosineSimilarity(any(), any())).thenReturn(-0.4);

        var mentor = new Mentor(); mentor.setExpertise("react");
        var mentee = new Mentee(); mentee.setGoals("learn react");
        var out = new SemanticMatchSignal(sim, props(true, 0.3)).compute(mentor, mentee, EMPTY_CTX);

        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).isEmpty();
    }

    @Test
    void code() {
        var signal = new SemanticMatchSignal(mock(SemanticSimilarityService.class), props(true, 0.3));
        assertThat(signal.code()).isEqualTo("semantic-match");
    }
}
