package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.MentorScoringSignal;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/**
 * Sums the per-day-of-week overlap between the mentor's and mentee's
 * recurring availability slots, then normalises to {@code [0, 1]} by
 * dividing by 720 minutes (12 hours saturation point). Emits the
 * {@code availability:Xh} factor for any positive overlap.
 *
 * <p>Behaviour matches the legacy ranker (touching but non-overlapping
 * slots count as 0; mentor-MONDAY vs mentee-TUESDAY are skipped) so
 * the regression suite for the rule-based ranker still holds.
 */
@Component
public class AvailabilitySignal implements MentorScoringSignal {

    private static final long SATURATION_MINUTES = 720;

    private final MentorRecommendationProperties props;

    public AvailabilitySignal(MentorRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "availability"; }

    @Override
    public boolean isEnabled() {
        return props.signals() != null && props.signals().availabilityEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights() == null ? 0.0 : props.weights().availability();
    }

    @Override
    public SignalContribution compute(Mentor mentor, Mentee mentee, ScoringContext ctx) {
        List<AvailabilitySlot> mentorSlots = ctx.mentorSlots();
        List<MenteeAvailabilitySlot> menteeSlots = ctx.menteeSlots();
        if (mentorSlots.isEmpty() || menteeSlots.isEmpty()) {
            return SignalContribution.NONE;
        }

        long overlapMinutes = 0;
        for (AvailabilitySlot ms : mentorSlots) {
            for (MenteeAvailabilitySlot es : menteeSlots) {
                if (ms.getDayOfWeek() != es.getDayOfWeek()) continue;
                overlapMinutes += overlapMinutes(ms.getStartTime(), ms.getEndTime(),
                                                 es.getStartTime(), es.getEndTime());
            }
        }
        if (overlapMinutes <= 0) {
            return SignalContribution.NONE;
        }

        double normalized = Math.min(1.0, (double) overlapMinutes / SATURATION_MINUTES);
        long hours = overlapMinutes / 60;
        return new SignalContribution(normalized, List.of("availability:" + hours + "h"));
    }

    private static long overlapMinutes(LocalTime aStart, LocalTime aEnd,
                                       LocalTime bStart, LocalTime bEnd) {
        LocalTime start = aStart.isAfter(bStart) ? aStart : bStart;
        LocalTime end = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        if (!start.isBefore(end)) return 0;
        return Duration.between(start, end).toMinutes();
    }
}
