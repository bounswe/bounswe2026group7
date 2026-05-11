package com.group7.backend.service.ranking.feed;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Per-request data threaded through every {@link FeedScoringSignal} so
 * signals don't reach back to repositories at scoring time. Mirrors the
 * mentor-side {@code ScoringContext} pattern: all aggregate lookups
 * (engagement counts, author-affinity counts, viewer/post embeddings)
 * are pre-computed once in the pipeline and shared across the whole
 * candidate window.
 *
 * @param viewerId                       authenticated viewer
 * @param viewerInterestHashtags         normalized hashtag set from
 *                                       viewer interests; used as the
 *                                       text source for the viewer's
 *                                       interest embedding and as a
 *                                       fallback Jaccard target when the
 *                                       embedder is unavailable.
 * @param viewerFollowedAuthorIds        author IDs the viewer follows
 * @param now                            stable "now" timestamp used by
 *                                       time-decay so all candidates in a
 *                                       request decay against the same
 *                                       reference point.
 * @param postEngagementWeightedCounts   per-post weighted engagement
 *                                       count
 *                                       ({@code 1·likes + 2·comments +
 *                                       3·shares + 4·bookmarks}).
 * @param engagementWindowMaxLog         {@code log(1 + max weighted count
 *                                       across the candidate window)}.
 *                                       Used as the per-window
 *                                       normalization denominator. May be
 *                                       0 (no engagement in window) — the
 *                                       signal must guard against the
 *                                       resulting divide-by-zero.
 * @param authorAffinityWeightedCounts30d per-author weighted engagement
 *                                       count from this viewer toward
 *                                       that author in the last 30 days
 *                                       (window size is configurable).
 * @param viewerInterestEmbedding        cached embedding of the viewer's
 *                                       interest text; empty array if the
 *                                       embedder is unavailable.
 * @param postEmbeddings                 cached embedding per candidate
 *                                       post id; empty array on miss.
 */
public record FeedScoringContext(
        Long viewerId,
        Set<String> viewerInterestHashtags,
        Set<Long> viewerFollowedAuthorIds,
        OffsetDateTime now,
        Map<Long, Integer> postEngagementWeightedCounts,
        double engagementWindowMaxLog,
        Map<Long, Integer> authorAffinityWeightedCounts30d,
        float[] viewerInterestEmbedding,
        Map<Long, float[]> postEmbeddings
) {

    public FeedScoringContext {
        viewerInterestHashtags = (viewerInterestHashtags == null)
                ? Set.of() : Set.copyOf(viewerInterestHashtags);
        viewerFollowedAuthorIds = (viewerFollowedAuthorIds == null)
                ? Set.of() : Set.copyOf(viewerFollowedAuthorIds);
        postEngagementWeightedCounts = (postEngagementWeightedCounts == null)
                ? Map.of() : Collections.unmodifiableMap(postEngagementWeightedCounts);
        authorAffinityWeightedCounts30d = (authorAffinityWeightedCounts30d == null)
                ? Map.of() : Collections.unmodifiableMap(authorAffinityWeightedCounts30d);
        viewerInterestEmbedding = (viewerInterestEmbedding == null)
                ? new float[0] : viewerInterestEmbedding;
        postEmbeddings = (postEmbeddings == null)
                ? Map.of() : Collections.unmodifiableMap(postEmbeddings);
    }
}
