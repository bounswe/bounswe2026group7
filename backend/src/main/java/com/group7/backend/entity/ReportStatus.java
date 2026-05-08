package com.group7.backend.entity;

/**
 * Lifecycle state of a {@link Report} (#135). The transition rules
 * live in {@link ReportStatusMachine} — see that class for the full
 * state diagram and {@code canTransition} semantics.
 *
 * <p>{@code RESOLVED} and {@code DISMISSED} are terminal. Reopening a
 * closed report (if ever needed) would land as a dedicated endpoint
 * rather than a relaxed transition.
 */
public enum ReportStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED
}
