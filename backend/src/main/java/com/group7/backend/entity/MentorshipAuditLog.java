package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Append-only audit row for a mentorship state transition (#133).
 *
 * <p>One row per transition. The first row of a mentorship has
 * {@code fromStatus = null} (NULL → ACTIVE on creation); subsequent rows
 * record {@code from -> to} pairs. {@code reason} is set by user-driven
 * cancellation flows; system-driven transitions leave it null.
 */
@Entity
@Table(name = "mentorship_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class MentorshipAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentorship_id", nullable = false)
    private Long mentorshipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private MentorshipStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20, nullable = false)
    private MentorshipStatus toStatus;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public static MentorshipAuditLog of(Long mentorshipId,
                                        MentorshipStatus fromStatus,
                                        MentorshipStatus toStatus,
                                        Long actorUserId,
                                        String reason) {
        MentorshipAuditLog entry = new MentorshipAuditLog();
        entry.setMentorshipId(mentorshipId);
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setActorUserId(actorUserId);
        entry.setReason(reason);
        return entry;
    }
}
