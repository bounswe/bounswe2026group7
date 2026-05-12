package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A single keyword the user has muted. Posts whose body contains any
 * of the user's muted keywords (case-insensitive substring match) are
 * dropped from every feed read path before DTO mapping.
 *
 * <p>The surrogate {@code id} PK lets the DELETE endpoint accept an
 * integer path variable, avoiding URL-encoding fragility on keywords
 * with spaces or special characters. A {@code UNIQUE (user_id, keyword)}
 * constraint preserves the no-duplicates guarantee that a composite PK
 * would have given.
 */
@Entity
@Table(name = "user_keyword_mutes",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_keyword_mutes_user_keyword", columnNames = {"user_id", "keyword"}))
@Getter
@Setter
@NoArgsConstructor
public class UserKeywordMute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String keyword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public UserKeywordMute(Long userId, String keyword) {
        this.userId = userId;
        this.keyword = keyword;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
