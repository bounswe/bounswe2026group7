package com.group7.backend.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Shared normalisation for the SQL-side search filters introduced in #262.
 * Both {@code MatchingService} and {@code UserService.searchUsers} call
 * into these helpers so wildcard escaping, lowercasing, and empty-list
 * coalescing rules stay consistent across the two surfaces.
 *
 * <h3>Why this is correctness-required, not stylistic</h3>
 * <ul>
 *   <li>Empty {@code List<String>} bound to JPQL {@code IN :list} translates
 *       to SQL {@code IN ()} which Postgres rejects. Coalescing empty to
 *       {@code null} lets the {@code :list IS NULL} gate short-circuit.</li>
 *   <li>{@code LOWER(column) LIKE :keyword} compares lowercased column to
 *       the parameter; the parameter must already be lowercased or matches
 *       drop unpredictably.</li>
 *   <li>{@link String#toLowerCase()} (no args) uses {@link Locale#getDefault()},
 *       which on a Turkish-locale JVM lowercases {@code "I"} to {@code "ı"}
 *       (dotless). Postgres's {@code LOWER()} uses the database collation
 *       (typically {@code en_US.UTF-8}); the two sides would disagree on
 *       any keyword containing {@code I}. All lowercasing here uses
 *       {@link Locale#ROOT} for predictable ASCII-style folding, matching
 *       what the database does.</li>
 *   <li>pg_trgm GIN indexes accelerate {@code LIKE} only when the pattern
 *       has ≥3 alphanumeric chars. Shorter inputs would silently seq-scan;
 *       returning null skips the keyword filter entirely.</li>
 * </ul>
 */
final class SearchNormaliser {

    private SearchNormaliser() {
    }

    /**
     * Normalises a user-supplied keyword for SQL {@code LIKE :keyword
     * ESCAPE '|'}. Returns null for null, blank, or short (less than 3
     * alphanumerics after trim) inputs so the {@code :keyword IS NULL}
     * gate skips the filter.
     *
     * <p>Wildcards in the input are escape-prefixed (so {@code "abc%def"}
     * matches the literal {@code "abc%def"}, not anything containing
     * {@code "abc"} followed by anything followed by {@code "def"}).
     * Escape-character order matters: escape {@code |} first, then the
     * SQL wildcards {@code %} and {@code _}.
     */
    static String keyword(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.length() < 3) return null;
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("|", "||")
                .replace("%", "|%")
                .replace("_", "|_");
        return "%" + escaped + "%";
    }

    /**
     * Normalises a multi-value filter list. Filters out null entries,
     * lowercases survivors, and returns null if the result is empty so the
     * {@code :list IS NULL} gate skips the filter (Hibernate translates a
     * non-null empty list to {@code IN ()} which Postgres rejects).
     */
    static List<String> list(List<String> raw) {
        if (raw == null || raw.isEmpty()) return null;
        List<String> normalised = raw.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        return normalised.isEmpty() ? null : normalised;
    }

    /**
     * Normalises a single-value scalar filter (e.g., {@code major}).
     * Returns null for null/blank inputs; lowercased+trimmed otherwise.
     */
    static String scalar(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase(Locale.ROOT);
    }
}
