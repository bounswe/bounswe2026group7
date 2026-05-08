package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A registered FCM device token for a user (#136). Each install of the
 * mobile / web client registers exactly one row via
 * {@code POST /api/users/me/devices}; subsequent registrations of the same
 * token are idempotent and refresh {@code lastSeenAt}. The per-user cap
 * (default 5) is enforced in the service layer with oldest-first eviction
 * keyed on {@code lastSeenAt}, not in the schema, because the eviction
 * policy is easier to express in code than as a partial constraint.
 *
 * <p>Token uniqueness is global (not scoped to {@code userId}). An FCM
 * token represents a specific app install and should never appear under
 * two user rows; if two users share a phone the token rotates server-side.
 */
@Entity
@Table(name = "user_devices")
@Getter
@Setter
@NoArgsConstructor
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 512, unique = true)
    private String token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }
}
