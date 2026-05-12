package com.group7.backend.service.ranking;

import java.util.List;

/**
 * Per-signal output from a {@link FollowScoringSignal#compute}.
 *
 * <p>{@code normalizedScore} is expected to lie in {@code [0,1]}; the
 * aggregator ({@code AdvancedFollowRanker}) clamps defensively so a
 * malformed signal cannot blow up the final score. {@code factors} is a
 * short, human-readable list of strings that flow through to the API
 * response so the UI can render "why are you seeing this?" cards.
 *
 * <p>Convention: factor strings use the signal's {@code code()} as the
 * prefix (kebab-case), e.g. {@code "shared-interest:Java"},
 * {@code "network-proximity"}, {@code "active-this-week"}.
 */
public record SignalContribution(double normalizedScore, List<String> factors) {

    /** Zero-contribution placeholder — useful when a signal has nothing to add. */
    public static final SignalContribution NONE = new SignalContribution(0.0, List.of());

    /** Convenience: build a contribution from a score and a varargs of factors. */
    public static SignalContribution of(double score, String... factors) {
        return new SignalContribution(score, List.of(factors));
    }
}
