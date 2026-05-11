package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class LocationProximitySignalTest {

    private static final ScoringContext EMPTY_CTX = new ScoringContext(List.of(), List.of());

    private static MentorRecommendationProperties props(boolean enabled, int decayKm, double cityBonus) {
        return new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, 0, 0, 0, 0, 0, 0.2),
                new MentorRecommendationProperties.Signals(false, false, false, false, false, enabled),
                new MentorRecommendationProperties.Proximity(decayKm, cityBonus),
                new MentorRecommendationProperties.Mmr(false, 0.7),
                null);
    }

    @Test
    void killSwitchOff() {
        assertThat(new LocationProximitySignal(props(false, 100, 0.2)).isEnabled()).isFalse();
    }

    @Test
    void nullSignalsRecord_disabledAndZeroWeight() {
        var signal = new LocationProximitySignal(new MentorRecommendationProperties(null, null, null, null, null, null));
        assertThat(signal.isEnabled()).isFalse();
        assertThat(signal.getWeight()).isZero();
    }

    @Test
    void bothLocationsAbsent_emitsLocationUnset() {
        var signal = new LocationProximitySignal(props(true, 100, 0.2));
        var out = signal.compute(new Mentor(), new Mentee(), EMPTY_CTX);
        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).containsExactly("location-unset");
    }

    @Test
    void onlyOneSideHasCoordinates_emitsLocationUnsetWhenNoCity() {
        var signal = new LocationProximitySignal(props(true, 100, 0.2));
        var mentor = new Mentor();
        mentor.setLatitude(41.0); mentor.setLongitude(29.0);
        var mentee = new Mentee();
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.factors()).containsExactly("location-unset");
    }

    @Test
    void cityMatchWithoutCoordinates_scoresBonusAndEmitsCityMatch() {
        var signal = new LocationProximitySignal(props(true, 100, 0.25));
        var mentor = new Mentor(); mentor.setCity("Istanbul");
        var mentee = new Mentee(); mentee.setCity("istanbul");
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.normalizedScore()).isCloseTo(0.25, offset(1e-9));
        assertThat(out.factors()).containsExactly("city-match");
    }

    @Test
    void cityFallback_capsAtOne() {
        var signal = new LocationProximitySignal(props(true, 100, 5.0));
        var mentor = new Mentor(); mentor.setCity("Ankara");
        var mentee = new Mentee(); mentee.setCity("Ankara");
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.normalizedScore()).isEqualTo(1.0); // clamped from 5.0
    }

    @Test
    void differentCitiesWithoutCoordinates_locationUnset() {
        var signal = new LocationProximitySignal(props(true, 100, 0.2));
        var mentor = new Mentor(); mentor.setCity("Istanbul");
        var mentee = new Mentee(); mentee.setCity("Ankara");
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.factors()).containsExactly("location-unset");
    }

    @Test
    void coordinates_nearby_scoreNearOneAndKmFactor() {
        var signal = new LocationProximitySignal(props(true, 100, 0.0));
        var mentor = new Mentor();
        mentor.setLatitude(41.0082); mentor.setLongitude(28.9784); // Istanbul
        var mentee = new Mentee();
        mentee.setLatitude(41.0083); mentee.setLongitude(28.9785); // ~14 m away
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.normalizedScore()).isCloseTo(1.0, offset(0.001));
        assertThat(out.factors()).containsExactly("nearby:0km");
    }

    @Test
    void coordinates_far_scoreNearZero() {
        var signal = new LocationProximitySignal(props(true, 100, 0.0));
        var mentor = new Mentor();
        mentor.setLatitude(41.0082); mentor.setLongitude(28.9784);   // Istanbul
        var mentee = new Mentee();
        mentee.setLatitude(40.7128); mentee.setLongitude(-74.0060);  // New York
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.normalizedScore()).isLessThan(0.001);
        assertThat(out.factors().get(0)).startsWith("nearby:");
    }

    @Test
    void coordinatesAndSameCity_emitsBothFactorsAndAddsBonus() {
        var signal = new LocationProximitySignal(props(true, 100, 0.1));
        var mentor = new Mentor();
        mentor.setLatitude(41.0); mentor.setLongitude(29.0); mentor.setCity("Istanbul");
        var mentee = new Mentee();
        mentee.setLatitude(41.001); mentee.setLongitude(29.001); mentee.setCity("Istanbul");
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.factors()).containsExactly("nearby:0km", "city-match");
        assertThat(out.normalizedScore()).isGreaterThan(0.9);   // base ≈ 1.0, capped at 1.0
        assertThat(out.normalizedScore()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void blankCityStrings_treatedAsAbsent() {
        var signal = new LocationProximitySignal(props(true, 100, 0.2));
        var mentor = new Mentor(); mentor.setCity("   ");
        var mentee = new Mentee(); mentee.setCity("");
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.factors()).containsExactly("location-unset");
    }

    @Test
    void invalidStoredCoordinates_degradesToLocationUnset() {
        // Defensive: if a row somehow holds out-of-range lat/lon (bypassing
        // the DB CHECK), the signal swallows the IAE and returns location-unset
        // rather than failing the whole matching request.
        var signal = new LocationProximitySignal(props(true, 100, 0.0));
        var mentor = new Mentor();
        mentor.setLatitude(95.0); mentor.setLongitude(0.0);
        var mentee = new Mentee();
        mentee.setLatitude(0.0); mentee.setLongitude(0.0);
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.factors()).containsExactly("location-unset");
    }

    @Test
    void onlyMentorMissingLongitude_treatedAsAbsent() {
        // L62 short-circuit branches: each null position in (mLat,mLon,eLat,eLon)
        // independently flips haveCoords false. Exercised: missing eLat above.
        // Here we exercise missing mLon specifically.
        var signal = new LocationProximitySignal(props(true, 100, 0.2));
        var mentor = new Mentor(); mentor.setLatitude(41.0); // no longitude
        var mentee = new Mentee(); mentee.setLatitude(41.0); mentee.setLongitude(29.0);
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX).factors())
                .containsExactly("location-unset");
    }

    @Test
    void onlyMenteeMissingLongitude_treatedAsAbsent() {
        var signal = new LocationProximitySignal(props(true, 100, 0.2));
        var mentor = new Mentor(); mentor.setLatitude(41.0); mentor.setLongitude(29.0);
        var mentee = new Mentee(); mentee.setLatitude(41.0); // no longitude
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX).factors())
                .containsExactly("location-unset");
    }

    @Test
    void onlyMenteeBNullInSameCityCheck_returnsFalse() {
        // sameCity short-circuit: covers the b==null branch directly.
        var signal = new LocationProximitySignal(props(true, 100, 0.2));
        var mentor = new Mentor(); mentor.setCity("Istanbul");
        var mentee = new Mentee(); mentee.setCity(null);
        assertThat(signal.compute(mentor, mentee, EMPTY_CTX).factors())
                .containsExactly("location-unset");
    }

    @Test
    void nullProximityProperties_fallsBackToZeroBonusAndDefaultDecay() {
        // props.proximity() == null path (covers L70/L90/L97).
        var props = new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, 0, 0, 0, 0, 0, 0.2),
                new MentorRecommendationProperties.Signals(false, false, false, false, false, true),
                null,                                   // proximity record missing
                new MentorRecommendationProperties.Mmr(false, 0.7),
                null);
        var signal = new LocationProximitySignal(props);

        // Same city only (no coordinates) → bonus from null proximity = 0.0.
        var mentor = new Mentor(); mentor.setCity("Istanbul");
        var mentee = new Mentee(); mentee.setCity("Istanbul");
        var out = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).containsExactly("city-match");

        // With coordinates → decayKm defaults to 100; score still computes.
        mentor.setLatitude(41.0); mentor.setLongitude(29.0);
        mentee.setLatitude(41.0); mentee.setLongitude(29.0);
        var out2 = signal.compute(mentor, mentee, EMPTY_CTX);
        assertThat(out2.normalizedScore()).isGreaterThan(0.99);
        assertThat(out2.factors()).contains("nearby:0km", "city-match");
    }

    @Test
    void code() {
        assertThat(new LocationProximitySignal(props(true, 100, 0.2)).code()).isEqualTo("proximity");
    }
}
