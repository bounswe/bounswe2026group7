package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A user-submitted report against a feed post, mentorship, or another
 * user (#135). Routed to platform admins via the unified review queue
 * at {@code GET /api/admin/reports}.
 *
 * <p><b>Polymorphic by design — no FK on {@code target_id}.</b> The
 * same table serves all three {@link ReportTargetType} categories, and
 * deletion of the underlying target (post / user / mentorship) does not
 * cascade-orphan the audit row. The denormalised
 * {@code targetSummary} computed at admin view-time handles "target
 * gone" gracefully (returns {@code [user deleted]} etc.).
 *
 * <p><b>No back-references to {@link User}.</b> {@code reporterId} and
 * {@code reviewedById} stay flat (mirrors {@link Follow}, {@link FeedPost})
 * to keep list reads N+1-free. Author / reviewer name resolution is the
 * mapper's job and uses one batch {@code findAllById} per page.
 *
 * <p><b>Update discipline.</b> Every immutable field carries
 * {@code updatable = false} so a stray PATCH cannot mutate the audit
 * trail. Only {@code status}, {@code reviewedAt}, {@code reviewedById}
 * change after creation, and only via the admin transition path.
 *
 * <p><b>Concurrency — {@code @Version}.</b> Two admins concurrently
 * transitioning the same report would otherwise lose-update each
 * other. Optimistic-lock makes one win; the loser surfaces
 * {@code ObjectOptimisticLockingFailureException} → 409 via the
 * existing {@code ConcurrencyFailureException} handler.
 *
 * <p><b>Description sensitivity.</b> {@code description} can contain
 * PII or sensitive third-party content (names, phone numbers, quoted
 * messages). Application code MUST NOT log this field at any level —
 * pinned by tests in {@code ReportServiceTest}.
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "problem_type", nullable = false, updatable = false, length = 40)
    private ProblemType problemType;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    // Initialised to 0L for first persist; bumps on every save. The
    // class-level Javadoc covers why the column exists (concurrent admin
    // transitions on the same report).
    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
