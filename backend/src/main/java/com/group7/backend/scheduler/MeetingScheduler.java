package com.group7.backend.scheduler;

import com.group7.backend.config.MeetingProperties;
import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.MeetingReminderState;
import com.group7.backend.entity.MeetingStatus;
import com.group7.backend.repository.MeetingReminderStateRepository;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.service.NotificationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "app.meetings.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MeetingScheduler {

    private static final Logger log = LoggerFactory.getLogger(MeetingScheduler.class);

    private final MeetingRepository meetingRepository;
    private final MeetingReminderStateRepository reminderStateRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;
    private final List<Integer> reminderOffsetHours;
    private final int reminderWindowMinutes;

    public MeetingScheduler(MeetingRepository meetingRepository,
                            MeetingReminderStateRepository reminderStateRepository,
                            NotificationEventPublisher notificationEventPublisher,
                            Clock clock,
                            MeetingProperties properties) {
        this.meetingRepository = meetingRepository;
        this.reminderStateRepository = reminderStateRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
        this.reminderOffsetHours = properties.reminderOffsetHours();
        this.reminderWindowMinutes = properties.reminderWindowMinutes();
    }

    @Scheduled(
            cron = "${app.meetings.scheduler.cron:0 */5 * * * *}",
            zone = "${app.meetings.scheduler.zone:UTC}")
    @Transactional
    public void run() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        sendReminders(now);
        expirePending(now);
        completePast(now);
    }

    private void sendReminders(OffsetDateTime now) {
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
                String reminderText = "Meeting starts at " + meeting.getStartTime();
                notificationEventPublisher.publishMeetingReminder(
                        meeting.getMentorship().getMentor().getId(), reminderText);
                notificationEventPublisher.publishMeetingReminder(
                        meeting.getMentorship().getMentee().getId(), reminderText);

                MeetingReminderState state = new MeetingReminderState();
                state.setMeeting(meeting);
                state.setReminderOffsetMinutes(offsetMinutes);
                reminderStateRepository.save(state);
            }
        }
    }

    private void expirePending(OffsetDateTime now) {
        List<Meeting> pending = meetingRepository.findByStatusAndConfirmationDeadlineBefore(
                MeetingStatus.PENDING_CONFIRMATION, now);
        for (Meeting meeting : pending) {
            meeting.setStatus(MeetingStatus.EXPIRED);
            meetingRepository.save(meeting);
            notificationEventPublisher.publishMeetingAutoDeclined(
                    meeting.getMentorship().getMentor().getId(),
                    meeting.getMentorship().getMentee().getFirstName());
        }
        if (!pending.isEmpty()) {
            log.info("Meeting auto-expiry: {} meetings expired", pending.size());
        }
    }

    private void completePast(OffsetDateTime now) {
        List<Meeting> confirmed = meetingRepository.findByStatusAndEndTimeBefore(
                MeetingStatus.CONFIRMED, now);
        for (Meeting meeting : confirmed) {
            meeting.setStatus(MeetingStatus.COMPLETED);
            meetingRepository.save(meeting);
        }
        if (!confirmed.isEmpty()) {
            log.info("Meeting auto-complete: {} meetings completed", confirmed.size());
        }
    }
}
