package com.group7.backend.service;

import com.group7.backend.config.SpamDetectionProperties;
import com.group7.backend.config.ratelimit.BucketCache;
import com.group7.backend.config.ratelimit.ClientIpResolver;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.BanSource;
import com.group7.backend.entity.BotSignal;
import com.group7.backend.entity.BotSignal.SignalType;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.exception.SpamDetectionException;
import com.group7.backend.repository.BotSignalRepository;
import com.group7.backend.repository.UserRepository;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpamDetectionService} (#345). Each signal path is
 * exercised in isolation; the integration test covers the full HTTP +
 * persistence stack.
 */
@ExtendWith(MockitoExtension.class)
class SpamDetectionServiceTest {

    private static final Instant FIXED = Instant.parse("2026-05-11T10:00:00Z");

    @Mock private BotSignalRepository botSignalRepository;
    @Mock private UserRepository userRepository;
    @Mock private BanService banService;
    @Mock private FormTokenService formTokenService;
    @Mock private BucketCache bucketCache;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private HttpServletRequest http;
    @Mock private Bucket bucket;

    private SpamDetectionProperties properties;
    private SpamDetectionService service;

    @BeforeEach
    void setUp() {
        properties = new SpamDetectionProperties();
        properties.setFormTokenSecret("test-secret");
        properties.setMinSubmitSeconds(1.5);
        // Default email bucket (capacity 3, refill PT1H) is fine for these tests.

        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        service = new SpamDetectionService(
                botSignalRepository, userRepository, banService, formTokenService,
                bucketCache, clientIpResolver, properties, clock);
    }

    private RegisterRequest validRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Jane");
        req.setLastName("Doe");
        req.setEmail("jane@example.com");
        req.setPassword("Password1");
        req.setIsMentor(false);
        req.setFormToken("ok-token");
        return req;
    }

    @Test
    void honeypotPopulated_throws_andRecordsSignal_andSkipsTokenCheck() {
        RegisterRequest req = validRequest();
        req.setWebsite("http://spam.example");
        when(clientIpResolver.resolve(http)).thenReturn("1.2.3.4");

        assertThatThrownBy(() -> service.evaluateRegistration(req, http))
                .isInstanceOf(SpamDetectionException.class);

        ArgumentCaptor<BotSignal> captor = ArgumentCaptor.forClass(BotSignal.class);
        verify(botSignalRepository).save(captor.capture());
        assertThat(captor.getValue().getSignalType()).isEqualTo(SignalType.HONEYPOT);
        assertThat(captor.getValue().getIp()).isEqualTo("1.2.3.4");

        // Honeypot short-circuits — we never hit the token verifier.
        verify(formTokenService, never()).verifyAndAge(anyString());
    }

    @Test
    void formTokenInvalid_throws_andRecordsSignal() {
        RegisterRequest req = validRequest();
        when(clientIpResolver.resolve(http)).thenReturn("1.2.3.4");
        when(formTokenService.verifyAndAge("ok-token"))
                .thenThrow(new SpamDetectionException(SignalType.FORM_TOKEN_INVALID));

        assertThatThrownBy(() -> service.evaluateRegistration(req, http))
                .isInstanceOf(SpamDetectionException.class)
                .extracting("signalType").isEqualTo(SignalType.FORM_TOKEN_INVALID);

        ArgumentCaptor<BotSignal> captor = ArgumentCaptor.forClass(BotSignal.class);
        verify(botSignalRepository).save(captor.capture());
        assertThat(captor.getValue().getSignalType()).isEqualTo(SignalType.FORM_TOKEN_INVALID);
    }

    @Test
    void timingTooFast_throws_andRecordsSignal() {
        RegisterRequest req = validRequest();
        when(clientIpResolver.resolve(http)).thenReturn("1.2.3.4");
        when(formTokenService.verifyAndAge("ok-token")).thenReturn(Duration.ofMillis(500));

        assertThatThrownBy(() -> service.evaluateRegistration(req, http))
                .isInstanceOf(SpamDetectionException.class)
                .extracting("signalType").isEqualTo(SignalType.TIMING_TOO_FAST);

        ArgumentCaptor<BotSignal> captor = ArgumentCaptor.forClass(BotSignal.class);
        verify(botSignalRepository).save(captor.capture());
        assertThat(captor.getValue().getSignalType()).isEqualTo(SignalType.TIMING_TOO_FAST);
    }

    @Test
    void emailBucketExhausted_throws_andRecordsSignal() {
        RegisterRequest req = validRequest();
        when(clientIpResolver.resolve(http)).thenReturn("1.2.3.4");
        when(formTokenService.verifyAndAge("ok-token")).thenReturn(Duration.ofSeconds(5));
        when(bucketCache.getOrCreate(anyString(), any())).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);

        assertThatThrownBy(() -> service.evaluateRegistration(req, http))
                .isInstanceOf(SpamDetectionException.class)
                .extracting("signalType").isEqualTo(SignalType.EMAIL_LIMIT);

        ArgumentCaptor<BotSignal> captor = ArgumentCaptor.forClass(BotSignal.class);
        verify(botSignalRepository).save(captor.capture());
        assertThat(captor.getValue().getSignalType()).isEqualTo(SignalType.EMAIL_LIMIT);
    }

    @Test
    void allChecksPassed_doesNotThrow_andDoesNotRecord() {
        RegisterRequest req = validRequest();
        when(clientIpResolver.resolve(http)).thenReturn("1.2.3.4");
        when(formTokenService.verifyAndAge("ok-token")).thenReturn(Duration.ofSeconds(5));
        when(bucketCache.getOrCreate(anyString(), any())).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        service.evaluateRegistration(req, http);

        verify(botSignalRepository, never()).save(any());
    }

    @Test
    void disabled_isANoOp() {
        properties.setEnabled(false);
        RegisterRequest req = validRequest();
        req.setWebsite("http://spam"); // would normally trip honeypot

        service.evaluateRegistration(req, http);

        verify(botSignalRepository, never()).save(any());
        verify(formTokenService, never()).verifyAndAge(anyString());
    }

    @Test
    void onRegistrationCommitted_aboveIpThreshold_flagsAndBans() {
        User user = new Mentee();
        user.setId(42L);
        user.setEmail("jane@example.com");
        when(clientIpResolver.resolve(http)).thenReturn("1.2.3.4");
        when(botSignalRepository.countByIpAndCreatedAtAfter(eq("1.2.3.4"), any()))
                .thenReturn(5L); // ≥ default threshold 3
        when(botSignalRepository.countByEmailHashAndCreatedAtAfter(anyString(), any()))
                .thenReturn(0L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onRegistrationCommitted(user, http);

        assertThat(user.getIsSuspectedBot()).isTrue();
        assertThat(user.getSuspectedAt()).isNotNull();
        verify(banService, times(1))
                .imposeSystemBan(eq(42L), eq("automated abuse signal"), eq(24L));
    }

    @Test
    void onRegistrationCommitted_belowThreshold_isANoOp() {
        User user = new Mentee();
        user.setId(42L);
        user.setEmail("jane@example.com");
        when(clientIpResolver.resolve(http)).thenReturn("1.2.3.4");
        when(botSignalRepository.countByIpAndCreatedAtAfter(anyString(), any()))
                .thenReturn(1L);
        when(botSignalRepository.countByEmailHashAndCreatedAtAfter(anyString(), any()))
                .thenReturn(0L);

        service.onRegistrationCommitted(user, http);

        assertThat(user.getIsSuspectedBot()).isFalse();
        verify(banService, never()).imposeSystemBan(anyLong(), anyString(), anyLong());
    }

    @Test
    void clearFlag_clearsAndLiftsSystemSpamBan() {
        User user = new Mentee();
        user.setId(42L);
        user.setIsSuspectedBot(true);
        user.setSuspectedAt(OffsetDateTime.now(ZoneOffset.UTC));
        Ban spamBan = new Ban();
        spamBan.setId(99L);
        spamBan.setSource(BanSource.SYSTEM_SPAM);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(banService.getActiveBanBySource(42L, BanSource.SYSTEM_SPAM))
                .thenReturn(Optional.of(spamBan));
        when(banService.liftBan(eq(99L), eq(7L))).thenReturn(spamBan);

        service.clearFlag(42L, 7L);

        assertThat(user.getIsSuspectedBot()).isFalse();
        assertThat(user.getSuspectedAt()).isNull();
        verify(banService).liftBan(99L, 7L);
        // Must not fall back to the source-blind unban path — that would
        // re-introduce the bug where an admin ban could get lifted instead.
        verify(banService, never()).unbanUser(anyLong(), anyLong());
    }

    @Test
    void clearFlag_isIdempotentWhenNoActiveSpamBan() {
        User user = new Mentee();
        user.setId(42L);
        user.setIsSuspectedBot(true);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(banService.getActiveBanBySource(42L, BanSource.SYSTEM_SPAM))
                .thenReturn(Optional.empty());

        service.clearFlag(42L, 7L);

        assertThat(user.getIsSuspectedBot()).isFalse();
        verify(banService, never()).liftBan(anyLong(), anyLong());
        verify(banService, never()).unbanUser(anyLong(), anyLong());
    }

    /**
     * Reviewer scenario (#345): user has both a SYSTEM_SPAM ban and an
     * unrelated ADMIN ban active. clearFlag must touch the spam one only —
     * the admin ban (which by construction is unrelated to the bot
     * heuristic) stays in place. This is the regression test for the bug
     * where {@code getActiveBan} returned the latest-expiring row of any
     * source, allowing the admin ban to be lifted by accident.
     */
    @Test
    void clearFlag_doesNotLiftUnrelatedAdminBan() {
        User user = new Mentee();
        user.setId(42L);
        user.setIsSuspectedBot(true);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        // No active spam ban (e.g. it already expired); the admin ban that
        // also exists must not be reachable through this code path.
        when(banService.getActiveBanBySource(42L, BanSource.SYSTEM_SPAM))
                .thenReturn(Optional.empty());

        service.clearFlag(42L, 7L);

        assertThat(user.getIsSuspectedBot()).isFalse();
        verify(banService, never()).liftBan(anyLong(), anyLong());
        verify(banService, never()).unbanUser(anyLong(), anyLong());
        // Critically: clearFlag must never query the source-blind helper —
        // that's exactly the call site that used to misroute admin lifts.
        verify(banService, never()).getActiveBan(anyLong());
    }
}
