package com.group7.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Tunables for the spam-bot detection layer (#345, NFR 2.2.5).
 *
 * <p>Auto-discovered via {@code @ConfigurationPropertiesScan}; bound at
 * startup so out-of-range values fail fast. The form-token secret is
 * mandatory: a missing or empty value blows up rather than silently signing
 * with an empty key.
 */
@ConfigurationProperties(prefix = "app.spam")
@Validated
public class SpamDetectionProperties {

    /**
     * Master switch. When false, {@code SpamDetectionService} skips all
     * registration checks — useful in the default test profile so the bulk
     * suite is not coupled to form-token round-trips. Tests that exercise
     * the layer flip this on via {@code @SpringBootTest(properties=...)}.
     */
    private boolean enabled = true;

    /** HMAC-SHA256 secret for the form-render token. MUST be set in production. */
    @NotNull
    private String formTokenSecret;

    /** Form-render token TTL. Submissions arriving after this elapsed time are rejected. */
    @NotNull
    private Duration formTokenTtl = Duration.ofMinutes(15);

    /** Minimum elapsed seconds from form-render to submit. Faster = bot. */
    @Positive
    private double minSubmitSeconds = 1.5;

    /** Per-email-hash bucket (slow-burn duplicates from same address). */
    private Bucket emailBucket = new Bucket(3, Duration.ofHours(1));

    /** Threshold + window for auto-banning a successfully-registered user when prior signals cluster. */
    private AutoBan autoBan = new AutoBan();

    /** Days after which {@code bot_signals} rows are purged. */
    @Min(1)
    private int signalRetentionDays = 90;

    /** Cron expression for the daily cleanup sweep. */
    @NotNull
    private String cleanupCron = "0 30 3 * * *";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getFormTokenSecret() { return formTokenSecret; }
    public void setFormTokenSecret(String formTokenSecret) { this.formTokenSecret = formTokenSecret; }

    public Duration getFormTokenTtl() { return formTokenTtl; }
    public void setFormTokenTtl(Duration formTokenTtl) { this.formTokenTtl = formTokenTtl; }

    public double getMinSubmitSeconds() { return minSubmitSeconds; }
    public void setMinSubmitSeconds(double minSubmitSeconds) { this.minSubmitSeconds = minSubmitSeconds; }

    public Bucket getEmailBucket() { return emailBucket; }
    public void setEmailBucket(Bucket emailBucket) { this.emailBucket = emailBucket; }

    public AutoBan getAutoBan() { return autoBan; }
    public void setAutoBan(AutoBan autoBan) { this.autoBan = autoBan; }

    public int getSignalRetentionDays() { return signalRetentionDays; }
    public void setSignalRetentionDays(int signalRetentionDays) { this.signalRetentionDays = signalRetentionDays; }

    public String getCleanupCron() { return cleanupCron; }
    public void setCleanupCron(String cleanupCron) { this.cleanupCron = cleanupCron; }

    public static class Bucket {
        @Min(1) private int capacity;
        @NotNull private Duration refill;

        public Bucket() {}
        public Bucket(int capacity, Duration refill) {
            this.capacity = capacity;
            this.refill = refill;
        }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public Duration getRefill() { return refill; }
        public void setRefill(Duration refill) { this.refill = refill; }
    }

    public static class AutoBan {
        /** Signal count in the window that flips {@code isSuspectedBot} on a new registration. */
        @Min(1)
        private int signalThreshold = 3;

        /** Sliding window for the signal count above. */
        @NotNull
        private Duration window = Duration.ofMinutes(10);

        /** Hours the auto-ban lasts. */
        @Min(1)
        private int durationHours = 24;

        public int getSignalThreshold() { return signalThreshold; }
        public void setSignalThreshold(int signalThreshold) { this.signalThreshold = signalThreshold; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
        public int getDurationHours() { return durationHours; }
        public void setDurationHours(int durationHours) { this.durationHours = durationHours; }
    }
}
