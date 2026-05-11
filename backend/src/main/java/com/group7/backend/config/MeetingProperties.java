package com.group7.backend.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.meetings")
@Validated
@Getter
@Setter
public class MeetingProperties {

    private int confirmationWindowHours = 24;
    private int confirmationMinHoursBeforeStart = 1;
    
    @NotEmpty(message = "Reminder offset hours cannot be empty")
    private List<Integer> reminderOffsetHours = List.of(24, 1);
    
    private int reminderWindowMinutes = 5;

    public static class Scheduler {
        private boolean enabled = true;
        private String cron = "0 */5 * * * *";
        private String zone = "UTC";
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
    }

    private Scheduler scheduler = new Scheduler();
}
