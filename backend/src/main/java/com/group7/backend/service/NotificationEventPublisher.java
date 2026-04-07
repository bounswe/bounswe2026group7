package com.group7.backend.service;

import com.group7.backend.entity.NotificationType;
import com.group7.backend.event.NotificationCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public NotificationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishRequestAccepted(Long recipientId, String mentorFirstName) {
        publish(
                recipientId,
                NotificationType.REQUEST_ACCEPTED,
                "Mentorship request accepted",
                mentorFirstName + " accepted your mentorship request."
        );
    }

    public void publishRequestReceived(Long recipientId, String menteeFirstName) {
        publish(
                recipientId,
                NotificationType.REQUEST_RECEIVED,
                "New mentorship request",
                menteeFirstName + " sent you a mentorship request."
        );
    }

    public void publishRequestRejected(Long recipientId, String mentorFirstName) {
        publish(
                recipientId,
                NotificationType.REQUEST_REJECTED,
                "Mentorship request rejected",
                mentorFirstName + " rejected your mentorship request."
        );
    }

    public void publishMatchFound(Long recipientId, String counterpartName) {
        publish(
                recipientId,
                NotificationType.MATCH_FOUND,
                "New match found",
                "We found a potential match for you: " + counterpartName
        );
    }

    public void publishNewMessage(Long recipientId, String senderName) {
        publish(
                recipientId,
                NotificationType.NEW_MESSAGE,
                "New message",
                "You received a new message from " + senderName
        );
    }

    public void publishMeetingReminder(Long recipientId, String reminderText) {
        publish(
                recipientId,
                NotificationType.MEETING_REMINDER,
                "Meeting reminder",
                reminderText
        );
    }

    public void publish(Long recipientId, NotificationType type, String title, String body) {
        applicationEventPublisher.publishEvent(new NotificationCreatedEvent(recipientId, type, title, body));
    }
}