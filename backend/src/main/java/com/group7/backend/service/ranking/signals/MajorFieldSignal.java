package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.MentorScoringSignal;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Softer major signal: 1.0 when the mentee's major matches the
 * mentor's general {@code field} (case-insensitive), 0 otherwise.
 * Emits {@code major-field-overlap}. See {@link MajorExactSignal}
 * for the stricter pairing.
 */
@Component
public class MajorFieldSignal implements MentorScoringSignal {

    private final MentorRecommendationProperties props;

    public MajorFieldSignal(MentorRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "major-field-overlap"; }

    @Override
    public boolean isEnabled() {
        return props.signals() != null && props.signals().majorEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights() == null ? 0.0 : props.weights().majorField();
    }

    @Override
    public SignalContribution compute(Mentor mentor, Mentee mentee, ScoringContext ctx) {
        String menteeMajor = mentee.getMajor();
        String field = mentor.getField();
        if (menteeMajor == null || field == null) return SignalContribution.NONE;
        if (menteeMajor.equalsIgnoreCase(field)) {
            return new SignalContribution(1.0, List.of("major-field-overlap"));
        }
        return SignalContribution.NONE;
    }
}
