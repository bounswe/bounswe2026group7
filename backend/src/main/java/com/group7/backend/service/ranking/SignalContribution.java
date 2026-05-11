package com.group7.backend.service.ranking;

import java.util.List;

/**
 * Result of a single {@link MentorScoringSignal#compute} call. The
 * {@code normalizedScore} is in {@code [0, 1]} and is multiplied by the
 * signal's {@code weight} by {@link AdvancedMentorRanker} during
 * aggregation; the {@code factors} list contains zero or more
 * machine-readable factor strings appended to the response for spec
 * 1.1.2.5 ("explanation for each recommendation").
 */
public record SignalContribution(double normalizedScore, List<String> factors) {

    public static final SignalContribution NONE = new SignalContribution(0.0, List.of());

    /** Defensive copy of {@code factors} so callers can pass a mutable list. */
    public SignalContribution {
        factors = (factors == null) ? List.of() : List.copyOf(factors);
    }
}
