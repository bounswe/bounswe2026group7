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
 * Unit coverage for {@link InterestOverlapFeedRanker} (#350). Exercises
 * the three independent scoring axes (interest overlap, time decay,
 * follow boost) and their composition under the configured weights.
 */
class InterestOverlapFeedRankerTest {

    // Fixed weights: interest 0.5, time 0.3, follow 0.2; halflife 24h.
    private final InterestOverlapFeedRanker ranker = new InterestOverlapFeedRanker(
            0.5, 0.3, 0.2, Duration.ofHours(24));

    private final OffsetDateTime now = OffsetDateTime.parse("2026-05-08T12:00:00Z");

    // ── Interest overlap ───────────────────────────────────────────────────

    @Test
    void noViewerInterests_interestComponentIsZero() {
        FeedPost post = postWithTags(List.of("data", "ai"), now.minusSeconds(0), 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        // 0 interest * 0.5 + 1.0 time * 0.3 + 0 follow * 0.2 = 0.3
        assertThat(ranker.score(post, ctx)).isCloseTo(0.3, withinTolerance());
    }

    @Test
    void allTagsMatch_interestComponentIsOne() {
        FeedPost post = postWithTags(List.of("data", "ai"), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of("data", "ai", "ml"), Set.of(), now);

        // 1.0 interest * 0.5 + 1.0 time * 0.3 + 0 follow * 0.2 = 0.8
        assertThat(ranker.score(post, ctx)).isCloseTo(0.8, withinTolerance());
    }

    @Test
    void halfTagsMatch_interestComponentIsHalf() {
        FeedPost post = postWithTags(List.of("data", "stuff"), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of("data"), Set.of(), now);

        // 0.5 interest * 0.5 + 1.0 time * 0.3 + 0 follow * 0.2 = 0.55
        assertThat(ranker.score(post, ctx)).isCloseTo(0.55, withinTolerance());
    }

    @Test
    void postWithNoTags_interestComponentIsZero() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of("data"), Set.of(), now);

        // 0 interest * 0.5 + 1.0 time * 0.3 + 0 follow * 0.2 = 0.3
        assertThat(ranker.score(post, ctx)).isCloseTo(0.3, withinTolerance());
    }

    // ── Time decay ─────────────────────────────────────────────────────────

    @Test
    void postCreatedNow_timeDecayIsOne() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        // exp(-0 * ln(2) / halflife) = 1.0; total = 0.0 + 0.3 + 0.0 = 0.3
        assertThat(ranker.score(post, ctx)).isCloseTo(0.3, withinTolerance());
    }

    @Test
    void postOneHalfLifeOld_timeDecayIsHalf() {
        FeedPost post = postWithTags(List.of(), now.minusHours(24), 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        // exp(-1 * ln(2)) = 0.5; total = 0 + 0.5 * 0.3 + 0 = 0.15
        assertThat(ranker.score(post, ctx)).isCloseTo(0.15, withinTolerance());
    }

    @Test
    void postFromFuture_timeDecayClampsToOne() {
        // Defensive: clock skew might produce createdAt in the future.
        FeedPost post = postWithTags(List.of(), now.plusHours(1), 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        // The Math.max(0, deltaSeconds) guard means future timestamps
        // score the same as now (1.0 on the time axis).
        assertThat(ranker.score(post, ctx)).isCloseTo(0.3, withinTolerance());
    }

    // ── Follow boost ───────────────────────────────────────────────────────

    @Test
    void authorIsFollowed_followBoostFires() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(99L), now);

        // 0 + 0.3 + 1.0 * 0.2 = 0.5
        assertThat(ranker.score(post, ctx)).isCloseTo(0.5, withinTolerance());
    }

    @Test
    void authorNotFollowed_followBoostIsZero() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(2L, 3L), now);

        // 0 + 0.3 + 0 = 0.3
        assertThat(ranker.score(post, ctx)).isCloseTo(0.3, withinTolerance());
    }

    @Test
    void emptyFollowGraph_followBoostIsZero() {
        FeedPost post = postWithTags(List.of(), now, 99L);
        FeedRanker.FeedRankingContext ctx = new FeedRanker.FeedRankingContext(
                1L, Set.of(), Set.of(), now);

        assertThat(ranker.score(post, ctx)).isCloseTo(0.3, withinTolerance());
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

        double sMatchFollowed = ranker.score(matchAndFollowed, ctx);
        double sMatchOnly = ranker.score(matchOnly, ctx);
        double sNeither = ranker.score(neither, ctx);

        // Ordering: matchAndFollowed > matchOnly > neither.
        assertThat(sMatchFollowed).isGreaterThan(sMatchOnly);
        assertThat(sMatchOnly).isGreaterThan(sNeither);
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
        assertThat(zeroHalfLife.score(now0, ctx)).isCloseTo(0.3, withinTolerance());
        assertThat(zeroHalfLife.score(old, ctx)).isCloseTo(0.0, withinTolerance());
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

    private static org.assertj.core.data.Offset<Double> withinTolerance() {
        return org.assertj.core.data.Offset.offset(0.001);
    }
}
