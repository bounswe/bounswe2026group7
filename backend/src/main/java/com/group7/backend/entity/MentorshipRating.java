package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "mentorship_ratings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mentorship_ratings_rater",
                columnNames = {"mentorship_id", "rater_user_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class MentorshipRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentorship_id", nullable = false)
    private Mentorship mentorship;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rater_user_id", nullable = false)
    private User rater;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rated_user_id", nullable = false)
    private User rated;

    @Column(nullable = false)
    private int stars;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
