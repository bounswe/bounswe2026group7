package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * One-off override on a mentor's weekly availability schedule. Each row
 * carries either an {@link AvailabilityOverrideKind#AVAILABLE} extra slot
 * (e.g., a one-time evening opening) or an {@link AvailabilityOverrideKind#UNAVAILABLE}
 * block (e.g., a vacation week).
 *
 * <p>Effective availability is computed by the consumer (or the iCal export):
 * recurring slots ∪ AVAILABLE overrides, minus UNAVAILABLE overrides over
 * the same time range. The data model keeps the layers separate so each can
 * be edited independently.
 */
@Entity
@Table(name = "mentor_availability_overrides")
@Getter
@Setter
@NoArgsConstructor
public class AvailabilityOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", nullable = false)
    private Mentor mentor;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private AvailabilityOverrideKind kind;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;
}
