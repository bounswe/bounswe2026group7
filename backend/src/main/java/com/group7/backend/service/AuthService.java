package com.group7.backend.service;

import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.AuthResponse;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.entity.PasswordResetToken;
import com.group7.backend.entity.VerificationToken;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.group7.backend.exception.AuthenticationFailedException;
import com.group7.backend.exception.DuplicateEmailException;
import com.group7.backend.exception.InvalidTokenException;
import com.group7.backend.exception.RateLimitExceededException;
import com.group7.backend.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    @Value("${app.verification.token-expiry-hours}")
    private int tokenExpiryHours;

    @Value("${app.verification.resend-max-per-hour}")
    private int resendMaxPerHour;

    @Value("${app.password-reset.token-expiry-hours}")
    private int resetTokenExpiryHours;

    @Value("${app.password-reset.max-requests-per-hour}")
    private int resetMaxRequestsPerHour;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       VerificationTokenRepository verificationTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration rejected due to duplicate email");
            throw new DuplicateEmailException("Email already in use");
        }

        User user;
        String role;

        if (Boolean.TRUE.equals(request.getIsMentor())) {
            Mentor mentor = new Mentor();
            mentor.setMaxMenteeCapacity(3);
            mentor.setCurrentMenteeCount(0);
            user = mentor;
            role = "MENTOR";
        } else {
            Mentee mentee = new Mentee();
            mentee.setProfileVisibility(true);
            mentee.setCancelCount(0);
            user = mentee;
            role = "MENTEE";
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsEmailVerified(false);

        User saved = userRepository.save(user);
        log.info("User registered successfully: userId={}, role={}", saved.getId(), role);

        String token = createVerificationToken(saved);
        emailService.sendVerificationEmail(saved, token);

        UserResponse response = new UserResponse();
        response.setId(saved.getId());
        response.setFirstName(saved.getFirstName());
        response.setLastName(saved.getLastName());
        response.setEmail(saved.getEmail());
        response.setProfilePhoto(saved.getProfilePhoto());
        response.setIsEmailVerified(saved.getIsEmailVerified());
        response.setCreatedAt(saved.getCreatedAt());
        response.setRole(role);

        return response;
    }

    public AuthResponse authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Authentication failed: user not found");
                    return new AuthenticationFailedException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Authentication failed: password mismatch for userId={}", user.getId());
            throw new AuthenticationFailedException("Invalid email or password");
        }

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            log.warn("Authentication blocked: email not verified for userId={}", user.getId());
            throw new AuthenticationFailedException("Email not verified. Please check your inbox.");
        }

        String role = roleNameOf(user);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), role);
        log.info("Authentication succeeded: userId={}, role={}", user.getId(), role);

        return new AuthResponse(token, role, user.getId());
    }

    @Transactional
    public void verifyEmail(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));

        if (Boolean.TRUE.equals(verificationToken.getUsed())) {
            throw new InvalidTokenException("Verification token already used");
        }

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Verification token has expired. Please request a new one.");
        }

        User user = verificationToken.getUser();
        user.setIsEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);
        log.info("Email verification completed for userId={}", user.getId());
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with that email"));

        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new InvalidTokenException("Email is already verified");
        }

        long recentCount = verificationTokenRepository.countByUserIdAndCreatedAtAfter(
                user.getId(), LocalDateTime.now().minusHours(1));

        if (recentCount >= resendMaxPerHour) {
            throw new RateLimitExceededException("Too many resend requests. Please try again later.");
        }

        String token = createVerificationToken(user);
        emailService.sendVerificationEmail(user, token);
        log.info("Verification email resent for userId={}", user.getId());
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            long recentCount = passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(
                    user.getId(), LocalDateTime.now().minusHours(1));

            if (recentCount >= resetMaxRequestsPerHour) {
                throw new RateLimitExceededException("Too many password reset requests. Please try again later.");
            }

            passwordResetTokenRepository.deleteByUserIdAndUsedFalse(user.getId());

            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(token);
            resetToken.setExpiresAt(LocalDateTime.now().plusHours(resetTokenExpiryHours));
            resetToken.setUsed(false);
            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(user, token);
            log.info("Password reset requested for userId={}", user.getId());
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid reset token"));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new InvalidTokenException("Reset token already used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Reset token has expired. Please request a new one.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        log.info("Password reset completed for userId={}", user.getId());
    }

    public void validateResetToken(String token) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid reset token"));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new InvalidTokenException("Reset token already used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Reset token has expired. Please request a new one.");
        }
    }

    private static String roleNameOf(User user) {
        if (user instanceof Mentor) return "MENTOR";
        if (user instanceof Mentee) return "MENTEE";
        if (user instanceof Admin)  return "ADMIN";
        throw new IllegalStateException("Unknown user subtype: " + user.getClass().getSimpleName());
    }

    private String createVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        verificationToken.setToken(token);
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(tokenExpiryHours));
        verificationToken.setUsed(false);
        verificationTokenRepository.save(verificationToken);
        return token;
    }
}
