package com.group7.backend.service;

import com.group7.backend.config.BanProperties;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.BanRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Auto-ban policy engine (#134, req 2.2.4).
 *
 * <p>Violation tracking is intentionally narrow today: only mentee-initiated
 * cancellations of pending mentorship requests increment {@code cancelCount}
 * and feed escalation. The first {@code cancellationThreshold - 1} cancels
 * are warning territory; at the threshold the first ban fires for
 * {@code firstBanHours} and each subsequent ban doubles up to
 * {@code maxBanHours}. Configuration lives in {@link BanProperties}.
 *
 * <p>Auto-expiry is a query concern: {@link #isBanned(Long)} returns true
 * iff a row exists with {@code lifted_at IS NULL AND expires_at > now}. The
 * timer simply running out does not require a row mutation; the optional
 * expiry notification is dispatched separately by
 * {@link com.group7.backend.scheduler.BanExpiryScheduler}.
 */
@Service
public class BanService {

    private static final Logger log = LoggerFactory.getLogger(BanService.class);

    private final BanRepository banRepository;
    private final MenteeRepository menteeRepository;
    private final UserRepository userRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final BanProperties properties;
    private final Clock clock;

    public BanService(BanRepository banRepository,
                      MenteeRepository menteeRepository,
                      UserRepository userRepository,
                      NotificationEventPublisher notificationEventPublisher,
                      BanProperties properties,
                      Clock clock) {
        this.banRepository = banRepository;
        this.menteeRepository = menteeRepository;
        this.userRepository = userRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.properties = properties;
        this.clock = clock;
    }

    /** Hot path: invoked by every ban-gated action. */
    @Transactional(readOnly = true)
    public boolean isBanned(Long userId) {
        return getActiveBan(userId).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<Ban> getActiveBan(Long userId) {
        return banRepository.findActive(userId, OffsetDateTime.now(clock), PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<Ban> listBansForUser(Long userId) {
        return banRepository.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    /**
     * Records a mentee-initiated cancellation. Increments {@code cancelCount},
     * and if the threshold is crossed, creates a new ban row and fires
     * {@code USER_BANNED}. Returns the new ban (or {@link Optional#empty()}
     * if the violation was below the threshold).
     */
    @Transactional
    public Optional<Ban> recordCancellation(Long menteeId, String reason) {
        Mentee mentee = menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));

        int updated = mentee.getCancelCount() + 1;
        mentee.setCancelCount(updated);
        menteeRepository.save(mentee);

        if (updated < properties.getCancellationThreshold()) {
            log.info("Cancellation recorded but below threshold: menteeId={}, cancelCount={}, threshold={}",
                    menteeId, updated, properties.getCancellationThreshold());
            return Optional.empty();
        }

        // Counts non-lifted bans only: an admin override should not escalate
        // the next legitimate ban's duration.
        long banOrdinal = banRepository.countNonLiftedByUserId(menteeId) + 1;
        long hours = computeBanHours(banOrdinal);
        OffsetDateTime now = OffsetDateTime.now(clock);

        Ban ban = new Ban();
        ban.setUser(mentee);
        ban.setReason(reason);
        ban.setBanCount((int) banOrdinal);
        ban.setExpiresAt(now.plusHours(hours));
        Ban saved = banRepository.save(ban);

        notificationEventPublisher.publishUserBanned(
                menteeId, saved.getExpiresAt(), reason, saved.getBanCount());

        log.warn("Auto-ban imposed: menteeId={}, banOrdinal={}, hours={}, expiresAt={}",
                menteeId, banOrdinal, hours, saved.getExpiresAt());
        return Optional.of(saved);
    }

    /**
     * Admin-initiated ban (#280). Inserts a fresh row immediately; if the user
     * already has an active ban it is left in place and the new row stacks
     * (the {@link #getActiveBan} query takes the row with the latest
     * {@code expiresAt}, so the longer ban wins).
     *
     * <p>Uses {@code countNonLiftedByUserId(...) + 1} for the ordinal so an
     * admin override participates in the same escalation accounting as
     * auto-bans — consistent with how {@link #recordCancellation} computes
     * its ordinal.
     *
     * @throws ResourceNotFoundException 404 — target user or admin not found
     * @throws IllegalArgumentException 400 — non-positive duration
     */
    @Transactional
    public Ban imposeAdminBan(Long targetUserId, Long adminId,
                              String reason, long durationHours) {
        if (durationHours <= 0) {
            throw new IllegalArgumentException("durationHours must be positive");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // Admin existence is verified by the @PreAuthorize at the controller,
        // but we still resolve the row to fail fast on a missing JWT subject.
        userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        long banOrdinal = banRepository.countNonLiftedByUserId(targetUserId) + 1;
        OffsetDateTime now = OffsetDateTime.now(clock);

        Ban ban = new Ban();
        ban.setUser(target);
        ban.setReason(reason);
        ban.setBanCount((int) banOrdinal);
        ban.setExpiresAt(now.plusHours(durationHours));
        Ban saved = banRepository.save(ban);

        notificationEventPublisher.publishUserBanned(
                targetUserId, saved.getExpiresAt(), reason, saved.getBanCount());

        log.warn("Admin-imposed ban: targetUserId={}, adminId={}, durationHours={}, expiresAt={}",
                targetUserId, adminId, durationHours, saved.getExpiresAt());
        return saved;
    }

    /**
     * Admin unban (#280): lifts the user's currently-active ban. Returns the
     * lifted ban; throws 404 if the user has no active ban so the admin gets
     * a clear signal rather than a silent no-op.
     */
    @Transactional
    public Ban unbanUser(Long targetUserId, Long adminId) {
        Ban active = getActiveBan(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User has no active ban"));
        return liftBan(active.getId(), adminId);
    }

    /** Admin override. Idempotent — lifting an already-lifted ban is a no-op. */
    @Transactional
    public Ban liftBan(Long banId, Long adminId) {
        Ban ban = banRepository.findById(banId)
                .orElseThrow(() -> new ResourceNotFoundException("Ban not found"));

        if (ban.getLiftedAt() != null) {
            return ban; // already lifted; skip admin lookup + notification
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        ban.setLiftedAt(OffsetDateTime.now(clock));
        ban.setLiftedByAdminId(admin.getId());
        Ban saved = banRepository.save(ban);

        notificationEventPublisher.publishBanLifted(ban.getUser().getId());
        log.info("Ban lifted by admin: banId={}, userId={}, adminId={}",
                banId, ban.getUser().getId(), adminId);
        return saved;
    }

    private long computeBanHours(long banOrdinal) {
        long hours = (long) properties.getFirstBanHours()
                * (long) Math.pow(properties.getEscalationFactor(), banOrdinal - 1);
        return Math.min(hours, properties.getMaxBanHours());
    }
}
