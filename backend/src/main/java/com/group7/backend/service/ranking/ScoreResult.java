package com.group7.backend.service.ranking;

import java.util.List;

/**
 * Score plus human-readable explanation factors emitted by a ranker. The
 * follow-recommendation pipeline (#344) attaches these factors to each
 * candidate so the UI can surface "why are you seeing this" cards
 * (mirroring the explanation pattern in spec 1.1.2.4).
 *
 * <p>Co-located with {@code FollowRanker} rather than nested inside it:
 * top-level types are easier to discover and don't conflict with Spring's
 * proxy mechanics on functional interfaces. The mentor-side
 * {@code MentorRanker} returns a plain {@code int} because mentor
 * matching has no factor surface yet — when it gains one, this record is
 * the natural shape to converge on.
 *
 * <p>{@code factors} is intended to be small (the ranker may cap its
 * own output for display) and is treated as immutable by callers; the
 * record wrapper does not defensively copy.
 */
public record ScoreResult(int score, List<String> factors) {

    public static ScoreResult of(int score) {
        return new ScoreResult(score, List.of());
    }
}
