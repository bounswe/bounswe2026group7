package com.group7.backend.dto.response;

import com.group7.backend.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "In-app notification payload")
public class NotificationResponse {

    @Schema(description = "Notification ID", example = "12")
    private Long id;

    @Schema(description = "Recipient user ID", example = "7")
    private Long recipientId;

    @Schema(description = "Notification type", example = "REQUEST_ACCEPTED")
    private String type;

    @Schema(description = "Short notification title", example = "Mentorship Request Accepted")
    private String title;

    @Schema(description = "Detailed notification body")
    private String body;

    @Schema(description = "Read status", example = "false")
    private boolean isRead;

    @Schema(description = "When notification was read")
    private OffsetDateTime readAt;

    @Schema(description = "When notification was created")
    private OffsetDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setRecipientId(notification.getRecipient().getId());
        response.setType(notification.getType().name());
        response.setTitle(notification.getTitle());
        response.setBody(notification.getBody());
        response.setRead(notification.isRead());
        response.setReadAt(notification.getReadAt());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }
}