package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Mentee-submitted rating for a mentor after a mentorship has terminated
 * (#237). One row per mentorship — enforced both by the service-level
 * existsBy check and by the {@code UNIQUE(mentorship_id)} DB constraint.
 *
 * <p>Scope is intentionally mentee → mentor only for #237; mentor → mentee
 * ratings are out of scope.
 */
@Entity
@Table(name = "mentor_ratings")
@Getter
@Setter
@NoArgsConstructor
public class MentorRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentorship_id", nullable = false, unique = true)
    private Long mentorshipId;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "mentee_id", nullable = false)
    private Long menteeId;

    @Column(nullable = false)
    private Integer score;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public static MentorRating of(Long mentorshipId, Long mentorId, Long menteeId,
                                  Integer score, String comment) {
        MentorRating r = new MentorRating();
        r.setMentorshipId(mentorshipId);
        r.setMentorId(mentorId);
        r.setMenteeId(menteeId);
        r.setScore(score);
        r.setComment(comment);
        return r;
    }
}
