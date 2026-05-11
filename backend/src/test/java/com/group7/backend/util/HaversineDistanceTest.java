package com.group7.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

/**
 * Coverage targets for {@link HaversineDistance}:
 *
 * <ul>
 *   <li>identical points → exactly 0</li>
 *   <li>antipodal points → π·R = ~20015 km (half the Earth's
 *       circumference; the practical maximum)</li>
 *   <li>one-degree-of-latitude at the equator → ~111.19 km</li>
 *   <li>symmetry: km(A,B) == km(B,A)</li>
 *   <li>known city-pair sanity check (Istanbul → Ankara ≈ 350 km)</li>
 *   <li>validation: NaN, out-of-range lat, out-of-range lon</li>
 * </ul>
 */
class HaversineDistanceTest {

    private static final double TOL_KM = 1.0;

    @Test
    void identicalPoints_returnZero() {
        assertThat(HaversineDistance.kilometres(41.0, 29.0, 41.0, 29.0)).isZero();
        assertThat(HaversineDistance.kilometres(0.0, 0.0, 0.0, 0.0)).isZero();
    }

    @Test
    void antipodalPoints_returnPiTimesEarthRadius() {
        // Antipode of (0,0) is (0,180). Distance = π·R.
        double expected = Math.PI * HaversineDistance.EARTH_RADIUS_KM;
        assertThat(HaversineDistance.kilometres(0.0, 0.0, 0.0, 180.0))
                .isCloseTo(expected, offset(1e-6));
    }

    @Test
    void oneDegreeOfLatitudeAtEquator_isAboutOneEleventhDegreeKm() {
        // (0,0) → (1,0): one degree of latitude ≈ 111.195 km.
        assertThat(HaversineDistance.kilometres(0.0, 0.0, 1.0, 0.0))
                .isCloseTo(111.19, offset(0.05));
    }

    @Test
    void istanbulToAnkara_isAboutThreeFiftyKm() {
        // Istanbul ≈ (41.0082, 28.9784), Ankara ≈ (39.9334, 32.8597).
        // Authoritative great-circle distance: ~349 km.
        double d = HaversineDistance.kilometres(41.0082, 28.9784, 39.9334, 32.8597);
        assertThat(d).isCloseTo(349.0, offset(2.0));
    }

    @Test
    void isSymmetric() {
        double ab = HaversineDistance.kilometres(41.0082, 28.9784, 39.9334, 32.8597);
        double ba = HaversineDistance.kilometres(39.9334, 32.8597, 41.0082, 28.9784);
        assertThat(ab).isCloseTo(ba, offset(1e-9));
    }

    @Test
    void poles_distanceIsHalfEarthCircumference() {
        // North pole → South pole = π·R (same as antipodes, different axis).
        double expected = Math.PI * HaversineDistance.EARTH_RADIUS_KM;
        assertThat(HaversineDistance.kilometres(90.0, 0.0, -90.0, 0.0))
                .isCloseTo(expected, offset(TOL_KM));
    }

    // ── Validation branches ─────────────────────────────────────────────

    @Test
    void rejectsNanLatitude() {
        assertThatThrownBy(() -> HaversineDistance.kilometres(Double.NaN, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN");
    }

    @Test
    void rejectsNanLongitude() {
        assertThatThrownBy(() -> HaversineDistance.kilometres(0.0, Double.NaN, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN");
    }

    @Test
    void rejectsOutOfRangeLatitude() {
        assertThatThrownBy(() -> HaversineDistance.kilometres(91.0, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude");
        assertThatThrownBy(() -> HaversineDistance.kilometres(-91.0, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutOfRangeLongitude() {
        assertThatThrownBy(() -> HaversineDistance.kilometres(0.0, 181.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude");
        assertThatThrownBy(() -> HaversineDistance.kilometres(0.0, -181.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
