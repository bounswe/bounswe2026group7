package com.group7.backend.service;

import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.AuthResponse;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
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

    // --- Unique Email (1.2.3.2) ---

    @Test
    void registerWithDuplicateEmailThrows() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
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

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mentor));
        when(passwordEncoder.matches("Password1", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(2L, "john@example.com", "MENTOR")).thenReturn("jwt-token");

        AuthResponse response = authService.authenticate(loginRequest);

        assertEquals("MENTOR", response.getRole());
    }

    // --- Invalid Credentials (1.2.3.7) ---

    @Test
    void loginWithWrongPasswordThrows() {
        Mentee mentee = new Mentee();
        mentee.setEmail("john@example.com");
        mentee.setPasswordHash("hashedPassword");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mentee));
        when(passwordEncoder.matches("Password1", "hashedPassword")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.authenticate(loginRequest));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void loginWithNonexistentEmailThrows() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.authenticate(loginRequest));
        assertEquals("Invalid email or password", ex.getMessage());
    }
}
