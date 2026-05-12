package com.group7.backend.service.ranking.feed;

import com.group7.backend.config.ForYouRecommendationProperties;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.repository.AuthorAffinityRepository;
import com.group7.backend.repository.FeedEngagementCountsRepository;
import com.group7.backend.service.bandit.ThompsonSamplingService;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.service.ranking.AdvancedForYouFeedRanker;
import com.group7.backend.service.ranking.MmrReranker;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Orchestration coverage for {@link ForYouScoringPipeline}. Verifies the
 * end-to-end flow (precompute → score → sort → MMR → diversity floor →
 * bandit slot) with mocked repository/embedding/bandit dependencies and
 * a real {@link AdvancedForYouFeedRanker} fed a fake signal.
 */
class ForYouScoringPipelineTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T12:00:00Z");
    private static final long VIEWER_ID = 7L;

    private FeedEngagementCountsRepository engagementRepo;
    private AuthorAffinityRepository affinityRepo;
    private SemanticSimilarityService semantic;
    private ThompsonSamplingService bandit;

    private ForYouScoringPipeline pipeline;
    private ForYouRecommendationProperties props;

    @BeforeEach
    void setup() {
        engagementRepo = mock(FeedEngagementCountsRepository.class);
        affinityRepo = mock(AuthorAffinityRepository.class);
        semantic = mock(SemanticSimilarityService.class);
        bandit = mock(ThompsonSamplingService.class);

        // No precompute data by default → signals score 0; rely on the fake
        // FreshnessSignal injected into the ranker to produce a sortable
        // ordering by the post's age.
        when(engagementRepo.weightedCountsFor(any())).thenReturn(Map.of());
        when(affinityRepo.weightedCountsByAuthor(anyLong(), any())).thenReturn(Map.of());
        when(semantic.embed(anyString())).thenReturn(new float[0]);
        when(bandit.samplePosterior(anyLong(), anyString())).thenReturn(0.5);

        props = defaultProps();
        // Full-weight freshness so the freshness-decayed scores stay distinct
        // after the int [0,100] rounding (small weights compress the
        // spread into 24-25 and MMR ties depend on input order, which is
        // not what we're testing here).
        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(
                List.of(new FreshnessSignal(1.0)));
        pipeline = new ForYouScoringPipeline(
                ranker, engagementRepo, affinityRepo, semantic,
                new MmrReranker(), new DiversityFloorEnforcer(), bandit, props);
    }

    @Test
    void emptyCandidates_returnsEmpty() {
        List<ForYouScoringPipeline.RankedFeedPost> out = pipeline.rank(
                List.of(), VIEWER_ID, Set.of(), Set.of(), NOW, 10, 0);
        assertThat(out).isEmpty();
    }

    @Test
    void scoresAndOrdersByFreshness_pageZero() {
        // Three posts, increasing age. FreshnessSignal returns higher
        // score for newer posts → expected order: now, -1h, -2h.
        FeedPost p1 = post(1L, List.of("ai"), NOW);
        FeedPost p2 = post(2L, List.of("ai"), NOW.minusHours(1));
        FeedPost p3 = post(3L, List.of("ai"), NOW.minusHours(2));

        List<ForYouScoringPipeline.RankedFeedPost> out = pipeline.rank(
                List.of(p3, p1, p2), VIEWER_ID, Set.of(), Set.of(), NOW, 10, 0);

        assertThat(out).extracting(r -> r.post().getId())
                .containsExactly(1L, 2L, 3L);
        // FreshnessSignal emits "feed:fresh" for every post; the pipeline
        // also adds feed:diverse-pick to one MMR-lifted item, IF any
        // moves up. With three same-tag posts and empty embeddings, MMR
        // similarity is forced to 1 (zero-vector clamp), so MMR collapses
        // to a no-op — no diverse-pick should fire.
        assertThat(out.get(0).factors()).containsExactly("feed:fresh");
    }

    @Test
    void diversityFloor_swapsInOutsider_whenAllPlacedShareTopHashtag() {
        // Build a pool where "ai" is clearly the #1 hashtag and "cooking"
        // is unique. With topHashtagsCount=1 (set via floorProps below), "ai"
        // is the only "primary" tag, so cooking is an outsider.
        props = floorProps(/* topHashtagsCount */ 1, /* mmrEnabled */ false);
        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(
                List.of(new FreshnessSignal(1.0)));
        pipeline = new ForYouScoringPipeline(
                ranker, engagementRepo, affinityRepo, semantic,
                new MmrReranker(), new DiversityFloorEnforcer(), bandit, props);

        FeedPost ai1 = post(1L, List.of("ai"), NOW);
        FeedPost ai2 = post(2L, List.of("ai"), NOW.minusMinutes(10));
        FeedPost cooking = post(3L, List.of("cooking"), NOW.minusHours(2)); // older → lower score

        List<ForYouScoringPipeline.RankedFeedPost> out = pipeline.rank(
                List.of(ai1, ai2, cooking), VIEWER_ID, Set.of(), Set.of(), NOW, 2, 0);

        // Page-of-2 by score = [ai1, ai2]. Both share "ai" which IS the
        // top-1 hashtag in the pool. cooking is in the candidate pool as
        // an outsider. Diversity floor should swap ai2 (lower score) for
        // cooking, emitting feed:outside-primary-goal.
        assertThat(out).hasSize(2);
        assertThat(out.get(0).post().getId()).isEqualTo(ai1.getId());
        assertThat(out.get(1).post().getId()).isEqualTo(cooking.getId());
        assertThat(out.get(1).factors()).contains("feed:outside-primary-goal");
    }

    @Test
    void pageOne_skipsDiversityFloorAndBandit() {
        // Use topHashtagsCount=1 + bigger time gaps so freshness scores
        // are distinct after int rounding, and "ai" is the sole top tag.
        props = floorProps(/* topHashtagsCount */ 1, /* mmrEnabled */ false);
        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(
                List.of(new FreshnessSignal(1.0)));
        pipeline = new ForYouScoringPipeline(
                ranker, engagementRepo, affinityRepo, semantic,
                new MmrReranker(), new DiversityFloorEnforcer(), bandit, props);

        FeedPost ai1 = post(1L, List.of("ai"), NOW);
        FeedPost ai2 = post(2L, List.of("ai"), NOW.minusHours(2));
        FeedPost ai3 = post(3L, List.of("ai"), NOW.minusHours(4));
        FeedPost cooking = post(4L, List.of("cooking"), NOW.minusHours(8));

        // pageSize=1, pageNumber=1 → second-best by freshness (ai2).
        List<ForYouScoringPipeline.RankedFeedPost> out = pipeline.rank(
                List.of(ai3, cooking, ai1, ai2), VIEWER_ID, Set.of(), Set.of(),
                NOW, 1, 1);

        // Expected: page 1 contains ai2. The diversity-floor swap-in
        // (cooking) would have fired only on page 0; page 1 must be pure
        // weighted+MMR order with no bandit/floor factors.
        assertThat(out).hasSize(1);
        assertThat(out.get(0).post().getId()).isEqualTo(ai2.getId());
        assertThat(out.get(0).factors()).doesNotContain("feed:outside-primary-goal");
        assertThat(out.get(0).factors()).doesNotContain("feed:bandit-exploration");
    }

    @Test
    void banditOff_byDefault_noBanditFactor() {
        FeedPost ai1 = post(1L, List.of("ai"), NOW);
        FeedPost ai2 = post(2L, List.of("ai"), NOW.minusMinutes(10));

        List<ForYouScoringPipeline.RankedFeedPost> out = pipeline.rank(
                List.of(ai1, ai2), VIEWER_ID, Set.of(), Set.of(), NOW, 10, 0);

        assertThat(out).flatExtracting(ForYouScoringPipeline.RankedFeedPost::factors)
                .doesNotContain("feed:bandit-exploration");
    }

    @Test
    void banditOn_pageZero_assignsBanditExplorationFactor() {
        // Bandit enabled and 10% exploration on a 10-slot page → 1 slot.
        props = defaultPropsWithBandit(true);
        pipeline = new ForYouScoringPipeline(
                new AdvancedForYouFeedRanker(List.of(new FreshnessSignal(0.25))),
                engagementRepo, affinityRepo, semantic,
                new MmrReranker(), new DiversityFloorEnforcer(), bandit, props);

        // Sample distribution: cooking gets a high draw, ai gets low.
        when(bandit.samplePosterior(anyLong(), org.mockito.ArgumentMatchers.eq("cooking"))).thenReturn(0.99);
        when(bandit.samplePosterior(anyLong(), org.mockito.ArgumentMatchers.eq("ai"))).thenReturn(0.10);

        List<FeedPost> pool = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) pool.add(post(i, List.of("ai"), NOW.minusMinutes(i)));
        FeedPost cooking = post(99L, List.of("cooking"), NOW.minusHours(10)); // very old, low organic score
        pool.add(cooking);

        List<ForYouScoringPipeline.RankedFeedPost> out = pipeline.rank(
                pool, VIEWER_ID, Set.of(), Set.of(), NOW, 10, 0);

        // The bandit replaced the lowest-scored placed slot with the
        // highest-sampled-hashtag candidate (cooking).
        boolean cookingPlaced = out.stream()
                .anyMatch(r -> r.post().getId().equals(cooking.getId())
                        && r.factors().contains("feed:bandit-exploration"));
        assertThat(cookingPlaced).isTrue();
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static ForYouRecommendationProperties defaultProps() {
        return new ForYouRecommendationProperties(
                new ForYouRecommendationProperties.Advanced(true),
                new ForYouRecommendationProperties.Weights(0.0, 0.0, 0.0, 1.0),
                new ForYouRecommendationProperties.Signals(true, true, true, true),
                new ForYouRecommendationProperties.TimeDecay(Duration.ofHours(24), 6),
                new ForYouRecommendationProperties.Affinity(30, 0.30, 10),
                new ForYouRecommendationProperties.Mmr(true, 0.7, 3),
                new ForYouRecommendationProperties.DiversityFloor(true, 3),
                new ForYouRecommendationProperties.Bandit(false, 0.10));
    }

    private static ForYouRecommendationProperties floorProps(int topHashtagsCount, boolean mmrEnabled) {
        return new ForYouRecommendationProperties(
                new ForYouRecommendationProperties.Advanced(true),
                new ForYouRecommendationProperties.Weights(0.0, 0.0, 0.0, 1.0),
                new ForYouRecommendationProperties.Signals(true, true, true, true),
                new ForYouRecommendationProperties.TimeDecay(Duration.ofHours(24), 6),
                new ForYouRecommendationProperties.Affinity(30, 0.30, 10),
                new ForYouRecommendationProperties.Mmr(mmrEnabled, 0.7, 3),
                new ForYouRecommendationProperties.DiversityFloor(true, topHashtagsCount),
                new ForYouRecommendationProperties.Bandit(false, 0.10));
    }

    private static ForYouRecommendationProperties defaultPropsWithBandit(boolean banditEnabled) {
        return new ForYouRecommendationProperties(
                new ForYouRecommendationProperties.Advanced(true),
                new ForYouRecommendationProperties.Weights(0.0, 0.0, 0.0, 1.0),
                new ForYouRecommendationProperties.Signals(true, true, true, true),
                new ForYouRecommendationProperties.TimeDecay(Duration.ofHours(24), 6),
                new ForYouRecommendationProperties.Affinity(30, 0.30, 10),
                new ForYouRecommendationProperties.Mmr(true, 0.7, 3),
                new ForYouRecommendationProperties.DiversityFloor(false, 3),
                new ForYouRecommendationProperties.Bandit(banditEnabled, 0.10));
    }

    private static FeedPost post(long id, List<String> tags, OffsetDateTime createdAt) {
        FeedPost p = new FeedPost(99L, "body");
        p.setId(id);
        p.setCreatedAt(createdAt);
        LinkedHashSet<FeedPostHashtag> hashtags = new LinkedHashSet<>();
        for (String t : tags) {
            hashtags.add(new FeedPostHashtag(p, t));
        }
        p.setHashtags(hashtags);
        return p;
    }

    /**
     * Minimal signal that lets the pipeline tests rank by post recency
     * without pulling in the real TimeDecaySignal's properties dependency.
     */
    private static final class FreshnessSignal implements FeedScoringSignal {
        private final double weight;
        FreshnessSignal(double weight) { this.weight = weight; }
        @Override public String code() { return "freshness-test"; }
        @Override public boolean isEnabled() { return true; }
        @Override public double getWeight() { return weight; }
        @Override public SignalContribution compute(FeedPost post, FeedScoringContext context) {
            long ageSeconds = Math.max(0,
                    java.time.Duration.between(post.getCreatedAt(), context.now()).getSeconds());
            double halflife = 3600.0 * 24.0;
            double score = Math.exp(-ageSeconds * Math.log(2) / halflife);
            return new SignalContribution(score, List.of("feed:fresh"));
        }
    }
}
