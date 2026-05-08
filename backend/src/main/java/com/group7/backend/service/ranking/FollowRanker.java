package com.group7.backend.service.ranking;

import com.group7.backend.entity.User;

/**
 * Strategy interface for scoring a candidate user as a follow
 * recommendation (#344), paralleling {@link MentorRanker} on the
 * mentor-discovery side. Implementations score one candidate against a
 * pre-fetched {@link FollowRecommendationContext} and return a
 * {@link ScoreResult} carrying both the numeric score and any
 * explanation factors to surface in the response.
 *
 * <p><b>Contract:</b> implementations MUST be pure functions over the
 * supplied arguments — no repository calls, no I/O, no shared mutable
 * state between calls. The service guarantees that everything the
 * ranker needs (viewer interests, follow graph, second-hop counts) is
 * already populated in {@code ctx}; this keeps scoring O(candidates) in
 * memory rather than O(candidates) in DB round-trips.
 */
@FunctionalInterface
public interface FollowRanker {

    ScoreResult score(User candidate, FollowRecommendationContext ctx);
}
