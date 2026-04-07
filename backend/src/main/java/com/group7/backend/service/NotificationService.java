package com.group7.backend.service;

import com.group7.backend.dto.response.NotificationResponse;
import com.group7.backend.entity.Notification;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId, boolean unreadOnly) {
        return notificationRepository.findForUser(userId, unreadOnly)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(OffsetDateTime.now(ZoneId.of("Europe/Istanbul")));
        }

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, OffsetDateTime.now(ZoneId.of("Europe/Istanbul")));
    }
}