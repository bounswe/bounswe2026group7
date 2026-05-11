package com.group7.backend.service.ranking;

import java.util.List;

/**
 * Result of ranking a single feed post. {@code score} is the bounded
 * integer used to sort the candidate set (higher ranks above lower).
 * {@code factors} is an ordered list of opaque short codes explaining
 * which signals contributed to the score; they flow through to the
 * list-response DTO so the UI can render explanation chips next to each
 * post.
 *
 * <p>Mirrors the mentor-side {@code ScoreResult} contract so both
 * surfaces follow the same shape. Factor codes from the For-You feed
 * are namespaced with a {@code feed:} prefix on the advanced ranker;
 * the legacy ranker emits an empty factor list (no explanations to
 * surface).
 */
public record FeedScoreResult(int score, List<String> factors) {

    public FeedScoreResult {
        factors = factors == null ? List.of() : List.copyOf(factors);
    }
}
