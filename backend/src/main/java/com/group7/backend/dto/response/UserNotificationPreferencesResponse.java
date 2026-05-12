package com.group7.backend.dto.response;

import com.group7.backend.entity.UserNotificationPreferences;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Per-user push notification toggles.")
public class UserNotificationPreferencesResponse {

    private boolean matchesEnabled;
    private boolean messagesEnabled;
    private boolean meetingsEnabled;
    private boolean tasksEnabled;
    private boolean requestsEnabled;
    private boolean taskDeadlineRemindersEnabled;
    private boolean milestoneRemindersEnabled;
    private boolean feedEngagementEnabled;
    private boolean newFollowerEnabled;
    private OffsetDateTime updatedAt;

    public static UserNotificationPreferencesResponse from(UserNotificationPreferences prefs) {
        return new UserNotificationPreferencesResponse(
                prefs.isMatchesEnabled(),
                prefs.isMessagesEnabled(),
                prefs.isMeetingsEnabled(),
                prefs.isTasksEnabled(),
                prefs.isRequestsEnabled(),
                prefs.isTaskDeadlineRemindersEnabled(),
                prefs.isMilestoneRemindersEnabled(),
                prefs.isFeedEngagementEnabled(),
                prefs.isNewFollowerEnabled(),
                prefs.getUpdatedAt());
    }
}
