package com.group7.backend.scheduler;

import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.MeetingStatus;
import com.group7.backend.repository.MeetingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private final MeetingSchedulerProcessor processor;
    private final Clock clock;

    public MeetingScheduler(MeetingRepository meetingRepository,
                            MeetingSchedulerProcessor processor,
                            Clock clock) {
        this.meetingRepository = meetingRepository;
        this.processor = processor;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${app.meetings.scheduler.cron:0 */5 * * * *}",
            zone = "${app.meetings.scheduler.zone:UTC}")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        
        processor.sendReminders(now);

        List<Meeting> pending = meetingRepository.findByStatusAndConfirmationDeadlineBefore(
                MeetingStatus.PENDING_CONFIRMATION, now);
        int expiredCount = 0;
        for (Meeting meeting : pending) {
            if (processor.processPending(meeting)) {
                expiredCount++;
            }
        }
        if (expiredCount > 0) {
            log.info("Meeting auto-expiry: {} meetings expired", expiredCount);
        }

        List<Meeting> confirmed = meetingRepository.findByStatusAndEndTimeBefore(
                MeetingStatus.CONFIRMED, now);
        int completedCount = 0;
        for (Meeting meeting : confirmed) {
            if (processor.processConfirmed(meeting)) {
                completedCount++;
            }
        }
        if (completedCount > 0) {
            log.info("Meeting auto-complete: {} meetings completed", completedCount);
        }
    }
}
