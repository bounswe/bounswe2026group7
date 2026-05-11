package com.group7.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables for the automatic temporary ban system (#134, req 2.2.4).
 *
 * <p>Auto-discovered via {@code @ConfigurationPropertiesScan} on
 * {@code BackendApplication}; no manual {@code @EnableConfigurationProperties}
 * is required. {@code @Validated} runs Bean Validation during binding so
 * negative thresholds fail at startup rather than mid-flight.
 *
 * <p>Geometric escalation: ban N gets {@code firstBanHours * factor^(N-1)}
 * hours, capped at {@code maxBanHours}. Defaults: 3 free cancellations, then
 * 24h → 48h → 96h → 192h → … capped at 30 days.
 */
@ConfigurationProperties(prefix = "app.bans")
@Validated
public class BanProperties {

    /** Cancellations strictly below this count are warnings; at this count the first ban fires. */
    @Min(1)
    private int cancellationThreshold = 3;

    /** Hours of the first ban (banOrdinal=1). */
    @Min(1)
    private int firstBanHours = 24;

    /** Geometric multiplier for subsequent bans. */
    @Min(1)
    private int escalationFactor = 2;

    /** Upper bound on any single ban's duration. Default 720h (30 days). */
    @Min(1)
    private int maxBanHours = 720;

    /** When false, BanExpiryScheduler is not registered as a bean. */
    private boolean expiryNotificationEnabled = true;

    public int getCancellationThreshold() { return cancellationThreshold; }
    public void setCancellationThreshold(int cancellationThreshold) { this.cancellationThreshold = cancellationThreshold; }

    public int getFirstBanHours() { return firstBanHours; }
    public void setFirstBanHours(int firstBanHours) { this.firstBanHours = firstBanHours; }

    public int getEscalationFactor() { return escalationFactor; }
    public void setEscalationFactor(int escalationFactor) { this.escalationFactor = escalationFactor; }

    public int getMaxBanHours() { return maxBanHours; }
    public void setMaxBanHours(int maxBanHours) { this.maxBanHours = maxBanHours; }

    public boolean isExpiryNotificationEnabled() { return expiryNotificationEnabled; }
    public void setExpiryNotificationEnabled(boolean expiryNotificationEnabled) {
        this.expiryNotificationEnabled = expiryNotificationEnabled;
    }
}
