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
 * Binary signal: 1.0 when the mentee's major matches the mentor's
 * {@code preferredMenteeMajor} (case-insensitive), 0 otherwise. Emits
 * {@code major-exact-match}.
 *
 * <p>Pairs with {@link MajorFieldSignal} for the softer "major matches
 * the mentor's field" fallback — kept as two signals so the weights are
 * independently tunable while sharing the {@code signals.major.enabled}
 * kill switch.
 */
@Component
public class MajorExactSignal implements MentorScoringSignal {

    private final MentorRecommendationProperties props;

    public MajorExactSignal(MentorRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "major-exact-match"; }

    @Override
    public boolean isEnabled() {
        return props.signals() != null && props.signals().majorEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights() == null ? 0.0 : props.weights().majorExact();
    }

    @Override
    public SignalContribution compute(Mentor mentor, Mentee mentee, ScoringContext ctx) {
        String menteeMajor = mentee.getMajor();
        String preferred = mentor.getPreferredMenteeMajor();
        if (menteeMajor == null || preferred == null) return SignalContribution.NONE;
        if (menteeMajor.equalsIgnoreCase(preferred)) {
            return new SignalContribution(1.0, List.of("major-exact-match"));
        }
        return SignalContribution.NONE;
    }
}
