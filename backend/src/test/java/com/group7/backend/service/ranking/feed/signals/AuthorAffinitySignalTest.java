package com.group7.backend.service.ranking.feed.signals;

import com.group7.backend.config.ForYouRecommendationProperties;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.service.ranking.SignalContribution;
import com.group7.backend.service.ranking.feed.FeedScoringContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorAffinitySignalTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T12:00:00Z");
    private static final long VIEWER_ID = 1L;
    private static final long OTHER_AUTHOR = 50L;

    private static ForYouRecommendationProperties props(boolean enabled) {
        return new ForYouRecommendationProperties(
                new ForYouRecommendationProperties.Advanced(true),
                new ForYouRecommendationProperties.Weights(0.40, 0.20, 0.15, 0.25),
                new ForYouRecommendationProperties.Signals(true, true, enabled, true),
                new ForYouRecommendationProperties.TimeDecay(Duration.ofHours(24), 6),
                new ForYouRecommendationProperties.Affinity(30, 0.30, 10),
                new ForYouRecommendationProperties.Mmr(true, 0.7, 3),
                new ForYouRecommendationProperties.DiversityFloor(true, 3),
                new ForYouRecommendationProperties.Bandit(false, 0.10)
        );
    }

    @Test
    void killSwitchOff_isEnabledFalse() {
        AuthorAffinitySignal signal = new AuthorAffinitySignal(props(false));
        assertThat(signal.isEnabled()).isFalse();
        assertThat(signal.getWeight()).isEqualTo(0.15);
    }

    @Test
    void selfAuthorExcluded_returnsNoneNoFactor() {
        AuthorAffinitySignal signal = new AuthorAffinitySignal(props(true));
        FeedScoringContext ctx = ctx(Set.of(), Map.of(VIEWER_ID, 5));

        // Author == viewer → self-affinity must not surface.
        SignalContribution out = signal.compute(postBy(VIEWER_ID), ctx);

        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).isEmpty();
    }

    @Test
    void followedNoEngagement_baseScoreAndFollowBoostFactor() {
        AuthorAffinitySignal signal = new AuthorAffinitySignal(props(true));
        FeedScoringContext ctx = ctx(Set.of(OTHER_AUTHOR), Map.of());

        SignalContribution out = signal.compute(postBy(OTHER_AUTHOR), ctx);

        assertThat(out.normalizedScore()).isEqualTo(0.30);
        assertThat(out.factors()).containsExactly("feed:follow-boost");
    }

    @Test
    void notFollowedFullEngagement_saturatesToOne() {
        AuthorAffinitySignal signal = new AuthorAffinitySignal(props(true));
        FeedScoringContext ctx = ctx(Set.of(), Map.of(OTHER_AUTHOR, 10));

        SignalContribution out = signal.compute(postBy(OTHER_AUTHOR), ctx);

        // base=0 + 10/10 = 1.0, only author-affinity chip
        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("feed:author-affinity:10");
    }

    @Test
    void notFollowedPartialEngagement_proportional() {
        AuthorAffinitySignal signal = new AuthorAffinitySignal(props(true));
        FeedScoringContext ctx = ctx(Set.of(), Map.of(OTHER_AUTHOR, 5));

        SignalContribution out = signal.compute(postBy(OTHER_AUTHOR), ctx);

        // base=0 + 5/10 = 0.5
        assertThat(out.normalizedScore()).isEqualTo(0.5);
        assertThat(out.factors()).containsExactly("feed:author-affinity:5");
    }

    @Test
    void followedAndEngaged_bothFactorsAndScoreCombined() {
        AuthorAffinitySignal signal = new AuthorAffinitySignal(props(true));
        FeedScoringContext ctx = ctx(Set.of(OTHER_AUTHOR), Map.of(OTHER_AUTHOR, 4));

        SignalContribution out = signal.compute(postBy(OTHER_AUTHOR), ctx);

        // base=0.30 + 4/10 = 0.70
        assertThat(out.normalizedScore()).isCloseTo(0.70, within());
        assertThat(out.factors()).containsExactly("feed:follow-boost", "feed:author-affinity:4");
    }

    @Test
    void followedAndOverSaturated_clampsToOne() {
        AuthorAffinitySignal signal = new AuthorAffinitySignal(props(true));
        FeedScoringContext ctx = ctx(Set.of(OTHER_AUTHOR), Map.of(OTHER_AUTHOR, 100));

        SignalContribution out = signal.compute(postBy(OTHER_AUTHOR), ctx);

        // base=0.30 + 1.0 (saturated) = 1.30 → clamps to 1.0
        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("feed:follow-boost", "feed:author-affinity:100");
    }

    @Test
    void nullAuthorId_returnsNone() {
        AuthorAffinitySignal signal = new AuthorAffinitySignal(props(true));
        FeedScoringContext ctx = ctx(Set.of(), Map.of());
        FeedPost orphan = new FeedPost(null, "body");
        orphan.setId(1L);
        orphan.setCreatedAt(NOW);

        SignalContribution out = signal.compute(orphan, ctx);

        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).isEmpty();
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private FeedScoringContext ctx(Set<Long> followedAuthorIds, Map<Long, Integer> affinityCounts) {
        return new FeedScoringContext(
                VIEWER_ID, Set.of(), followedAuthorIds, NOW,
                Map.of(), 0.0,
                affinityCounts,
                new float[0],
                Map.of()
        );
    }

    private static FeedPost postBy(long authorId) {
        FeedPost p = new FeedPost(authorId, "body");
        p.setId(99L);
        p.setCreatedAt(NOW);
        return p;
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(0.0001);
    }
}
