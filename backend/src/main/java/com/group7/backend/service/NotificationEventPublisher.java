package com.group7.backend.service;

import com.group7.backend.entity.NotificationType;
import com.group7.backend.event.NotificationCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

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

    public void publishRequestSubmitted(Long recipientId, String mentorFirstName) {
        publish(
                recipientId,
                NotificationType.REQUEST_SUBMITTED,
                "Mentorship request submitted",
                "Your mentorship request to " + mentorFirstName + " has been submitted."
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

    public void publishTaskAssigned(Long menteeId, String taskTitle) {
        publish(
                menteeId,
                NotificationType.TASK_ASSIGNED,
                "New Task Assigned",
                "Your mentor assigned you a new task: " + taskTitle
        );
    }

    public void publishTaskSubmitted(Long mentorId, String menteeName, String taskTitle) {
        publish(
                mentorId,
                NotificationType.TASK_SUBMITTED,
                "Task Submitted",
                menteeName + " submitted their work for task: " + taskTitle
        );
    }

    public void publishTaskReviewed(Long menteeId, String taskTitle, boolean needsRevision) {
        publish(
                menteeId,
                NotificationType.TASK_REVIEWED,
                needsRevision ? "Task Needs Revision" : "Task Completed",
                "Your mentor reviewed your submission for task: " + taskTitle
        );
    }

    public void publishMeetingPendingConfirmation(Long recipientId, String mentorName) {
        publish(
                recipientId,
                NotificationType.MEETING_PENDING_CONFIRMATION,
                "Meeting pending confirmation",
                mentorName + " scheduled a meeting. Please confirm or decline."
        );
    }

    public void publishMeetingConfirmed(Long recipientId, String menteeName) {
        publish(
                recipientId,
                NotificationType.MEETING_CONFIRMED,
                "Meeting confirmed",
                menteeName + " confirmed the meeting."
        );
    }

    public void publishMeetingDeclined(Long recipientId, String menteeName) {
        publish(
                recipientId,
                NotificationType.MEETING_DECLINED,
                "Meeting declined",
                menteeName + " declined the meeting."
        );
    }

    public void publishMeetingAutoDeclined(Long recipientId, String menteeName) {
        publish(
                recipientId,
                NotificationType.MEETING_AUTO_DECLINED,
                "Meeting auto-declined",
                "The meeting with " + menteeName + " expired without confirmation."
        );
    }

    public void publishMeetingRescheduleRequested(Long recipientId, String requesterName) {
        publish(
                recipientId,
                NotificationType.MEETING_RESCHEDULE_REQUESTED,
                "Meeting reschedule requested",
                requesterName + " requested to reschedule the meeting."
        );
    }

    public void publishMeetingRescheduleApproved(Long recipientId, String approverName) {
        publish(
                recipientId,
                NotificationType.MEETING_RESCHEDULE_APPROVED,
                "Meeting reschedule approved",
                approverName + " approved the reschedule request."
        );
    }

    public void publishMeetingRescheduleRejected(Long recipientId, String approverName) {
        publish(
                recipientId,
                NotificationType.MEETING_RESCHEDULE_REJECTED,
                "Meeting reschedule rejected",
                approverName + " rejected the reschedule request."
        );
    }

    public void publishMeetingCancelled(Long recipientId, String mentorName) {
        publish(
                recipientId,
                NotificationType.MEETING_CANCELLED,
                "Meeting cancelled",
                mentorName + " cancelled the meeting."
        );
    }

    public void publishMilestoneCreated(Long menteeId, String milestoneTitle) {
        publish(
                menteeId,
                NotificationType.MILESTONE_CREATED,
                "New Milestone Created",
                "Your mentor has created a new milestone: " + milestoneTitle + "."
        );
    }

    public void publishMilestoneCompleted(Long userId, String milestoneTitle, boolean isMentor) {
        String message = isMentor
                ? "Your mentee's milestone is completed: " + milestoneTitle + "."
                : "Your mentor marked a milestone as completed: " + milestoneTitle + ".";
        publish(
                userId,
                NotificationType.MILESTONE_COMPLETED,
                "Milestone Completed!",
                message
        );
    }

    public void publishActionItemCompleted(Long mentorId, String menteeName, String itemText) {
        String truncated = itemText.length() > 80 ? itemText.substring(0, 80) + "…" : itemText;
        publish(
                mentorId,
                NotificationType.ACTION_ITEM_COMPLETED,
                "Action Item Completed",
                menteeName + " completed the action item: " + truncated + "."
        );
    }

    public void publishUserBanned(Long recipientId, OffsetDateTime expiresAt, String reason, int banCount) {
        publish(
                recipientId,
                NotificationType.USER_BANNED,
                "Account temporarily restricted",
                "Your account has been restricted (ban #" + banCount + ") until " + expiresAt
                        + ". Reason: " + reason
        );
    }

    public void publishBanLifted(Long recipientId) {
        publish(
                recipientId,
                NotificationType.BAN_LIFTED,
                "Ban lifted",
                "Your account restriction has been lifted by an administrator."
        );
    }

    public void publishBanExpired(Long recipientId) {
        publish(
                recipientId,
                NotificationType.BAN_EXPIRED,
                "Ban expired",
                "Your account restriction has expired. You can resume normal use."
        );
    }

    public void publishMentorshipCancelled(Long recipientId, String otherFirstName, String reason) {
        publish(
                recipientId,
                NotificationType.MENTORSHIP_CANCELLED,
                "Mentorship cancelled",
                otherFirstName + " cancelled your mentorship. Reason: " + reason
        );
    }

    public void publishMentorshipEnded(Long recipientId, String mentorFirstName, String reason) {
        String suffix = (reason == null || reason.isBlank()) ? "." : ". Note: " + reason;
        publish(
                recipientId,
                NotificationType.MENTORSHIP_ENDED,
                "Mentorship ended",
                mentorFirstName + " ended your mentorship" + suffix
        );
    }

    public void publishMentorshipExtended(Long recipientId, String mentorFirstName,
                                          int additionalMonths, OffsetDateTime newEndDate) {
        publish(
                recipientId,
                NotificationType.MENTORSHIP_EXTENDED,
                "Mentorship extended",
                mentorFirstName + " extended your mentorship by " + additionalMonths
                        + " month(s); new end date " + newEndDate + "."
        );
    }

    public void publishMentorshipAutoCompleted(Long recipientId, String counterpartFirstName) {
        publish(
                recipientId,
                NotificationType.MENTORSHIP_AUTO_COMPLETED,
                "Mentorship completed",
                "Your mentorship with " + counterpartFirstName
                        + " has reached its end date and is now complete."
        );
    }

    /** Fires FEED_LIKE to the post author. Body collapses cross-post likes per actor under the 24h dedup gate. */
    public void publishFeedLike(Long recipientId, String actorFirstName, Long postId) {
        applicationEventPublisher.publishEvent(new NotificationCreatedEvent(
                recipientId,
                NotificationType.FEED_LIKE,
                "New like",
                actorFirstName + " liked your post.",
                postId,
                null
        ));
    }

    /** Fires FEED_COMMENT to the post author for each new visible comment. */
    public void publishFeedComment(Long recipientId, String actorFirstName, Long postId) {
        applicationEventPublisher.publishEvent(new NotificationCreatedEvent(
                recipientId,
                NotificationType.FEED_COMMENT,
                "New comment",
                actorFirstName + " commented on your post.",
                postId,
                null
        ));
    }

    /** Fires FEED_SHARE to the post author whenever someone shares the post. */
    public void publishFeedShare(Long recipientId, String actorFirstName, Long postId) {
        applicationEventPublisher.publishEvent(new NotificationCreatedEvent(
                recipientId,
                NotificationType.FEED_SHARE,
                "Post shared",
                actorFirstName + " shared your post.",
                postId,
                null
        ));
    }

    /** Fires NEW_FOLLOWER to the followee. entityId carries the follower's user id for deep-link context. */
    public void publishNewFollower(Long recipientId, String followerFirstName, Long followerId) {
        applicationEventPublisher.publishEvent(new NotificationCreatedEvent(
                recipientId,
                NotificationType.NEW_FOLLOWER,
                "New follower",
                followerFirstName + " started following you.",
                followerId,
                null
        ));
    }

    public void publish(Long recipientId, NotificationType type, String title, String body) {
        applicationEventPublisher.publishEvent(new NotificationCreatedEvent(recipientId, type, title, body));
    }

    public void publish(NotificationCreatedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}