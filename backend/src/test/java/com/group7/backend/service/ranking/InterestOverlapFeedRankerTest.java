package com.group7.backend.service.ranking;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link InterestOverlapFeedRanker}. Exercises the three
 * independent scoring axes (interest overlap, time decay, follow boost)
 * and their composition under the configured weights, asserting the new
 * integer [0, 100] score contract.
 */
class InterestOverlapFeedRankerTest {

    // Fixed weights: interest 0.5, time 0.3, follow 0.2; halflife 24h.
    private final InterestOverlapFeedRanker ranker = new InterestOverlapFeedRanker(
            0.5, 0.3, 0.2, Duration.ofHours(24));

    private final OffsetDateTime now = OffsetDateTime.parse("2026-05-08T12:00:00Z");

    private int scoreOf(FeedPost post, FeedRanker.FeedRankingContext ctx) {
        return ranker.score(post, ctx).score();
    }

    // ── Interest overlap ───────────────────────────────────────────────────

    @Test
    void noViewerInterests_interestComponentIsZero() {
        FeedPost post = postWithTags(List.of("data", "ai"), now.minusSeconds(0), 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        // 0 interest * 0.5 + 1.0 time * 0.3 + 0 follow * 0.2 = 0.3 → 30
        assertThat(scoreOf(post, ctx)).isEqualTo(30);
    }

    @Test
    void allTagsMatch_interestComponentIsOne() {
        FeedPost post = postWithTags(List.of("data", "ai"), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of("data", "ai", "ml"), Set.of(), now);

        // 1.0 * 0.5 + 1.0 * 0.3 + 0 * 0.2 = 0.8 → 80
        assertThat(scoreOf(post, ctx)).isEqualTo(80);
    }

    @Test
    void halfTagsMatch_interestComponentIsHalf() {
        FeedPost post = postWithTags(List.of("data", "stuff"), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of("data"), Set.of(), now);

        // 0.5 * 0.5 + 1.0 * 0.3 + 0 * 0.2 = 0.55 → 55
        assertThat(scoreOf(post, ctx)).isEqualTo(55);
    }

    @Test
    void postWithNoTags_interestComponentIsZero() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of("data"), Set.of(), now);

        // 0 + 0.3 + 0 = 0.3 → 30
        assertThat(scoreOf(post, ctx)).isEqualTo(30);
    }

    // ── Time decay ─────────────────────────────────────────────────────────

    @Test
    void postCreatedNow_timeDecayIsOne() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        // exp(-0 * ln(2) / halflife) = 1.0; total = 0 + 0.3 + 0 = 0.3 → 30
        assertThat(scoreOf(post, ctx)).isEqualTo(30);
    }

    @Test
    void postOneHalfLifeOld_timeDecayIsHalf() {
        FeedPost post = postWithTags(List.of(), now.minusHours(24), 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        // exp(-1 * ln(2)) = 0.5; total = 0 + 0.5 * 0.3 + 0 = 0.15 → 15
        assertThat(scoreOf(post, ctx)).isEqualTo(15);
    }

    @Test
    void postFromFuture_timeDecayClampsToOne() {
        // Defensive: clock skew might produce createdAt in the future.
        FeedPost post = postWithTags(List.of(), now.plusHours(1), 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        // The Math.max(0, deltaSeconds) guard means future timestamps
        // score the same as now (1.0 on the time axis) → 30.
        assertThat(scoreOf(post, ctx)).isEqualTo(30);
    }

    // ── Follow boost ───────────────────────────────────────────────────────

    @Test
    void authorIsFollowed_followBoostFires() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(99L), now);

        // 0 + 0.3 + 1.0 * 0.2 = 0.5 → 50
        assertThat(scoreOf(post, ctx)).isEqualTo(50);
    }

    @Test
    void authorNotFollowed_followBoostIsZero() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(2L, 3L), now);

        // 0 + 0.3 + 0 = 0.3 → 30
        assertThat(scoreOf(post, ctx)).isEqualTo(30);
    }

    @Test
    void emptyFollowGraph_followBoostIsZero() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        assertThat(scoreOf(post, ctx)).isEqualTo(30);
    }

    // ── Composition: relative ordering across realistic candidates ─────────

    @Test
    void rankingOrder_combinesAllThreeSignals() {
        // Three posts, same age, different tag overlap and follow status.
        FeedPost matchAndFollowed = postWithTags(List.of("data"), now, 50L);
        FeedPost matchOnly       = postWithTags(List.of("data"), now, 60L);
        FeedPost neither         = postWithTags(List.of("other"), now, 70L);

        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of("data"), Set.of(50L), now);

        int sMatchFollowed = scoreOf(matchAndFollowed, ctx);
        int sMatchOnly = scoreOf(matchOnly, ctx);
        int sNeither = scoreOf(neither, ctx);

        // Ordering: matchAndFollowed > matchOnly > neither.
        assertThat(sMatchFollowed).isGreaterThan(sMatchOnly);
        assertThat(sMatchOnly).isGreaterThan(sNeither);
    }

    // ── Result contract ────────────────────────────────────────────────────

    @Test
    void scoreResult_legacyRankerEmitsEmptyFactorList() {
        FeedPost post = postWithTags(List.of("data"), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of("data"), Set.of(99L), now);

        FeedScoreResult result = ranker.score(post, ctx);

        assertThat(result.factors()).isEmpty();
        assertThat(result.score()).isBetween(0, 100);
    }

    @Test
    void scoreResult_isClampedToZeroHundredEvenWithMisconfiguredWeights() {
        // Weights summing past 1.0 are mistuned but shouldn't blow past 100.
        InterestOverlapFeedRanker mistuned = new InterestOverlapFeedRanker(
                10.0, 10.0, 10.0, Duration.ofHours(24));
        FeedPost post = postWithTags(List.of("data"), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of("data"), Set.of(99L), now);

        assertThat(mistuned.score(post, ctx).score()).isEqualTo(100);
    }

    // ── Edge cases ─────────────────────────────────────────────────────────

    @Test
    void zeroHalfLife_treatedAsImmediateExpiry() {
        InterestOverlapFeedRanker zeroHalfLife = new InterestOverlapFeedRanker(
                0.5, 0.3, 0.2, Duration.ZERO);
        FeedPost now0 = postWithTags(List.of(), now, 99L);
        FeedPost old = postWithTags(List.of(), now.minusSeconds(1), 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        // halflife=0 → degenerate guard: score 1.0 only when delta == 0.
        assertThat(zeroHalfLife.score(now0, ctx).score()).isEqualTo(30);
        assertThat(zeroHalfLife.score(old, ctx).score()).isEqualTo(0);
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static FeedPost postWithTags(List<String> tags, OffsetDateTime createdAt, Long authorId) {
        FeedPost p = new FeedPost(authorId, "body");
        p.setId((long) Math.abs(tags.hashCode() + 1));
        p.setCreatedAt(createdAt);
        p.setUpdatedAt(createdAt);
        LinkedHashSet<FeedPostHashtag> hashtags = new LinkedHashSet<>();
        for (String t : tags) {
            hashtags.add(new FeedPostHashtag(p, t));
        }
        p.setHashtags(hashtags);
        return p;
    }
}
