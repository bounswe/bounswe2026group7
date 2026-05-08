package com.group7.backend.entity;

/**
 * Lifecycle state of a {@link Report} (#135).
 *
 * <p>State machine (enforced in {@code ReportService.validateTransition}):
 * <pre>
 *   OPEN ─────► UNDER_REVIEW ─────► RESOLVED
 *     │                       └──► DISMISSED
 *     ├───────────────────────► RESOLVED
 *     └───────────────────────► DISMISSED
 * </pre>
 *
 * <p>{@code RESOLVED} and {@code DISMISSED} are terminal — no further
 * transitions are allowed. Reopening a closed report (if ever needed)
 * would land as a dedicated endpoint rather than a relaxed transition.
 */
public enum ReportStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED
}
