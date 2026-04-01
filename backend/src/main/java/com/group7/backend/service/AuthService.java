package com.group7.backend.service;

import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.AuthResponse;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.entity.VerificationToken;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;

    @Value("${app.verification.token-expiry-hours}")
    private int tokenExpiryHours;

    @Value("${app.verification.resend-max-per-hour}")
    private int resendMaxPerHour;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       VerificationTokenRepository verificationTokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificationTokenRepository = verificationTokenRepository;
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
