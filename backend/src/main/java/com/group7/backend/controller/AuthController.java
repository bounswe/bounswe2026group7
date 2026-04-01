package com.group7.backend.controller;

import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.AuthResponse;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register user", description = "Creates a new user and sends a verification email.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates credentials and returns a JWT session token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content),
            @ApiResponse(responseCode = "403", description = "Email not verified", content = @Content)
    })
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email", description = "Activates the account using the token from the verification email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or already used token", content = @Content)
    })
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Sends a new verification email. Rate limited to 3 per hour.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification email sent", content = @Content),
            @ApiResponse(responseCode = "429", description = "Too many requests", content = @Content)
    })
    public ResponseEntity<Map<String, String>> resendVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        authService.resendVerification(email);
        return ResponseEntity.ok(Map.of("message", "Verification email sent. Please check your inbox."));
    }
}
