package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PATCH body for {@code /api/users/me/notification-preferences} (#136).
 * All fields are nullable so omitted fields are left unchanged.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Partial update for the per-user push category toggles.")
public class NotificationPreferencesUpdateRequest {

    private Boolean matchesEnabled;
    private Boolean messagesEnabled;
    private Boolean meetingsEnabled;
    private Boolean tasksEnabled;
    private Boolean requestsEnabled;
    private Boolean taskDeadlineRemindersEnabled;
    private Boolean milestoneRemindersEnabled;
    private Boolean feedEngagementEnabled;
    private Boolean newFollowerEnabled;
}
