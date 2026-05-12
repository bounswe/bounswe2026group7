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
 * switch — the compiler flags missing cases at build time, which is why
 * we treat the switch as the project's source of truth for category-to-
 * preference mapping. DB column defaults match this in
 * {@code V24__push_notifications.sql}.
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

    @Column(name = "task_deadline_reminders_enabled", nullable = false)
    private boolean taskDeadlineRemindersEnabled = true;

    @Column(name = "milestone_reminders_enabled", nullable = false)
    private boolean milestoneRemindersEnabled = true;

    @Column(name = "feed_engagement_enabled", nullable = false)
    private boolean feedEngagementEnabled = true;

    @Column(name = "new_follower_enabled", nullable = false)
    private boolean newFollowerEnabled = true;

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
            case MEETING_REMINDER, MEETING_PENDING_CONFIRMATION, MEETING_CONFIRMED,
                 MEETING_DECLINED, MEETING_AUTO_DECLINED, MEETING_RESCHEDULE_REQUESTED,
                 MEETING_RESCHEDULE_APPROVED, MEETING_RESCHEDULE_REJECTED,
                 MEETING_CANCELLED -> meetingsEnabled;
            case TASK_ASSIGNED, TASK_SUBMITTED, TASK_REVIEWED,
                 MILESTONE_CREATED, MILESTONE_COMPLETED,
                 ACTION_ITEM_COMPLETED -> tasksEnabled;
            case REQUEST_RECEIVED, REQUEST_ACCEPTED, REQUEST_REJECTED,
                 REQUEST_SUBMITTED -> requestsEnabled;
            case TASK_DEADLINE_REMINDER -> taskDeadlineRemindersEnabled;
            case MILESTONE_REMINDER -> milestoneRemindersEnabled;
            // Auto-ban system (#134) — system-mandated communications, always
            // enabled. Users cannot opt out of "you have been banned" notices.
            case USER_BANNED, BAN_LIFTED, BAN_EXPIRED -> true;
            // Mentorship cancellation (#133) — losing an active mentorship is a
            // consequential lifecycle event that affects ongoing tasks/meetings,
            // so it bypasses category opt-outs the same way ban notices do.
            case MENTORSHIP_CANCELLED -> true;
            // Mentorship lifecycle (#237) — same reasoning: end / extend /
            // auto-completion all change the user's relationship status and
            // their pending tasks/meetings, so always-on.
            case MENTORSHIP_ENDED, MENTORSHIP_EXTENDED, MENTORSHIP_AUTO_COMPLETED -> true;
            case FEED_LIKE, FEED_COMMENT, FEED_SHARE -> feedEngagementEnabled;
            case NEW_FOLLOWER -> newFollowerEnabled;
            // Admin-routed report fan-out — operational signal admins cannot
            // opt out of. The fan-out itself only targets admin accounts, so
            // there's no user-side preference to respect.
            case REPORT_RECEIVED -> true;
        };
    }
}
