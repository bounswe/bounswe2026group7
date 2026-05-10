package com.group7.backend.service;

import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.User;
import com.group7.backend.entity.VerificationToken;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pins the system clock to a known instant and verifies that token expiry
 * is computed as an absolute UTC duration regardless of the JVM's default
 * timezone — issue #272 acceptance criterion #2.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceClockTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private com.group7.backend.service.BanService banService;

    private static final Instant FIXED = Instant.parse("2026-04-29T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED, ZoneOffset.UTC);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                verificationTokenRepository,
                passwordResetTokenRepository,
                emailService,
                banService,
                fixedClock
        );
        ReflectionTestUtils.setField(authService, "tokenExpiryHours", 24);
        ReflectionTestUtils.setField(authService, "resendMaxPerHour", 3);
        ReflectionTestUtils.setField(authService, "resetTokenExpiryHours", 1);
        ReflectionTestUtils.setField(authService, "resetMaxRequestsPerHour", 5);
    }

    @Test
    void verificationTokenExpiry_isExactlyTokenExpiryHoursAfterFixedClock() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Time");
        req.setLastName("Zero");
        req.setEmail("tz@test.com");
        req.setPassword("Password1");
        req.setIsMentor(false);

        authService.register(req);

        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        org.mockito.Mockito.verify(verificationTokenRepository).save(captor.capture());

        // Expiry must be exactly fixed-clock + 24h, in UTC.
        Instant expectedExpiry = FIXED.plusSeconds(24 * 3600L);
        assertThat(captor.getValue().getExpiresAt().toInstant())
                .as("token expiry must be JVM-TZ-independent and UTC-anchored")
                .isEqualTo(expectedExpiry);
        assertThat(captor.getValue().getExpiresAt().getOffset())
                .isEqualTo(ZoneOffset.UTC);
    }
}
