package com.group7.backend.entity;

/**
 * Loose taxonomy of report categories (#135). Extensible — adding a new
 * value requires both a Java enum addition AND a Flyway migration to
 * update the {@code reports_problem_type_check} CHECK constraint.
 * Removing a value requires a migration that scans for and either
 * remaps or rejects existing rows.
 *
 * <p>{@code OTHER} is the catch-all so the UI can submit reports that
 * don't fit a finer category — admin sees the free-text {@code description}
 * for context.
 */
public enum ProblemType {
    INAPPROPRIATE_BEHAVIOR,
    HARASSMENT,
    SPAM,
    MISLEADING_PROFILE,
    OTHER
}
