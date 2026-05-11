package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class AvailabilitySignalTest {

    private static MentorRecommendationProperties props(boolean enabled, double weight) {
        return new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, 0, 0, 0, 0, weight, 0),
                new MentorRecommendationProperties.Signals(false, false, false, false, enabled, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                new MentorRecommendationProperties.Mmr(false, 0.7),
                null);
    }

    @Test
    void killSwitchOff() {
        assertThat(new AvailabilitySignal(props(false, 0.15)).isEnabled()).isFalse();
    }

    @Test
    void nullSignalsRecord_disabledAndZeroWeight() {
        var signal = new AvailabilitySignal(new MentorRecommendationProperties(null, null, null, null, null, null));
        assertThat(signal.isEnabled()).isFalse();
        assertThat(signal.getWeight()).isZero();
    }

    @Test
    void emptySlotsEitherSide_returnsNone() {
        var signal = new AvailabilitySignal(props(true, 0.15));
        var ctx1 = new ScoringContext(List.of(), List.of(menteeSlot(DayOfWeek.MONDAY, 9, 10)));
        var ctx2 = new ScoringContext(List.of(mentorSlot(DayOfWeek.MONDAY, 9, 10)), List.of());
        assertThat(signal.compute(new Mentor(), new Mentee(), ctx1)).isEqualTo(SignalContribution.NONE);
        assertThat(signal.compute(new Mentor(), new Mentee(), ctx2)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void differentDayOfWeek_noOverlap_returnsNone() {
        var signal = new AvailabilitySignal(props(true, 0.15));
        var ctx = new ScoringContext(
                List.of(mentorSlot(DayOfWeek.MONDAY, 9, 17)),
                List.of(menteeSlot(DayOfWeek.TUESDAY, 9, 17)));
        assertThat(signal.compute(new Mentor(), new Mentee(), ctx)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void adjacentNonOverlapping_returnsNone() {
        var signal = new AvailabilitySignal(props(true, 0.15));
        var ctx = new ScoringContext(
                List.of(mentorSlot(DayOfWeek.MONDAY, 9, 10)),
                List.of(menteeSlot(DayOfWeek.MONDAY, 10, 11)));
        assertThat(signal.compute(new Mentor(), new Mentee(), ctx)).isEqualTo(SignalContribution.NONE);
    }

    @Test
    void oneHourOverlap_normalisedAndFactorEmitted() {
        var signal = new AvailabilitySignal(props(true, 0.15));
        var ctx = new ScoringContext(
                List.of(mentorSlot(DayOfWeek.MONDAY, 9, 11)),
                List.of(menteeSlot(DayOfWeek.MONDAY, 10, 11)));
        var out = signal.compute(new Mentor(), new Mentee(), ctx);
        assertThat(out.normalizedScore()).isCloseTo(60.0 / 720.0, offset(1e-9));
        assertThat(out.factors()).containsExactly("availability:1h");
    }

    @Test
    void twelveHoursPlus_saturatesAtOneWithFactor() {
        var signal = new AvailabilitySignal(props(true, 0.15));
        var ctx = new ScoringContext(
                List.of(mentorSlot(DayOfWeek.MONDAY, 0, 23)),
                List.of(menteeSlot(DayOfWeek.MONDAY, 0, 23)));
        var out = signal.compute(new Mentor(), new Mentee(), ctx);
        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("availability:23h");
    }

    @Test
    void code() {
        assertThat(new AvailabilitySignal(props(true, 0.15)).code()).isEqualTo("availability");
    }

    private static AvailabilitySlot mentorSlot(DayOfWeek day, int startHour, int endHour) {
        var slot = new AvailabilitySlot();
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.of(startHour, 0));
        slot.setEndTime(LocalTime.of(endHour, 0));
        slot.setRecurring(true);
        return slot;
    }

    private static MenteeAvailabilitySlot menteeSlot(DayOfWeek day, int startHour, int endHour) {
        var slot = new MenteeAvailabilitySlot();
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.of(startHour, 0));
        slot.setEndTime(LocalTime.of(endHour, 0));
        slot.setRecurring(true);
        return slot;
    }
}
