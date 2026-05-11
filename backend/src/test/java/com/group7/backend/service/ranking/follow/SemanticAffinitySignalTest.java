package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SemanticAffinitySignalTest {

    @Mock private SemanticSimilarityService semantic;

    private final SemanticAffinitySignal signal() {
        return new SemanticAffinitySignal(semantic, props());
    }

    @Test
    void viewerEmbeddingMissing_returnsSemanticUnavailable_andSkipsEmbedCall() {
        SignalContribution out = signal().compute(mentorWithBio(7L, "ml expert"),
                ctx(null /*no viewer vec*/));
        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).containsExactly("semantic-unavailable");
        verify(semantic, never()).embed(anyString());
    }

    @Test
    void viewerEmbeddingEmpty_returnsSemanticUnavailable() {
        SignalContribution out = signal().compute(mentorWithBio(7L, "ml expert"),
                ctx(new float[0]));
        assertThat(out.factors()).containsExactly("semantic-unavailable");
        verify(semantic, never()).embed(anyString());
    }

    @Test
    void candidateProfileBlank_returnsNone_andSkipsEmbedCall() {
        Mentor m = new Mentor();
        m.setId(7L); m.setEmail("m@x.com"); m.setFirstName("F"); m.setLastName("L");
        // no bio, no expertise, no field, no interests, no goals

        SignalContribution out = signal().compute(m, ctx(new float[]{1f, 0f}));
        assertThat(out).isSameAs(SignalContribution.NONE);
        verify(semantic, never()).embed(anyString());
    }

    @Test
    void candidateEmbeddingEmpty_returnsSemanticUnavailable() {
        when(semantic.embed(anyString())).thenReturn(new float[0]);
        SignalContribution out = signal().compute(mentorWithBio(7L, "ml expert"),
                ctx(new float[]{1f, 0f}));
        assertThat(out.factors()).containsExactly("semantic-unavailable");
    }

    @Test
    void zeroSimilarity_returnsNone() {
        // viewer = (1,0), candidate = (0,1) → cosine = 0 → NONE
        when(semantic.embed(anyString())).thenReturn(new float[]{0f, 1f});
        SignalContribution out = signal().compute(mentorWithBio(7L, "ml expert"),
                ctx(new float[]{1f, 0f}));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void positiveSimilarity_emitsScoreFactor_withRoundedValue() {
        // viewer = (3,4), candidate = (4,3) → cosine ≈ 0.96
        when(semantic.embed(anyString())).thenReturn(new float[]{4f, 3f});
        SignalContribution out = signal().compute(mentorWithBio(7L, "ml expert"),
                ctx(new float[]{3f, 4f}));
        assertThat(out.normalizedScore()).isGreaterThan(0.95);
        assertThat(out.factors()).containsExactly("semantic-affinity:0.96");
    }

    @Test
    void perfectMatch_emitsFullScore() {
        float[] v = {0.6f, 0.8f};
        when(semantic.embed(anyString())).thenReturn(v);
        SignalContribution out = signal().compute(mentorWithBio(7L, "ml"), ctx(v));
        assertThat(out.normalizedScore()).isGreaterThan(0.99);
        assertThat(out.factors().get(0)).startsWith("semantic-affinity:1.00");
    }

    @Test
    void contractMetadata_isExposed() {
        SemanticAffinitySignal s = signal();
        assertThat(s.code()).isEqualTo("semantic-affinity");
        assertThat(s.isEnabled()).isTrue();
        assertThat(s.getWeight()).isEqualTo(0.18);
    }

    // ── candidateProfileText ── covers Mentor, Mentee, Admin (no-text) ─────

    @Test
    void candidateProfileText_mentor_concatenatesAllFields() {
        Mentor m = new Mentor();
        m.setBio("ml expert");
        m.setExpertise("nlp");
        m.setField("CS");
        m.setInterests(List.of("ai", "ml"));
        m.setMentoringGoals("teach beginners");

        String text = SemanticAffinitySignal.candidateProfileText(m);
        assertThat(text).contains("ml expert", "nlp", "CS", "ai", "ml", "teach beginners");
    }

    @Test
    void candidateProfileText_mentee_concatenatesAllFields() {
        Mentee me = new Mentee();
        me.setGoals("learn ml");
        me.setCareerInterest("data science");
        me.setMajor("statistics");
        me.setInterests(List.of("python", "torch"));
        me.setBackgroundInfo("undergrad");

        String text = SemanticAffinitySignal.candidateProfileText(me);
        assertThat(text).contains("learn ml", "data science", "statistics",
                "python", "torch", "undergrad");
    }

    @Test
    void candidateProfileText_admin_returnsEmpty() {
        com.group7.backend.entity.Admin a = new com.group7.backend.entity.Admin();
        assertThat(SemanticAffinitySignal.candidateProfileText(a)).isEmpty();
    }

    @Test
    void candidateProfileText_skipsBlankFields() {
        Mentor m = new Mentor();
        m.setBio("only bio");
        m.setExpertise(""); m.setField(null); m.setInterests(null); m.setMentoringGoals("  ");

        String text = SemanticAffinitySignal.candidateProfileText(m);
        assertThat(text).isEqualTo("only bio");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static FollowRecommendationContext ctx(float[] viewerEmbedding) {
        return new FollowRecommendationContext(
                42L, Set.of(), Set.of(), Map.of(),
                Map.of(), Map.of(), Set.of(), Map.of(),
                viewerEmbedding, false, OffsetDateTime.now());
    }

    private static User mentorWithBio(Long id, String bio) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setEmail("m" + id + "@x.com");
        m.setFirstName("F"); m.setLastName("L");
        m.setBio(bio);
        return m;
    }

    private static FollowRecommendationProperties props() {
        return new FollowRecommendationProperties(
                "advanced",
                new FollowRecommendationProperties.Weights(0.10, 0.13, 0.22, 0.13, 0.12, 0.18, 0.12),
                new FollowRecommendationProperties.Signals(true, true, true, true, true, true, true),
                new FollowRecommendationProperties.Ppr(0.85, 20, 2000, 10, 30),
                new FollowRecommendationProperties.Mmr(true, 0.65, 20, 10),
                new FollowRecommendationProperties.Engagement(30, 14, 1.0, 3.0, 4.0),
                new FollowRecommendationProperties.ColdStart(90, 64, 30, 0.6, 0.4));
    }
}
