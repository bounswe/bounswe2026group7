package com.group7.backend.service;

import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.AuthResponse;
import com.group7.backend.dto.response.UserResponse;
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
            throw new RuntimeException("Email already in use");
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
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new RuntimeException("Email not verified. Please check your inbox.");
        }

        String role = (user instanceof Mentor) ? "MENTOR" : "MENTEE";
        String token = jwtService.generateToken(user.getId(), user.getEmail(), role);

        return new AuthResponse(token, role, user.getId());
    }

    @Transactional
    public void verifyEmail(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        if (Boolean.TRUE.equals(verificationToken.getUsed())) {
            throw new RuntimeException("Verification token already used");
        }

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification token has expired. Please request a new one.");
        }

        User user = verificationToken.getUser();
        user.setIsEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with that email"));

        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new RuntimeException("Email is already verified");
        }

        long recentCount = verificationTokenRepository.countByUserIdAndCreatedAtAfter(
                user.getId(), LocalDateTime.now().minusHours(1));

        if (recentCount >= resendMaxPerHour) {
            throw new RuntimeException("Too many resend requests. Please try again later.");
        }

        String token = createVerificationToken(user);
        emailService.sendVerificationEmail(user, token);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            long recentCount = passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(
                    user.getId(), LocalDateTime.now().minusHours(1));

            if (recentCount >= resetMaxRequestsPerHour) {
                throw new RuntimeException("Too many password reset requests. Please try again later.");
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
                .orElseThrow(() -> new RuntimeException("Invalid reset token"));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new RuntimeException("Reset token already used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    public void validateResetToken(String token) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid reset token"));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new RuntimeException("Reset token already used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }
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
