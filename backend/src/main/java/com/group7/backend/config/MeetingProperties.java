package com.group7.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MeetingProperties {

    private final int confirmationWindowHours;
    private final int confirmationMinHoursBeforeStart;
    private final List<Integer> reminderOffsetHours;
    private final int reminderWindowMinutes;
    private final String schedulerCron;
    private final String schedulerZone;

    public MeetingProperties(
            @Value("${app.meetings.confirmation-window-hours:24}") int confirmationWindowHours,
            @Value("${app.meetings.confirmation-min-hours-before-start:1}") int confirmationMinHoursBeforeStart,
            @Value("${app.meetings.reminder-offset-hours:24,1}") String reminderOffsetHours,
            @Value("${app.meetings.reminder-window-minutes:5}") int reminderWindowMinutes,
            @Value("${app.meetings.scheduler.cron:0 */5 * * * *}") String schedulerCron,
            @Value("${app.meetings.scheduler.zone:UTC}") String schedulerZone) {
        this.confirmationWindowHours = confirmationWindowHours;
        this.confirmationMinHoursBeforeStart = confirmationMinHoursBeforeStart;
        this.reminderOffsetHours = parseOffsets(reminderOffsetHours);
        this.reminderWindowMinutes = reminderWindowMinutes;
        this.schedulerCron = schedulerCron;
        this.schedulerZone = schedulerZone;
    }

    public int confirmationWindowHours() {
        return confirmationWindowHours;
    }

    public int confirmationMinHoursBeforeStart() {
        return confirmationMinHoursBeforeStart;
    }

    public List<Integer> reminderOffsetHours() {
        return reminderOffsetHours;
    }

    public int reminderWindowMinutes() {
        return reminderWindowMinutes;
    }

    public String schedulerCron() {
        return schedulerCron;
    }

    public String schedulerZone() {
        return schedulerZone;
    }

    private static List<Integer> parseOffsets(String raw) {
        List<Integer> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            result.add(24);
            result.add(1);
            return result;
        }
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int value = Integer.parseInt(trimmed);
            if (value > 0) {
                result.add(value);
            }
        }
        if (result.isEmpty()) {
            result.add(24);
            result.add(1);
        }
        return result;
    }
}
