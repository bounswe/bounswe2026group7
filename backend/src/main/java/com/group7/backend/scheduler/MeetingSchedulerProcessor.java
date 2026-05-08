package com.group7.backend.scheduler;

import com.group7.backend.config.MeetingProperties;
import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.MeetingReminderState;
import com.group7.backend.entity.MeetingStatus;
import com.group7.backend.repository.MeetingReminderStateRepository;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.service.NotificationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class MeetingSchedulerProcessor {

    private static final Logger log = LoggerFactory.getLogger(MeetingSchedulerProcessor.class);

    private final MeetingRepository meetingRepository;
    private final MeetingReminderStateRepository reminderStateRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final List<Integer> reminderOffsetHours;
    private final int reminderWindowMinutes;

    public MeetingSchedulerProcessor(MeetingRepository meetingRepository,
                                     MeetingReminderStateRepository reminderStateRepository,
                                     NotificationEventPublisher notificationEventPublisher,
                                     MeetingProperties properties) {
        this.meetingRepository = meetingRepository;
        this.reminderStateRepository = reminderStateRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.reminderOffsetHours = properties.getReminderOffsetHours();
        this.reminderWindowMinutes = properties.getReminderWindowMinutes();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReminders(OffsetDateTime now) {
        if (reminderOffsetHours == null || reminderOffsetHours.isEmpty()) {
            log.warn("No reminder offset hours configured, skipping reminders");
            return;
        }

        for (int hours : reminderOffsetHours) {
            int offsetMinutes = hours * 60;
            OffsetDateTime windowStart = now.plusMinutes(offsetMinutes);
            OffsetDateTime windowEnd = windowStart.plusMinutes(reminderWindowMinutes);

            List<Meeting> meetings = meetingRepository.findByStatusAndStartTimeBetween(
                    MeetingStatus.CONFIRMED, windowStart, windowEnd);
            for (Meeting meeting : meetings) {
                if (reminderStateRepository.existsByMeeting_IdAndReminderOffsetMinutes(
                        meeting.getId(), offsetMinutes)) {
                    continue;
                }
                MeetingReminderState state = new MeetingReminderState();
                state.setMeeting(meeting);
                state.setReminderOffsetMinutes(offsetMinutes);
                try {
                    reminderStateRepository.save(state);
                } catch (DataIntegrityViolationException ex) {
                    log.debug("Reminder already sent for meeting {} at offset {} minutes",
                        meeting.getId(), offsetMinutes);
                    continue;
                }

                String reminderText = "Meeting starts at " + meeting.getStartTime();
                notificationEventPublisher.publishMeetingReminder(
                    meeting.getMentorship().getMentor().getId(), reminderText);
                notificationEventPublisher.publishMeetingReminder(
                    meeting.getMentorship().getMentee().getId(), reminderText);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processPending(Meeting meeting) {
        int updated = meetingRepository.expireMeeting(meeting.getId());
        if (updated > 0) {
            notificationEventPublisher.publishMeetingAutoDeclined(
                    meeting.getMentorship().getMentor().getId(),
                    meeting.getMentorship().getMentee().getFirstName());
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processConfirmed(Meeting meeting) {
        return meetingRepository.completeMeeting(meeting.getId()) > 0;
    }
}
