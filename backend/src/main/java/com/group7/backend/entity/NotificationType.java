package com.group7.backend.entity;

public enum NotificationType {
    MATCH_FOUND,
    REQUEST_RECEIVED,
    REQUEST_ACCEPTED,
    REQUEST_REJECTED,
    // Sent to the mentee after a successful POST /api/mentorship-requests
    // (req 1.2.1.6). Pairs with REQUEST_RECEIVED, which targets the mentor.
    REQUEST_SUBMITTED,
    TASK_ASSIGNED,
    TASK_SUBMITTED,
    TASK_REVIEWED,
    NEW_MESSAGE,
    MEETING_REMINDER,
    MEETING_PENDING_CONFIRMATION,
    MEETING_CONFIRMED,
    MEETING_DECLINED,
    MEETING_AUTO_DECLINED,
    MEETING_RESCHEDULE_REQUESTED,
    MEETING_RESCHEDULE_APPROVED,
    MEETING_RESCHEDULE_REJECTED,
    MEETING_CANCELLED,
    MILESTONE_CREATED,
    MILESTONE_COMPLETED,
    ACTION_ITEM_COMPLETED,
    TASK_DEADLINE_REMINDER,
    MILESTONE_REMINDER,
    // Auto-ban system (#134). USER_BANNED fires when a temporary ban is imposed
    // (carries reason + expiresAt in body); BAN_LIFTED fires when an admin
    // overrides; BAN_EXPIRED fires once when the timer runs out.
    USER_BANNED,
    BAN_LIFTED,
    BAN_EXPIRED
}
