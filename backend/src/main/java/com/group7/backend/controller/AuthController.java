package com.group7.backend.controller;

import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.request.ResetPasswordRequest;
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
import org.springframework.web.server.ResponseStatusException;

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
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            UserResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates credentials and returns a JWT session token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content),
            @ApiResponse(responseCode = "403", description = "Email not verified", content = @Content)
    })
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.authenticate(request);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", "Unauthorized", "message", ex.getReason()));
        }
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email", description = "Activates the account using the token from the verification email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or already used token", content = @Content)
    })
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
        }
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Sends a new verification email. Rate limited to 3 per hour.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification email sent", content = @Content),
            @ApiResponse(responseCode = "429", description = "Too many requests", content = @Content)
    })
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            authService.resendVerification(email);
            return ResponseEntity.ok(Map.of("message", "Verification email sent. Please check your inbox."));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
        }
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Sends a password reset email. Always returns 200 to prevent email enumeration. Rate limited to 5 per hour.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "If the email is registered, a reset link has been sent", content = @Content)
    })
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) {
        authService.requestPasswordReset(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "If that email is registered, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Sets a new password using the token from the reset email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or already used token", content = @Content)
    })
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now log in."));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
        }
    }

    @GetMapping("/validate-reset-token")
    @Operation(summary = "Validate reset token", description = "Checks whether a password reset token is still valid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token is valid", content = @Content),
            @ApiResponse(responseCode = "400", description = "Token is invalid or expired", content = @Content)
    })
    public ResponseEntity<?> validateResetToken(@RequestParam String token) {
        try {
            authService.validateResetToken(token);
            return ResponseEntity.ok(Map.of("message", "Token is valid."));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
        }
    }
}
