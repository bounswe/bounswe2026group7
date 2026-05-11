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
 * Jaccard similarity between the mentor's {@code preferredMenteeSkills}
 * and the mentee's {@code skills}. Same pattern as
 * {@link SharedInterestsSignal} — emits up to 3 {@code shared-skill:<name>}
 * factors preserving the mentee's casing.
 */
@Component
public class SharedSkillsSignal implements MentorScoringSignal {

    private static final int FACTOR_CAP = 3;

    private final MentorRecommendationProperties props;

    public SharedSkillsSignal(MentorRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "shared-skills"; }

    @Override
    public boolean isEnabled() {
        return props.signals() != null && props.signals().sharedSkillsEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights() == null ? 0.0 : props.weights().sharedSkills();
    }

    @Override
    public SignalContribution compute(Mentor mentor, Mentee mentee, ScoringContext ctx) {
        List<String> preferred = nullSafe(mentor.getPreferredMenteeSkills());
        List<String> menteeSkills = nullSafe(mentee.getSkills());
        if (preferred.isEmpty() || menteeSkills.isEmpty()) {
            return SignalContribution.NONE;
        }

        Set<String> preferredLower = lowercase(preferred);
        Set<String> menteeLower = lowercase(menteeSkills);

        Set<String> intersection = new HashSet<>(preferredLower);
        intersection.retainAll(menteeLower);
        if (intersection.isEmpty()) {
            return SignalContribution.NONE;
        }

        Set<String> union = new HashSet<>(preferredLower);
        union.addAll(menteeLower);
        double jaccard = (double) intersection.size() / union.size();

        List<String> factors = new ArrayList<>();
        for (String label : menteeSkills) {
            if (label == null) continue;
            if (intersection.contains(label.toLowerCase(Locale.ROOT))) {
                factors.add("shared-skill:" + label);
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
