package com.group7.backend.service.ranking;

import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-signal weighted-sum mentor ranker for spec 1.1.2.1 / 1.1.2.3 /
 * 1.1.2.5. Loaded as {@code @Primary} when
 * {@code app.recommendations.mentor.advanced.enabled=true}; otherwise
 * Spring resolves the unique {@link RuleBasedMentorRanker} bean and
 * legacy scoring continues unchanged.
 *
 * <p>Aggregation:
 * <pre>
 *   total = Σ_{signals s, enabled} weight(s) · normalizedScore(s, mentor, mentee)
 *   matchScore = round(clamp(total, 0, 1) · 100)
 *   factors = ⋃_{signals s} s.factors()
 * </pre>
 *
 * <p>Open-closed: new signals are new {@code @Component} implementations
 * of {@link MentorScoringSignal} — no edits here.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.recommendations.mentor.advanced.enabled", havingValue = "true")
public class AdvancedMentorRanker implements MentorRanker {

    private final List<MentorScoringSignal> signals;

    public AdvancedMentorRanker(List<MentorScoringSignal> signals) {
        this.signals = List.copyOf(signals);
    }

    @Override
    public ScoreResult score(Mentor mentor,
                             Mentee mentee,
                             List<AvailabilitySlot> mentorSlots,
                             List<MenteeAvailabilitySlot> menteeSlots) {
        ScoringContext ctx = new ScoringContext(mentorSlots, menteeSlots);

        double weightedSum = 0.0;
        List<String> allFactors = new ArrayList<>();

        for (MentorScoringSignal signal : signals) {
            // isEnabled() is the operator's hard kill switch (no factors,
            // no score). weight=0 is softer — "don't score this signal" —
            // but informational factors like `location-unset` and
            // `semantic-unavailable` MUST still flow through so the UI can
            // explain *why* a contribution is missing. So we always call
            // compute() when isEnabled() is true; only the weighted score
            // contribution is gated on weight > 0.
            if (!signal.isEnabled()) continue;

            SignalContribution contribution = signal.compute(mentor, mentee, ctx);
            allFactors.addAll(contribution.factors());

            double weight = signal.getWeight();
            if (weight > 0.0) {
                double normalized = Math.max(0.0, Math.min(1.0, contribution.normalizedScore()));
                weightedSum += weight * normalized;
            }
        }

        // Two clamps: each signal's contribution is already clamped to
        // [0, 1] above so the score contribution is well-behaved; the
        // outer clamp protects against operator mis-config (weights summing
        // to > 1 in application.properties). Defensive, not redundant.
        double clamped = Math.max(0.0, Math.min(1.0, weightedSum));
        int score = (int) Math.round(clamped * 100.0);
        return new ScoreResult(score, List.copyOf(allFactors));
    }
}
