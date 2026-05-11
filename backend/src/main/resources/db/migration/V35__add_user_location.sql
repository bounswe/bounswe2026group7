-- Adds optional location columns on users (#282).
--
-- Location is opt-in per spec 1.1.2.3 — users without coordinates are still
-- recommendable; the matching pipeline treats absent location as a 0-weight
-- proximity signal (factor: "location-unset" surfaced to the viewer).
--
-- Schema choices, deliberate:
--   * city is free-form text (up to 120 chars) — we don't constrain to a
--     gazetteer because the project doesn't have one and the "city-match"
--     signal uses case-insensitive equality, which tolerates "Istanbul" vs
--     "İstanbul" via the lower() index below.
--   * latitude/longitude are DOUBLE PRECISION (sufficient for ~1 cm
--     precision near the equator — far beyond what city-level matching
--     needs). NULL when only city is set, or when no location set at all.
--   * No NOT NULL constraints — location is genuinely optional.
--   * Range check ensures bad clients can't store impossible coordinates.
--   * Partial index on lat/lon (where both non-null) supports future
--     spatial filtering; today the proximity signal scans the candidate
--     window in-memory so the index is for spec compliance and future use.

ALTER TABLE users
    ADD COLUMN city       VARCHAR(120),
    ADD COLUMN latitude   DOUBLE PRECISION,
    ADD COLUMN longitude  DOUBLE PRECISION;

ALTER TABLE users
    ADD CONSTRAINT users_latitude_range  CHECK (latitude  IS NULL OR (latitude  BETWEEN -90  AND 90)),
    ADD CONSTRAINT users_longitude_range CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180)),
    -- Either both coords are set or both are NULL — never one without the other.
    ADD CONSTRAINT users_coords_pair_completeness CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    );

-- Case-insensitive index for the "same city" comparison used by the
-- LocationProximitySignal when at least one side has only city (no coords).
CREATE INDEX idx_users_city_lower
    ON users (lower(city))
    WHERE city IS NOT NULL;

-- Partial index on (latitude, longitude) for future spatial filters
-- (the in-memory proximity computation in the matching window doesn't need
-- it today, but #282 hints at distance-based filter UIs as a follow-up).
CREATE INDEX idx_users_coords
    ON users (latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
