package com.group7.backend.event;

import com.group7.backend.entity.NotificationType;

public record NotificationCreatedEvent(
        Long recipientId,
        NotificationType type,
        String title,
        String body
) {
}