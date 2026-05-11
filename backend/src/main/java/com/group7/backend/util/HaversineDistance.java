package com.group7.backend.util;

/**
 * Great-circle distance between two points on Earth, using the
 * Haversine formula and a spherical-Earth radius of 6371 km. Drives the
 * proximity signal in the advanced mentor ranker and the
 * {@code nearby:Xkm} factor string. Pure, side-effect-free utility.
 *
 * <p>Earth is an oblate spheroid, not a perfect sphere — the spherical
 * approximation has an error of ~0.5 % at worst (poles vs. equator).
 * For a "are these two people in the same metro?" proximity signal,
 * that's well below the noise floor of how people self-report city
 * boundaries; switching to Vincenty's formula would be over-engineering.
 *
 * <p>Inputs are decimal degrees in WGS-84 (the {@code latitude} /
 * {@code longitude} columns on the {@code users} table). Returns
 * kilometres as a non-negative {@code double}. {@code NaN} or
 * out-of-range coordinates throw {@link IllegalArgumentException} —
 * callers must pre-validate against the DB CHECK constraints (lat in
 * [-90, 90], lon in [-180, 180]).
 */
public final class HaversineDistance {

    /** Spherical-Earth radius in kilometres. */
    static final double EARTH_RADIUS_KM = 6371.0;

    private HaversineDistance() {
        // pure-function utility — no instances
    }

    /**
     * Great-circle distance in kilometres between {@code (lat1, lon1)}
     * and {@code (lat2, lon2)}. Symmetric: {@code km(a, b) == km(b, a)}.
     * Identical points return exactly {@code 0.0}.
     *
     * @throws IllegalArgumentException if any coordinate is NaN or
     *         out of the WGS-84 valid range.
     */
    public static double kilometres(double lat1, double lon1,
                                    double lat2, double lon2) {
        validate(lat1, lon1);
        validate(lat2, lon2);

        double dLatRad = Math.toRadians(lat2 - lat1);
        double dLonRad = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double sinHalfDLat = Math.sin(dLatRad / 2.0);
        double sinHalfDLon = Math.sin(dLonRad / 2.0);
        double a = sinHalfDLat * sinHalfDLat
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinHalfDLon * sinHalfDLon;
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    private static void validate(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            throw new IllegalArgumentException("NaN coordinate");
        }
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude out of range [-90, 90]: " + lat);
        }
        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude out of range [-180, 180]: " + lon);
        }
    }
}
