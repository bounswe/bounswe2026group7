package com.group7.backend.event;

import com.group7.backend.entity.NotificationType;

public record NotificationCreatedEvent(
        Long recipientId,
        NotificationType type,
        String title,
        String body,
        Long entityId,
        Long mentorshipId
) {
    public NotificationCreatedEvent(Long recipientId, NotificationType type, String title, String body) {
        this(recipientId, type, title, body, null, null);
    }
}