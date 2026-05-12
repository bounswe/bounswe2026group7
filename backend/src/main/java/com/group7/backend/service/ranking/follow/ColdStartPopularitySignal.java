package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.FollowScoringSignal;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cold-start fallback signal — fires ONLY when the viewer has zero
 * followees, so the established signals (second-hop, PPR, direct-
 * interaction) all have nothing to contribute and the recommendation
 * would otherwise be deterministic-by-id noise.
 *
 * <p>Hybrid 60/40 blend that mirrors Netflix / LinkedIn cold-start
 * patterns:
 * <ul>
 *   <li><b>60%</b> — log-scaled popularity within the viewer's major.
 *       Picks up "people you should know about" without trapping the
 *       viewer in a pure-celebrity feed.</li>
 *   <li><b>40%</b> — Jaccard interest-label overlap with the viewer.
 *       Keeps the recommendation tied to what the viewer actually said
 *       they care about.</li>
 * </ul>
 *
 * <p>If the candidate isn't in the popularity-by-major map, the
 * popularity half contributes 0 but the interest half still counts —
 * the signal degrades gracefully toward "shared interests only" for
 * cross-major candidates.
 *
 * <p>Emits up to two factors: {@code popular-in-major} when popularity
 * contributed and {@code shared-interest:<Label>} when interest overlap
 * did. Caller (the aggregator) concatenates these into the final
 * factor list so the UI's "why are you seeing this?" card has
 * actionable text.
 */
@Component
public class ColdStartPopularitySignal implements FollowScoringSignal {

    private static final int MAX_INTEREST_FACTORS = 2;

    private final FollowRecommendationProperties props;

    public ColdStartPopularitySignal(FollowRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "cold-start-popularity"; }
    @Override public boolean isEnabled() { return props.signals().coldStartPopularityEnabled(); }
    @Override public double getWeight() { return props.weights().coldStartPopularity(); }

    @Override
    public SignalContribution compute(User candidate, FollowRecommendationContext ctx) {
        if (!ctx.coldStart()) {
            return SignalContribution.NONE;
        }

        FollowRecommendationProperties.ColdStart cfg = props.coldStart();

        // ── popularity half ────────────────────────────────────────────
        double popularityScore = 0.0;
        boolean popularityContributed = false;
        Map<Long, Long> popularityMap = ctx.popularityByMajor();
        if (popularityMap != null && !popularityMap.isEmpty()) {
            Long count = popularityMap.get(candidate.getId());
            if (count != null && count > 0) {
                long max = popularityMap.values().stream()
                        .mapToLong(Long::longValue)
                        .max()
                        .orElse(0L);
                if (max > 0) {
                    popularityScore =
                            Math.log1p(count) / Math.log1p(max);
                    popularityContributed = true;
                }
            }
        }

        // ── interest-overlap half ──────────────────────────────────────
        Collection<String> candInterests = candidateInterestLabels(candidate);
        Set<String> viewerInterests = ctx.viewerInterestLabels() == null
                ? Set.of()
                : ctx.viewerInterestLabels();
        List<String> overlappingLabels = new ArrayList<>();
        double interestScore = 0.0;
        if (!viewerInterests.isEmpty() && !candInterests.isEmpty()) {
            Set<String> normalisedCand = new LinkedHashSet<>();
            for (String label : candInterests) {
                String lower = label == null ? null : label.toLowerCase(Locale.ROOT);
                if (lower != null && viewerInterests.contains(lower)) {
                    overlappingLabels.add(label);
                }
                if (lower != null) normalisedCand.add(lower);
            }
            int unionSize = unionSize(viewerInterests, normalisedCand);
            int interSize = overlappingLabels.size();
            if (unionSize > 0) {
                interestScore = (double) interSize / unionSize;
            }
        }

        double blended = cfg.popularityMix() * popularityScore
                       + cfg.interestMix()   * interestScore;
        blended = Math.max(0.0, Math.min(1.0, blended));
        if (blended <= 0.0) {
            return SignalContribution.NONE;
        }

        List<String> factors = new ArrayList<>();
        if (popularityContributed) {
            factors.add("popular-in-major");
        }
        int displayed = 0;
        for (String label : overlappingLabels) {
            if (displayed++ >= MAX_INTEREST_FACTORS) break;
            factors.add("shared-interest:" + label);
        }
        return new SignalContribution(blended, List.copyOf(factors));
    }

    private static int unionSize(Set<String> a, Set<String> b) {
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.size();
    }

    /**
     * Mirrors {@code InterestOverlapFollowSignal#labelsOf} — interest
     * labels are stored as plain {@code List<String>} on both Mentor and
     * Mentee. Admins return an empty list.
     */
    private static Collection<String> candidateInterestLabels(User u) {
        if (u instanceof Mentor mentor) {
            return mentor.getInterests() == null ? List.of() : mentor.getInterests();
        }
        if (u instanceof Mentee mentee) {
            return mentee.getInterests() == null ? List.of() : mentee.getInterests();
        }
        return List.of();
    }
}
