package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.MentorScoringSignal;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import com.group7.backend.util.HaversineDistance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Proximity signal for spec 1.1.2.3 ("location-based recommendations for
 * face-to-face pairing"). Computes the great-circle distance between
 * the mentor and mentee with {@link HaversineDistance} and maps it
 * through an exponential decay so nearby pairs score close to 1 and
 * cross-continent pairs score close to 0.
 *
 * <pre>
 *   score = exp(-distance_km / decayKm)         // decayKm from config
 *   factor: "nearby:Xkm"                         // rounded to whole km
 * </pre>
 *
 * <p>When coordinates are absent but both sides recorded the same
 * {@code city} string (case-insensitive), the signal scores
 * {@code cityMatchBonus} (from config) and emits {@code city-match}
 * — the cheap fallback that still rewards same-city pairs even before
 * users opt into precise location.
 *
 * <p>When neither lat/lon nor matching city are available the signal
 * scores 0 and emits {@code location-unset} so the UI can surface that
 * its proximity contribution was unavailable rather than zero on merit.
 */
@Component
public class LocationProximitySignal implements MentorScoringSignal {

    private final MentorRecommendationProperties props;

    public LocationProximitySignal(MentorRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "proximity"; }

    @Override
    public boolean isEnabled() {
        return props.signals() != null && props.signals().proximityEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights() == null ? 0.0 : props.weights().proximity();
    }

    @Override
    public SignalContribution compute(Mentor mentor, Mentee mentee, ScoringContext ctx) {
        Double mLat = mentor.getLatitude(), mLon = mentor.getLongitude();
        Double eLat = mentee.getLatitude(), eLon = mentee.getLongitude();
        boolean haveCoords = mLat != null && mLon != null && eLat != null && eLon != null;

        if (haveCoords) {
            return coordinateScore(mentor, mentee, mLat, mLon, eLat, eLon);
        }

        // Fall back to city-string match if precise coordinates aren't set.
        if (sameCity(mentor.getCity(), mentee.getCity())) {
            double bonus = props.proximity() == null ? 0.0 : props.proximity().cityMatchBonus();
            return new SignalContribution(Math.min(1.0, bonus), List.of("city-match"));
        }

        return new SignalContribution(0.0, List.of("location-unset"));
    }

    private SignalContribution coordinateScore(Mentor mentor, Mentee mentee,
                                               double mLat, double mLon,
                                               double eLat, double eLon) {
        double km;
        try {
            km = HaversineDistance.kilometres(mLat, mLon, eLat, eLon);
        } catch (IllegalArgumentException invalid) {
            // Defensive: DB constraints should already enforce ranges, but if
            // a row slipped through (legacy data, future migration) we degrade
            // gracefully rather than failing the whole match request.
            return new SignalContribution(0.0, List.of("location-unset"));
        }

        int decayKm = props.proximity() == null ? 100 : props.proximity().decayKm();
        double score = Math.exp(-km / decayKm);

        List<String> factors = new ArrayList<>(2);
        factors.add("nearby:" + Math.round(km) + "km");
        if (sameCity(mentor.getCity(), mentee.getCity())) {
            factors.add("city-match");
            double bonus = props.proximity() == null ? 0.0 : props.proximity().cityMatchBonus();
            score = Math.min(1.0, score + bonus);
        }
        return new SignalContribution(score, factors);
    }

    private static boolean sameCity(String a, String b) {
        if (a == null || b == null) return false;
        return a.strip().equalsIgnoreCase(b.strip()) && !a.isBlank();
    }
}
