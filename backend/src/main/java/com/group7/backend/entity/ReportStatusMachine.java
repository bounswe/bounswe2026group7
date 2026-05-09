package com.group7.backend.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Encodes the legal {@link ReportStatus} transitions for the moderation
 * flow (#135). Single source of truth for the state machine — co-located
 * with the enum it operates on, rather than buried in the service layer
 * that consumes it. New transitions land here and the enum's Javadoc
 * stays in sync with one file.
 *
 * <p>Backed by an {@link EnumMap} of {@link EnumSet}s — the idiomatic
 * Java collections for enum-keyed structures (faster lookups, identity
 * hashing, smaller footprint than {@code HashMap}/{@code HashSet}).
 *
 * <p>Stateless and side-effect-free: every method is {@code static}.
 * Verified directly by {@code ReportStatusMachineTest}; the
 * {@code ReportService} state-machine tests cover the integration through
 * {@code updateStatus}.
 *
 * <pre>
 *   OPEN ─────► UNDER_REVIEW ─────► RESOLVED
 *     │                       └──► DISMISSED
 *     ├───────────────────────► RESOLVED
 *     └───────────────────────► DISMISSED
 * </pre>
 *
 * {@code RESOLVED} and {@code DISMISSED} are terminal.
 */
public final class ReportStatusMachine {

    private static final Map<ReportStatus, Set<ReportStatus>> NEXT_STATES;

    static {
        EnumMap<ReportStatus, Set<ReportStatus>> map = new EnumMap<>(ReportStatus.class);
        map.put(ReportStatus.OPEN, Collections.unmodifiableSet(
                EnumSet.of(ReportStatus.UNDER_REVIEW, ReportStatus.RESOLVED, ReportStatus.DISMISSED)));
        map.put(ReportStatus.UNDER_REVIEW, Collections.unmodifiableSet(
                EnumSet.of(ReportStatus.RESOLVED, ReportStatus.DISMISSED)));
        map.put(ReportStatus.RESOLVED, Collections.unmodifiableSet(
                EnumSet.noneOf(ReportStatus.class)));
        map.put(ReportStatus.DISMISSED, Collections.unmodifiableSet(
                EnumSet.noneOf(ReportStatus.class)));
        NEXT_STATES = Collections.unmodifiableMap(map);
    }

    private ReportStatusMachine() {
    }

    /**
     * {@code true} when {@code from} may legally move to {@code to}. Same-state
     * transitions are not legal — every legal target is a distinct state.
     */
    public static boolean canTransition(ReportStatus from, ReportStatus to) {
        return NEXT_STATES.get(from).contains(to);
    }

    /**
     * {@code true} for {@link ReportStatus#RESOLVED} and {@link ReportStatus#DISMISSED};
     * no transitions out of these states are legal.
     */
    public static boolean isTerminal(ReportStatus status) {
        return NEXT_STATES.get(status).isEmpty();
    }

    /**
     * Read-only set of states reachable from {@code from} in one step.
     * Empty for terminal states. The returned set is the same
     * unmodifiable view shared across calls — wrapping happens once at
     * static-init time.
     */
    public static Set<ReportStatus> legalNextStates(ReportStatus from) {
        return NEXT_STATES.get(from);
    }
}
