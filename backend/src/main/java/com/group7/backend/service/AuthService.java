package com.group7.backend.service;

import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.AuthResponse;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

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

        String role = (user instanceof Mentor) ? "MENTOR" : "MENTEE";
        String token = jwtService.generateToken(user.getId(), user.getEmail(), role);

        return new AuthResponse(token, role, user.getId());
    }
}
