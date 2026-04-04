package com.group7.backend.controller;

import com.group7.backend.dto.request.EmailRequest;
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
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email already in use", content = @Content)
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
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or email not verified", content = @Content)
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
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
            @ApiResponse(responseCode = "400", description = "Invalid email or already verified", content = @Content),
            @ApiResponse(responseCode = "404", description = "Account not found", content = @Content),
            @ApiResponse(responseCode = "429", description = "Too many requests", content = @Content)
    })
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Verification email sent. Please check your inbox."));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Sends a password reset email. Always returns 200 to prevent email enumeration. Rate limited to 5 per hour.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "If the email is registered, a reset link has been sent", content = @Content),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
    })
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If that email is registered, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Sets a new password using the token from the reset email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or already used token", content = @Content)
    })
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now log in."));
    }

    @GetMapping("/validate-reset-token")
    @Operation(summary = "Validate reset token", description = "Checks whether a password reset token is still valid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token is valid", content = @Content),
            @ApiResponse(responseCode = "400", description = "Token is invalid or expired", content = @Content)
    })
    public ResponseEntity<Map<String, String>> validateResetToken(@RequestParam String token) {
        authService.validateResetToken(token);
        return ResponseEntity.ok(Map.of("message", "Token is valid."));
    }
}
