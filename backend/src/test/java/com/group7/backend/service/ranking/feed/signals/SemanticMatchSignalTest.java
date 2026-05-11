package com.group7.backend.service.ranking.feed.signals;

import com.group7.backend.config.ForYouRecommendationProperties;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.service.ranking.SignalContribution;
import com.group7.backend.service.ranking.feed.FeedScoringContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticMatchSignalTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T12:00:00Z");

    private static ForYouRecommendationProperties props(boolean semanticEnabled) {
        return new ForYouRecommendationProperties(
                new ForYouRecommendationProperties.Advanced(true),
                new ForYouRecommendationProperties.Weights(0.40, 0.20, 0.15, 0.25),
                new ForYouRecommendationProperties.Signals(semanticEnabled, true, true, true),
                new ForYouRecommendationProperties.TimeDecay(Duration.ofHours(24), 6),
                new ForYouRecommendationProperties.Affinity(30, 0.30, 10),
                new ForYouRecommendationProperties.Mmr(true, 0.7, 3),
                new ForYouRecommendationProperties.DiversityFloor(true, 3),
                new ForYouRecommendationProperties.Bandit(false, 0.10)
        );
    }

    @Test
    void killSwitchOff_isEnabledFalse() {
        SemanticMatchSignal signal = new SemanticMatchSignal(props(false), mock(SemanticSimilarityService.class));
        assertThat(signal.isEnabled()).isFalse();
        assertThat(signal.getWeight()).isEqualTo(0.40);
    }

    @Test
    void viewerHasNoInterests_returnsNoneNoFactor() {
        SemanticMatchSignal signal = new SemanticMatchSignal(props(true), mock(SemanticSimilarityService.class));
        FeedScoringContext ctx = ctxWith(Set.of(), new float[]{1, 0}, Map.of(1L, new float[]{1, 0}));

        SignalContribution out = signal.compute(post(1L, "body", List.of("ai")), ctx);

        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).isEmpty();
    }

    @Test
    void embedderUnavailable_fallsBackToJaccardAndEmitsSemanticUnavailable() {
        // Viewer embedding is empty (embedder not wired) — signal must fall
        // back rather than score every candidate at 0.
        SemanticMatchSignal signal = new SemanticMatchSignal(props(true), mock(SemanticSimilarityService.class));
        FeedScoringContext ctx = ctxWith(Set.of("ai", "data"), new float[0], Map.of());

        // Post has 2 tags, both match → Jaccard = 2/2 = 1.0
        SignalContribution out = signal.compute(post(1L, "body", List.of("ai", "data")), ctx);

        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("feed:semantic-unavailable");
    }

    @Test
    void postEmbeddingMissing_fallsBackToJaccard() {
        SemanticMatchSignal signal = new SemanticMatchSignal(props(true), mock(SemanticSimilarityService.class));
        // Viewer embedding present, but no embedding for the post in question.
        FeedScoringContext ctx = ctxWith(Set.of("ai"), new float[]{1, 0}, Map.of());

        SignalContribution out = signal.compute(post(1L, "body", List.of("ai", "ml")), ctx);

        // 1 of 2 tags matches → 0.5
        assertThat(out.normalizedScore()).isEqualTo(0.5);
        assertThat(out.factors()).containsExactly("feed:semantic-unavailable");
    }

    @Test
    void highCosine_emitsFormattedFactor() {
        SemanticSimilarityService sim = mock(SemanticSimilarityService.class);
        when(sim.cosineSimilarity(any(), any())).thenReturn(0.82);

        SemanticMatchSignal signal = new SemanticMatchSignal(props(true), sim);
        FeedScoringContext ctx = ctxWith(Set.of("ai"), new float[]{1, 0}, Map.of(1L, new float[]{1, 0}));

        SignalContribution out = signal.compute(post(1L, "body", List.of("ai")), ctx);

        assertThat(out.normalizedScore()).isEqualTo(0.82);
        assertThat(out.factors()).containsExactly("feed:semantic-match:0.82");
    }

    @Test
    void lowCosine_returnsScoreWithoutFactor() {
        SemanticSimilarityService sim = mock(SemanticSimilarityService.class);
        when(sim.cosineSimilarity(any(), any())).thenReturn(0.30);

        SemanticMatchSignal signal = new SemanticMatchSignal(props(true), sim);
        FeedScoringContext ctx = ctxWith(Set.of("ai"), new float[]{1, 0}, Map.of(1L, new float[]{1, 0}));

        SignalContribution out = signal.compute(post(1L, "body", List.of("ai")), ctx);

        // Below 0.5 threshold for the chip — score still flows.
        assertThat(out.normalizedScore()).isEqualTo(0.30);
        assertThat(out.factors()).isEmpty();
    }

    @Test
    void negativeCosineClampedToZero() {
        SemanticSimilarityService sim = mock(SemanticSimilarityService.class);
        when(sim.cosineSimilarity(any(), any())).thenReturn(-0.10);

        SemanticMatchSignal signal = new SemanticMatchSignal(props(true), sim);
        FeedScoringContext ctx = ctxWith(Set.of("ai"), new float[]{1, 0}, Map.of(1L, new float[]{1, 0}));

        SignalContribution out = signal.compute(post(1L, "body", List.of("ai")), ctx);

        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).isEmpty();
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private FeedScoringContext ctxWith(Set<String> interests, float[] viewerEmbed, Map<Long, float[]> postEmbeds) {
        return new FeedScoringContext(
                1L, interests, Set.of(), NOW,
                Map.of(), 0.0,
                Map.of(),
                viewerEmbed,
                postEmbeds
        );
    }

    private static FeedPost post(long id, String body, List<String> tags) {
        FeedPost p = new FeedPost(99L, body);
        p.setId(id);
        p.setCreatedAt(NOW);
        p.setUpdatedAt(NOW);
        LinkedHashSet<FeedPostHashtag> hashtags = new LinkedHashSet<>();
        for (String t : tags) {
            hashtags.add(new FeedPostHashtag(p, t));
        }
        p.setHashtags(hashtags);
        return p;
    }
}
