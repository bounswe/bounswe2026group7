package com.group7.backend.service;

import com.group7.backend.dto.response.NotificationResponse;
import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private NotificationService notificationService;

    private Notification notification;

    @BeforeEach
    void setUp() {
        User recipient = new com.group7.backend.entity.Mentee();
        recipient.setId(7L);

        notification = new Notification();
        notification.setId(1L);
        notification.setRecipient(recipient);
        notification.setType(NotificationType.REQUEST_ACCEPTED);
        notification.setTitle("Mentorship request accepted");
        notification.setBody("Ahmet accepted your mentorship request.");
        notification.setRead(false);
        notification.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void getNotificationsReturnsMappedResults() {
        when(notificationRepository.findForUser(7L, false)).thenReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.getNotifications(7L, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecipientId()).isEqualTo(7L);
        assertThat(result.get(0).getType()).isEqualTo("REQUEST_ACCEPTED");
    }

    @Test
    void markAsReadUpdatesUnreadNotification() {
        when(notificationRepository.findByIdAndRecipientId(1L, 7L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationResponse response = notificationService.markAsRead(7L, 1L);

        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isNotNull();
    }

    @Test
    void markAsReadThrowsWhenNotificationNotFound() {
        when(notificationRepository.findByIdAndRecipientId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(7L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found");
    }

    @Test
    void markAllAsReadReturnsUpdatedCount() {
        when(notificationRepository.markAllAsRead(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(3);

        int updated = notificationService.markAllAsRead(7L);

        assertThat(updated).isEqualTo(3);
        verify(notificationRepository).markAllAsRead(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());
    }
}