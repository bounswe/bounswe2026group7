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
    BAN_EXPIRED,
    // Mentorship cancellation (#133). Sent to the OTHER participant when
    // either side cancels an active mentorship; body includes the reason.
    MENTORSHIP_CANCELLED,
    // Mentorship lifecycle (#237). MENTORSHIP_ENDED is sent to the mentee
    // when the mentor closes a mentorship gracefully (status -> COMPLETED).
    // MENTORSHIP_EXTENDED is sent to the mentee when the mentor pushes
    // end_date forward. MENTORSHIP_AUTO_COMPLETED fires from the scheduler
    // when end_date passes; both participants receive it.
    MENTORSHIP_ENDED,
    MENTORSHIP_EXTENDED,
    MENTORSHIP_AUTO_COMPLETED,
    // Social-feed engagement. FEED_LIKE / FEED_COMMENT / FEED_SHARE fire to
    // the post author when another user interacts with their post. Self-likes
    // / self-comments / self-shares are skipped at the publisher call site.
    // FEED_LIKE is deduplicated against the 24h same-body window so a single
    // actor liking multiple posts collapses to one notification per day.
    FEED_LIKE,
    FEED_COMMENT,
    FEED_SHARE,
    // Sent to the followee when another user follows them. Self-follow is
    // already blocked upstream by SelfFollowException.
    NEW_FOLLOWER
}
