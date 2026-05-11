package com.group7.backend.service;

import com.group7.backend.config.SpamDetectionProperties;
import com.group7.backend.config.ratelimit.BucketCache;
import com.group7.backend.config.ratelimit.ClientIpResolver;
import com.group7.backend.config.ratelimit.KeyStrategy;
import com.group7.backend.config.ratelimit.RateLimitRule;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.BanSource;
import com.group7.backend.entity.BotSignal;
import com.group7.backend.entity.BotSignal.SignalType;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.exception.SpamDetectionException;
import com.group7.backend.repository.BotSignalRepository;
import com.group7.backend.repository.UserRepository;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Orchestrates the spam-bot layered defences for the registration surface
 * (#345, NFR 2.2.5). Composes:
 *
 * <ul>
 *   <li>honeypot field check (rejects bots that auto-fill every input),</li>
 *   <li>form-render token + minimum-submit-time check (catches headless
 *       submitters that skip rendering or fire too fast),</li>
 *   <li>per-email Bucket4j bucket on top of the existing #252 cache
 *       (catches slow-burn duplicates from the same address),</li>
 *   <li>post-commit signal accumulation that flips {@code isSuspectedBot}
 *       and triggers a system ban via {@link BanService#imposeSystemBan}.</li>
 * </ul>
 *
 * <p>Every rejection records a {@link BotSignal} row with hashed PII so the
 * team can tune thresholds offline. The signal table self-expires via
 * {@code BotSignalCleanupScheduler}.
 */
@Service
public class SpamDetectionService {

    private static final Logger log = LoggerFactory.getLogger(SpamDetectionService.class);
    private static final String EMAIL_BUCKET_PREFIX = "spam-email:";

    private final BotSignalRepository botSignalRepository;
    private final UserRepository userRepository;
    private final BanService banService;
    private final FormTokenService formTokenService;
    private final BucketCache bucketCache;
    private final ClientIpResolver clientIpResolver;
    private final SpamDetectionProperties properties;
    private final Clock clock;
    private final RateLimitRule emailBucketRule;

    public SpamDetectionService(BotSignalRepository botSignalRepository,
                                UserRepository userRepository,
                                BanService banService,
                                FormTokenService formTokenService,
                                BucketCache bucketCache,
                                ClientIpResolver clientIpResolver,
                                SpamDetectionProperties properties,
                                Clock clock) {
        this.botSignalRepository = botSignalRepository;
        this.userRepository = userRepository;
        this.banService = banService;
        this.formTokenService = formTokenService;
        this.bucketCache = bucketCache;
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
        this.clock = clock;
        // Synthetic rule reused for the per-email bucket. Method/pattern are
        // not used by BucketCache.buildBucket; only capacity and refill are.
        this.emailBucketRule = new RateLimitRule(
                HttpMethod.POST, "spam-email", KeyStrategy.IP,
                properties.getEmailBucket().getCapacity(),
                properties.getEmailBucket().getRefill());
    }

    /**
     * Pre-creation gate: rejects obvious bots before {@code AuthService}
     * creates a {@code users} row. Throws {@link SpamDetectionException} on
     * any failed check, after recording a {@link BotSignal} row.
     *
     * <p>Intentionally not {@code @Transactional}: a transactional wrapper
     * would mark itself rollback-only when we throw, undoing the signal
     * write. Each {@link BotSignalRepository#save} call gets its own
     * Spring-managed transaction (via Spring Data JPA's transactional
     * defaults) and commits independently of the rejection throw.
     */
    public void evaluateRegistration(RegisterRequest request, HttpServletRequest http) {
        if (!properties.isEnabled()) {
            return;
        }
        String ip = clientIpResolver.resolve(http);
        String userAgent = truncate(http.getHeader("User-Agent"));
        String emailHash = hashEmail(request.getEmail());
        String payloadHash = hashPayload(request);

        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            recordSignal(SignalType.HONEYPOT, null, ip, userAgent, emailHash, payloadHash);
            throw new SpamDetectionException(SignalType.HONEYPOT);
        }

        Duration age;
        try {
            age = formTokenService.verifyAndAge(request.getFormToken());
        } catch (SpamDetectionException ex) {
            recordSignal(ex.getSignalType(), null, ip, userAgent, emailHash, payloadHash);
            throw ex;
        }

        double ageSeconds = age.toMillis() / 1000.0;
        if (ageSeconds < properties.getMinSubmitSeconds()) {
            recordSignal(SignalType.TIMING_TOO_FAST, null, ip, userAgent, emailHash, payloadHash);
            throw new SpamDetectionException(SignalType.TIMING_TOO_FAST);
        }

        Bucket bucket = bucketCache.getOrCreate(EMAIL_BUCKET_PREFIX + emailHash, emailBucketRule);
        if (!bucket.tryConsume(1)) {
            recordSignal(SignalType.EMAIL_LIMIT, null, ip, userAgent, emailHash, payloadHash);
            throw new SpamDetectionException(SignalType.EMAIL_LIMIT);
        }
    }

    /**
     * Post-commit hook: counts recent signals from the same IP or email
     * within the configured window. If the count clears the threshold,
     * flips {@code isSuspectedBot}, persists, and triggers a system ban so
     * the user can't immediately log in.
     */
    @Transactional
    public void onRegistrationCommitted(User user, HttpServletRequest http) {
        if (!properties.isEnabled()) {
            return;
        }
        SpamDetectionProperties.AutoBan ab = properties.getAutoBan();
        OffsetDateTime since = OffsetDateTime.now(clock).minus(ab.getWindow());

        String ip = clientIpResolver.resolve(http);
        String emailHash = hashEmail(user.getEmail());

        long ipSignals = botSignalRepository.countByIpAndCreatedAtAfter(ip, since);
        long emailSignals = botSignalRepository.countByEmailHashAndCreatedAtAfter(emailHash, since);

        if (ipSignals < ab.getSignalThreshold() && emailSignals < ab.getSignalThreshold()) {
            return;
        }

        User attached = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        attached.setIsSuspectedBot(true);
        attached.setSuspectedAt(OffsetDateTime.now(clock));
        userRepository.save(attached);

        // Record a final IP_BURST signal so the audit log includes the
        // post-commit decision point, not just the rejected attempts.
        recordSignal(SignalType.IP_BURST, attached, ip,
                truncate(http.getHeader("User-Agent")),
                emailHash, null);

        banService.imposeSystemBan(attached.getId(),
                "automated abuse signal",
                ab.getDurationHours());

        log.warn("Auto-flagged suspected bot after commit: userId={}, ipSignals={}, emailSignals={}",
                attached.getId(), ipSignals, emailSignals);
    }

    /**
     * Admin clear-bot-flag (#345). Resets the user's flag and lifts the
     * active system spam-ban so the user can log in again. Idempotent: a
     * missing active spam-ban is not an error here (the auto-ban may
     * already have expired or been lifted manually).
     *
     * <p>Scoped strictly to {@link BanSource#SYSTEM_SPAM}: if the user
     * also has an unrelated active {@link BanSource#ADMIN} or
     * {@link BanSource#MENTEE_CANCELLATION} ban, this method leaves it in
     * place. Lifting those is the {@code BanService.unbanUser} / admin
     * UI's job, not the spam-flag clear path's.
     */
    @Transactional
    public void clearFlag(Long userId, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setIsSuspectedBot(false);
        user.setSuspectedAt(null);
        userRepository.save(user);

        // Look up the spam ban directly and lift it by id, instead of
        // calling unbanUser(userId,...) — that helper re-resolves "the"
        // active ban and would route around the source filter we just
        // applied. liftBan is also idempotent on an already-lifted row,
        // so a tiny race between the lookup and lift commit is harmless.
        banService.getActiveBanBySource(userId, BanSource.SYSTEM_SPAM)
                .ifPresent(ban -> banService.liftBan(ban.getId(), adminId));

        log.info("Bot flag cleared by admin: userId={}, adminId={}", userId, adminId);
    }

    private void recordSignal(SignalType type, User user, String ip, String userAgent,
                              String emailHash, String payloadHash) {
        BotSignal signal = new BotSignal();
        signal.setSignalType(type);
        signal.setUser(user);
        signal.setIp(ip);
        signal.setUserAgent(userAgent);
        signal.setEmailHash(emailHash);
        signal.setPayloadHash(payloadHash);
        botSignalRepository.save(signal);
        log.info("Bot signal recorded: type={}, ip={}, userId={}",
                type, ip, user == null ? null : user.getId());
    }

    private String hashEmail(String email) {
        if (email == null) {
            return null;
        }
        return sha256Hex(email.trim().toLowerCase(Locale.ROOT));
    }

    private String hashPayload(RegisterRequest request) {
        if (request == null) {
            return null;
        }
        String payload = (nullSafe(request.getFirstName()) + "|"
                + nullSafe(request.getLastName()) + "|"
                + nullSafe(request.getEmail()).toLowerCase(Locale.ROOT) + "|"
                + Boolean.TRUE.equals(request.getIsMentor()));
        return sha256Hex(payload);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }
}
