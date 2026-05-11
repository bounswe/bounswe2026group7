package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.MentorScoringSignal;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Jaccard similarity between mentor and mentee interest sets. Emits one
 * {@code shared-interest:<name>} factor per overlap (capped at 3 to
 * keep the card readable) using the mentee's original casing for the
 * label — case-insensitive matching avoids "AI" vs "ai" misses while
 * preserving the display form the user typed.
 */
@Component
public class SharedInterestsSignal implements MentorScoringSignal {

    private static final int FACTOR_CAP = 3;

    private final MentorRecommendationProperties props;

    public SharedInterestsSignal(MentorRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "shared-interests"; }

    @Override
    public boolean isEnabled() {
        return props.signals() != null && props.signals().sharedInterestsEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights() == null ? 0.0 : props.weights().sharedInterests();
    }

    @Override
    public SignalContribution compute(Mentor mentor, Mentee mentee, ScoringContext ctx) {
        List<String> mentorInterests = nullSafe(mentor.getInterests());
        List<String> menteeInterests = nullSafe(mentee.getInterests());
        if (mentorInterests.isEmpty() || menteeInterests.isEmpty()) {
            return SignalContribution.NONE;
        }

        Set<String> mentorLower = lowercase(mentorInterests);
        Set<String> menteeLower = lowercase(menteeInterests);

        Set<String> intersection = new HashSet<>(mentorLower);
        intersection.retainAll(menteeLower);
        if (intersection.isEmpty()) {
            return SignalContribution.NONE;
        }

        Set<String> union = new HashSet<>(mentorLower);
        union.addAll(menteeLower);
        double jaccard = (double) intersection.size() / union.size();

        // Emit factors preserving mentee's original casing, capped at 3.
        List<String> factors = new ArrayList<>();
        for (String label : menteeInterests) {
            if (label == null) continue;
            if (intersection.contains(label.toLowerCase(Locale.ROOT))) {
                factors.add("shared-interest:" + label);
                if (factors.size() >= FACTOR_CAP) break;
            }
        }
        return new SignalContribution(jaccard, factors);
    }

    private static List<String> nullSafe(List<String> xs) {
        return xs == null ? List.of() : xs;
    }

    private static Set<String> lowercase(List<String> xs) {
        Set<String> s = new HashSet<>();
        for (String x : xs) if (x != null) s.add(x.toLowerCase(Locale.ROOT));
        return s;
    }
}
