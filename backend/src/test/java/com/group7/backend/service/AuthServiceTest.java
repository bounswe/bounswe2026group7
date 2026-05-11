package com.group7.backend.service;

import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.AuthResponse;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.exception.AuthenticationFailedException;
import com.group7.backend.exception.DuplicateEmailException;
import com.group7.backend.exception.InvalidTokenException;
import com.group7.backend.exception.RateLimitExceededException;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.entity.PasswordResetToken;
import com.group7.backend.entity.VerificationToken;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private com.group7.backend.service.BanService banService;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "tokenExpiryHours", 24);
        ReflectionTestUtils.setField(authService, "resendMaxPerHour", 3);
        ReflectionTestUtils.setField(authService, "resetTokenExpiryHours", 1);
        ReflectionTestUtils.setField(authService, "resetMaxRequestsPerHour", 5);

        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPassword("Password1");
        registerRequest.setIsMentor(false);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("Password1");
    }

    // --- Registration Tests (1.2.3.1) ---

    @Test
    void registerMenteeSuccessfully() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse response = authService.register(registerRequest);

        assertEquals("John", response.getFirstName());
        assertEquals("Doe", response.getLastName());
        assertEquals("john@example.com", response.getEmail());
        assertEquals("MENTEE", response.getRole());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertInstanceOf(Mentee.class, captor.getValue());
    }

    @Test
    void registerMentorSuccessfully() {
        registerRequest.setIsMentor(true);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse response = authService.register(registerRequest);

        assertEquals("MENTOR", response.getRole());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        Mentor mentor = assertInstanceOf(Mentor.class, captor.getValue());
        assertEquals(3, mentor.getMaxMenteeCapacity());
        assertEquals(0, mentor.getCurrentMenteeCount());
    }

    @Test
    void registerSendsVerificationEmail() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        authService.register(registerRequest);

        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail(any(User.class), anyString());
    }

    // --- Unique Email (1.2.3.2) ---

    @Test
    void registerWithDuplicateEmailThrows() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        DuplicateEmailException ex = assertThrows(DuplicateEmailException.class,
                () -> authService.register(registerRequest));
        assertEquals("Email already in use", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    // --- Password Hashing (2.2.1) ---

    @Test
    void registerHashesPassword() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("$2a$10$hashedValue");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        authService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("$2a$10$hashedValue", captor.getValue().getPasswordHash());
    }

    // --- Login / Credential Verification (1.2.3.5, 1.2.3.6) ---

    @Test
    void loginSuccessfully() {
        Mentee mentee = new Mentee();
        mentee.setId(1L);
        mentee.setEmail("john@example.com");
        mentee.setPasswordHash("hashedPassword");
        mentee.setIsEmailVerified(true);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mentee));
        when(passwordEncoder.matches("Password1", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(1L, "john@example.com", "MENTEE")).thenReturn("jwt-token");

        AuthResponse response = authService.authenticate(loginRequest);

        assertEquals("jwt-token", response.getSessionToken());
        assertEquals("MENTEE", response.getRole());
        assertEquals(1L, response.getUserId());
    }

    @Test
    void loginMentorReturnsCorrectRole() {
        Mentor mentor = new Mentor();
        mentor.setId(2L);
        mentor.setEmail("john@example.com");
        mentor.setPasswordHash("hashedPassword");
        mentor.setIsEmailVerified(true);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mentor));
        when(passwordEncoder.matches("Password1", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(2L, "john@example.com", "MENTOR")).thenReturn("jwt-token");

        AuthResponse response = authService.authenticate(loginRequest);

        assertEquals("MENTOR", response.getRole());
    }

    // --- Email Verification Required for Login (1.2.3.4) ---

    @Test
    void loginWithUnverifiedEmailThrows() {
        Mentee mentee = new Mentee();
        mentee.setId(1L);
        mentee.setEmail("john@example.com");
        mentee.setPasswordHash("hashedPassword");
        mentee.setIsEmailVerified(false);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mentee));
        when(passwordEncoder.matches("Password1", "hashedPassword")).thenReturn(true);

        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class,
                () -> authService.authenticate(loginRequest));
        assertEquals("Email not verified. Please check your inbox.", ex.getMessage());
    }

    // --- Invalid Credentials (1.2.3.7) ---

    @Test
    void loginWithWrongPasswordThrows() {
        Mentee mentee = new Mentee();
        mentee.setEmail("john@example.com");
        mentee.setPasswordHash("hashedPassword");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mentee));
        when(passwordEncoder.matches("Password1", "hashedPassword")).thenReturn(false);

        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class,
                () -> authService.authenticate(loginRequest));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void loginWithNonexistentEmailThrows() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class,
                () -> authService.authenticate(loginRequest));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    // --- Verify Email (1.2.3.4) ---

    @Test
    void verifyEmailSuccessfully() {
        Mentee user = new Mentee();
        user.setId(1L);
        user.setIsEmailVerified(false);

        VerificationToken token = new VerificationToken();
        token.setToken("valid-token");
        token.setUser(user);
        token.setUsed(false);
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        when(verificationTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        authService.verifyEmail("valid-token");

        assertTrue(user.getIsEmailVerified());
        assertTrue(token.getUsed());
        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(token);
    }

    @Test
    void verifyEmailWithExpiredTokenThrows() {
        Mentee user = new Mentee();
        user.setIsEmailVerified(false);

        VerificationToken token = new VerificationToken();
        token.setToken("expired-token");
        token.setUser(user);
        token.setUsed(false);
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        when(verificationTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> authService.verifyEmail("expired-token"));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void verifyEmailWithUsedTokenThrows() {
        Mentee user = new Mentee();

        VerificationToken token = new VerificationToken();
        token.setToken("used-token");
        token.setUser(user);
        token.setUsed(true);
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        when(verificationTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> authService.verifyEmail("used-token"));
        assertTrue(ex.getMessage().contains("already used"));
    }

    @Test
    void verifyEmailWithInvalidTokenThrows() {
        when(verificationTokenRepository.findByToken("nonexistent")).thenReturn(Optional.empty());

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> authService.verifyEmail("nonexistent"));
        assertEquals("Invalid verification token", ex.getMessage());
    }

    // --- Resend Verification (1.2.3.4) ---

    @Test
    void resendVerificationSuccessfully() {
        Mentee user = new Mentee();
        user.setId(1L);
        user.setEmail("john@example.com");
        user.setIsEmailVerified(false);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(verificationTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(0L);

        authService.resendVerification("john@example.com");

        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail(eq(user), anyString());
    }

    @Test
    void resendVerificationRateLimitedThrows() {
        Mentee user = new Mentee();
        user.setId(1L);
        user.setEmail("john@example.com");
        user.setIsEmailVerified(false);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(verificationTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(3L);

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> authService.resendVerification("john@example.com"));
        assertTrue(ex.getMessage().contains("Too many"));
    }

    @Test
    void resendVerificationForAlreadyVerifiedThrows() {
        Mentee user = new Mentee();
        user.setId(1L);
        user.setEmail("john@example.com");
        user.setIsEmailVerified(true);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> authService.resendVerification("john@example.com"));
        assertTrue(ex.getMessage().contains("already verified"));
    }

    // --- Request Password Reset ---

    @Test
    void requestPasswordResetSendsEmail() {
        Mentee user = new Mentee();
        user.setId(1L);
        user.setEmail("john@example.com");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(0L);

        authService.requestPasswordReset("john@example.com");

        verify(passwordResetTokenRepository).deleteByUserIdAndUsedFalse(1L);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq(user), anyString());
    }

    @Test
    void requestPasswordResetWithUnknownEmailDoesNotThrow() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.requestPasswordReset("unknown@example.com"));
        verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    void requestPasswordResetRateLimitedThrows() {
        Mentee user = new Mentee();
        user.setId(1L);
        user.setEmail("john@example.com");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(5L);

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> authService.requestPasswordReset("john@example.com"));
        assertTrue(ex.getMessage().contains("Too many"));
    }

    // --- Reset Password ---

    @Test
    void resetPasswordSuccessfully() {
        Mentee user = new Mentee();
        user.setId(1L);
        user.setPasswordHash("oldHash");

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("valid-token");
        token.setUser(user);
        token.setUsed(false);
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass1")).thenReturn("newHash");

        authService.resetPassword("valid-token", "NewPass1");

        assertEquals("newHash", user.getPasswordHash());
        assertTrue(token.getUsed());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void resetPasswordWithExpiredTokenThrows() {
        Mentee user = new Mentee();

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("expired-token");
        token.setUser(user);
        token.setUsed(false);
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> authService.resetPassword("expired-token", "NewPass1"));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void resetPasswordWithUsedTokenThrows() {
        Mentee user = new Mentee();

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("used-token");
        token.setUser(user);
        token.setUsed(true);
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> authService.resetPassword("used-token", "NewPass1"));
        assertTrue(ex.getMessage().contains("already used"));
    }

    @Test
    void resetPasswordWithInvalidTokenThrows() {
        when(passwordResetTokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> authService.resetPassword("bad-token", "NewPass1"));
        assertEquals("Invalid reset token", ex.getMessage());
    }
}
