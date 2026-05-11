package com.group7.backend.entity;

/**
 * Direction of a one-off {@link AvailabilityOverride} on a mentor's weekly
 * schedule.
 *
 * <ul>
 *   <li>{@link #AVAILABLE} — mentor is available during this concrete time
 *       range, in addition to (or outside of) their weekly schedule.</li>
 *   <li>{@link #UNAVAILABLE} — mentor is not available during this range,
 *       overriding any weekly slot that would otherwise apply.</li>
 * </ul>
 *
 * <p>Stored as a string in the {@code kind} column with a CHECK constraint;
 * see {@code V18__add_availability_overrides.sql}.
 */
public enum AvailabilityOverrideKind {
    AVAILABLE,
    UNAVAILABLE
}
