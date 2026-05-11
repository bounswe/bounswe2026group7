package com.group7.backend.repository;

import com.group7.backend.entity.UserNotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNotificationPreferencesRepository
        extends JpaRepository<UserNotificationPreferences, Long> {
}
