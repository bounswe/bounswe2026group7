package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Per-user category-level toggles for push delivery (#136). One row per
 * user, lazily created on first read. Defaults are all-enabled; a missing
 * row is functionally equivalent to "all enabled" but we still write the
 * row so {@code updatedAt} reflects user intent for audit purposes.
 *
 * <p>The {@link #isEnabledFor(NotificationType)} dispatch is exhaustive
 * over {@link NotificationType}. New enum values must be added to the
 * switch — the compiler will flag missing cases as a tripwire when other
 * features (Tasks #370, Meetings #352) introduce new types.
 */
@Entity
@Table(name = "user_notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserNotificationPreferences {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "matches_enabled", nullable = false)
    private boolean matchesEnabled = true;

    @Column(name = "messages_enabled", nullable = false)
    private boolean messagesEnabled = true;

    @Column(name = "meetings_enabled", nullable = false)
    private boolean meetingsEnabled = true;

    @Column(name = "tasks_enabled", nullable = false)
    private boolean tasksEnabled = true;

    @Column(name = "requests_enabled", nullable = false)
    private boolean requestsEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public boolean isEnabledFor(NotificationType type) {
        return switch (type) {
            case MATCH_FOUND -> matchesEnabled;
            case NEW_MESSAGE -> messagesEnabled;
            case MEETING_REMINDER -> meetingsEnabled;
            case REQUEST_RECEIVED, REQUEST_ACCEPTED, REQUEST_REJECTED,
                 REQUEST_SUBMITTED -> requestsEnabled;
        };
    }
}
