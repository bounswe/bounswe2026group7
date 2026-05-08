package com.group7.backend.service.ranking;

import java.util.Map;
import java.util.Set;

/**
 * Pre-fetched signals consumed by a {@link FollowRanker} when scoring
 * candidates for the follow-recommendation pipeline (#344). The service
 * builds this once per request and passes it to the ranker for every
 * candidate; the ranker MUST NOT make repository calls.
 *
 * <ul>
 *   <li>{@code viewerId} — the requester, never returned as a candidate.</li>
 *   <li>{@code viewerInterestLabels} — lowercased set of the viewer's
 *       interest labels (from {@code Mentor.getInterests()} or
 *       {@code Mentee.getInterests()}); empty for admins or for users
 *       with no interests on file.</li>
 *   <li>{@code viewerFolloweeIds} — the set of users the viewer already
 *       follows, capped at 200 by the service to bound the second-hop
 *       query.</li>
 *   <li>{@code secondHopCount} — for each candidate id, the number of
 *       viewer's followees that follow them (the "followed by N of your
 *       follows" signal). Already excludes the viewer's own followees;
 *       a candidate id absent from the map gets a zero contribution.</li>
 * </ul>
 */
public record FollowRecommendationContext(
        Long viewerId,
        Set<String> viewerInterestLabels,
        Set<Long> viewerFolloweeIds,
        Map<Long, Integer> secondHopCount) {
}
