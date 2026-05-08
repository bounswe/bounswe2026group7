package com.group7.backend.entity;

/**
 * The kind of resource a {@link Report} is filed against (#135).
 *
 * <p>The {@code reports} table is polymorphic by design: {@code target_id}
 * carries no foreign-key constraint, so a single table serves all three
 * target categories and a target deletion does not cascade-orphan the
 * audit row.
 *
 * <p>Adding a new value requires:
 * <ol>
 *   <li>extending this enum,</li>
 *   <li>a Flyway migration that updates the
 *       {@code reports_target_type_check} CHECK constraint, and</li>
 *   <li>a new branch in {@code ReportService.validateTargetExists}
 *       and {@code ReportService.resolveTargetSummary}.</li>
 * </ol>
 */
public enum ReportTargetType {
    POST,
    MENTORSHIP,
    USER
}
