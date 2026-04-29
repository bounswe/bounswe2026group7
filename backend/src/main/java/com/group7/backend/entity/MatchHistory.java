package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Tracks the top match for a user (mentee or mentor) over time.
 * Used to determine if a new "match found" notification should be sent.
 */
@Entity
@Table(name = "match_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;

    @Column(name = "top_match_id", nullable = false)
    private Long topMatchId;

    @Column(name = "top_match_name", nullable = false, length = 255)
    private String topMatchName;

    @Column(name = "match_score", nullable = false)
    private Integer matchScore;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private OffsetDateTime calculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        this.calculatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public enum UserType {
        MENTEE,
        MENTOR
    }
}
