package com.group7.backend.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct coverage of the moderation state machine (#135). Pins every
 * legal transition + every illegal one, and the terminal-state contract
 * for {@link ReportStatus#RESOLVED} and {@link ReportStatus#DISMISSED}.
 *
 * <p>{@code ReportServiceTest} additionally covers the integration
 * through {@code updateStatus}; this file is the unit-level pin.
 */
class ReportStatusMachineTest {

    // ── canTransition: legal moves ──────────────────────────────────────────

    @Test
    void canTransition_open_toUnderReview() {
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.OPEN, ReportStatus.UNDER_REVIEW)).isTrue();
    }

    @Test
    void canTransition_open_toResolved() {
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.OPEN, ReportStatus.RESOLVED)).isTrue();
    }

    @Test
    void canTransition_open_toDismissed() {
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.OPEN, ReportStatus.DISMISSED)).isTrue();
    }

    @Test
    void canTransition_underReview_toResolved() {
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.UNDER_REVIEW, ReportStatus.RESOLVED)).isTrue();
    }

    @Test
    void canTransition_underReview_toDismissed() {
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.UNDER_REVIEW, ReportStatus.DISMISSED)).isTrue();
    }

    // ── canTransition: illegal moves ────────────────────────────────────────

    @Test
    void canTransition_underReview_toOpen_isFalse() {
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.UNDER_REVIEW, ReportStatus.OPEN)).isFalse();
    }

    @Test
    void canTransition_resolvedToAnything_isFalse() {
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.RESOLVED, ReportStatus.OPEN)).isFalse();
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.RESOLVED, ReportStatus.UNDER_REVIEW)).isFalse();
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.RESOLVED, ReportStatus.DISMISSED)).isFalse();
    }

    @Test
    void canTransition_dismissedToAnything_isFalse() {
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.DISMISSED, ReportStatus.OPEN)).isFalse();
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.DISMISSED, ReportStatus.UNDER_REVIEW)).isFalse();
        assertThat(ReportStatusMachine.canTransition(
                ReportStatus.DISMISSED, ReportStatus.RESOLVED)).isFalse();
    }

    @Test
    void canTransition_sameState_isFalseForEveryStatus() {
        for (ReportStatus s : ReportStatus.values()) {
            assertThat(ReportStatusMachine.canTransition(s, s))
                    .as("Same-state transition for %s must be illegal", s)
                    .isFalse();
        }
    }

    // ── isTerminal ──────────────────────────────────────────────────────────

    @Test
    void isTerminal_trueForResolvedAndDismissed() {
        assertThat(ReportStatusMachine.isTerminal(ReportStatus.RESOLVED)).isTrue();
        assertThat(ReportStatusMachine.isTerminal(ReportStatus.DISMISSED)).isTrue();
    }

    @Test
    void isTerminal_falseForOpenAndUnderReview() {
        assertThat(ReportStatusMachine.isTerminal(ReportStatus.OPEN)).isFalse();
        assertThat(ReportStatusMachine.isTerminal(ReportStatus.UNDER_REVIEW)).isFalse();
    }

    // ── legalNextStates ─────────────────────────────────────────────────────

    @Test
    void legalNextStates_open_returnsAllThreeNonOpenStates() {
        assertThat(ReportStatusMachine.legalNextStates(ReportStatus.OPEN))
                .containsExactlyInAnyOrder(ReportStatus.UNDER_REVIEW,
                        ReportStatus.RESOLVED, ReportStatus.DISMISSED);
    }

    @Test
    void legalNextStates_underReview_returnsOnlyTerminals() {
        assertThat(ReportStatusMachine.legalNextStates(ReportStatus.UNDER_REVIEW))
                .containsExactlyInAnyOrder(ReportStatus.RESOLVED, ReportStatus.DISMISSED);
    }

    @Test
    void legalNextStates_terminalStates_returnEmpty() {
        assertThat(ReportStatusMachine.legalNextStates(ReportStatus.RESOLVED)).isEmpty();
        assertThat(ReportStatusMachine.legalNextStates(ReportStatus.DISMISSED)).isEmpty();
    }

    @Test
    void legalNextStates_returnsUnmodifiableSet() {
        var states = ReportStatusMachine.legalNextStates(ReportStatus.OPEN);
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> states.add(ReportStatus.OPEN));
    }
}
