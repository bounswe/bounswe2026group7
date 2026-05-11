package com.group7.backend.service.ranking;

import com.group7.backend.entity.FeedPost;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Strategy for scoring a feed post against a viewer. The legacy impl
 * ({@code InterestOverlapFeedRanker}) is a weighted-sum of interest
 * overlap, time-decay, and follow-graph proximity; an AI-driven advanced
 * ranker plugs in here behind {@code @Primary} without service-layer
 * changes.
 *
 * <p><b>Contract:</b> implementations MUST NOT make repository or
 * external calls. The {@link FeedRankingContext} carries everything the
 * ranker needs (viewer state, follow graph, viewer interests, plus any
 * pre-computed maps for engagement and author affinity). Stateless pure
 * functions sort and slice cleanly under concurrency; reaching back to
 * repos at scoring time would surface in the For-You critical path as
 * N+1 — exactly the trap that {@code MentorRanker} was designed to
 * avoid.
 */
public interface FeedRanker {

    /**
     * Compute a bounded score for {@code post} from {@code viewer}'s
     * perspective along with any factor codes explaining the result.
     * Higher {@code score} ranks above lower; callers sort but do not
     * interpret the magnitude beyond ordering.
     */
    FeedScoreResult score(FeedPost post, FeedRankingContext context);

    /**
     * Bag of inputs the ranker uses, computed once per request and shared
     * across the whole candidate set. Pre-computed at the service layer
     * so the ranker stays a pure function.
     */
    record FeedRankingContext(
            Long viewerId,
            Set<String> viewerInterestHashtags,
            Set<Long> viewerFollowedAuthorIds,
            OffsetDateTime now
    ) {
    }
}
