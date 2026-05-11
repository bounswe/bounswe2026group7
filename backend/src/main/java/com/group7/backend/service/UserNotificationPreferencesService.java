package com.group7.backend.service;

import com.group7.backend.dto.request.NotificationPreferencesUpdateRequest;
import com.group7.backend.dto.response.UserNotificationPreferencesResponse;
import com.group7.backend.entity.User;
import com.group7.backend.entity.UserNotificationPreferences;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.UserNotificationPreferencesRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user notification-preferences accessor (#136). The row is created
 * lazily on first read so that callers never observe a missing row;
 * defaults are all-enabled and match the DB-level defaults in
 * {@code V24__push_notifications.sql}.
 */
@Service
public class UserNotificationPreferencesService {

    private final UserNotificationPreferencesRepository repository;
    private final UserRepository userRepository;

    public UserNotificationPreferencesService(UserNotificationPreferencesRepository repository,
                                              UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UserNotificationPreferencesResponse get(Long userId) {
        return UserNotificationPreferencesResponse.from(loadOrCreate(userId));
    }

    @Transactional
    public UserNotificationPreferencesResponse update(Long userId,
                                                      NotificationPreferencesUpdateRequest request) {
        UserNotificationPreferences prefs = loadOrCreate(userId);
        if (request.getMatchesEnabled() != null) prefs.setMatchesEnabled(request.getMatchesEnabled());
        if (request.getMessagesEnabled() != null) prefs.setMessagesEnabled(request.getMessagesEnabled());
        if (request.getMeetingsEnabled() != null) prefs.setMeetingsEnabled(request.getMeetingsEnabled());
        if (request.getTasksEnabled() != null) prefs.setTasksEnabled(request.getTasksEnabled());
        if (request.getRequestsEnabled() != null) prefs.setRequestsEnabled(request.getRequestsEnabled());
        if (request.getTaskDeadlineRemindersEnabled() != null) prefs.setTaskDeadlineRemindersEnabled(request.getTaskDeadlineRemindersEnabled());
        if (request.getMilestoneRemindersEnabled() != null) prefs.setMilestoneRemindersEnabled(request.getMilestoneRemindersEnabled());
        return UserNotificationPreferencesResponse.from(repository.save(prefs));
    }

    private UserNotificationPreferences loadOrCreate(Long userId) {
        return repository.findById(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            UserNotificationPreferences prefs = new UserNotificationPreferences();
            prefs.setUser(user);
            return repository.save(prefs);
        });
    }
}
