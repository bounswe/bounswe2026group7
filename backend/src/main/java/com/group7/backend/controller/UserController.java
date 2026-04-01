package com.group7.backend.controller;

import com.group7.backend.dto.request.UpdateProfileRequest;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Profiles", description = "User, mentor, and mentee profile endpoints")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ── Profile CRUD endpoints ──────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get own profile",
            description = "Returns the full profile of the authenticated user with role-specific fields")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile retrieved",
                    content = @Content(schema = @Schema(oneOf = {MentorResponse.class, MenteeResponse.class}))),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ProfileResponse> getOwnProfile(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        ProfileResponse profile = userService.getOwnProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update own profile",
            description = "Partially updates the authenticated user's profile. "
                    + "Only non-null fields are applied. "
                    + "Mentor-specific fields are ignored for mentees and vice versa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated",
                    content = @Content(schema = @Schema(oneOf = {MentorResponse.class, MenteeResponse.class}))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ProfileResponse> updateOwnProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                           Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        ProfileResponse updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(updated);
    }

    // ── Existing endpoints ──────────────────────────────────

    @GetMapping
    @Operation(summary = "List users")
    @ApiResponse(responseCode = "200", description = "List of users")
    public List<ProfileResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Get user profile by ID",
            description = "Returns the profile of the specified user. "
                    + "Mentees cannot view other mentee profiles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile found",
                    content = @Content(schema = @Schema(oneOf = {MentorResponse.class, MenteeResponse.class}))),
            @ApiResponse(responseCode = "403", description = "Not allowed to view this profile", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ProfileResponse> getUserById(@Parameter(description = "User id") @PathVariable Long id,
                                                       Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        ProfileResponse profile = userService.getProfileById(id, requesterId);
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/mentors")
    @Operation(summary = "Create mentor profile")
    @ApiResponse(responseCode = "200", description = "Mentor created",
            content = @Content)
    public Mentor createMentor(@RequestBody Mentor mentor) {
        return userService.createMentor(mentor);
    }

    @PostMapping("/mentees")
    @Operation(summary = "Create mentee profile")
    @ApiResponse(responseCode = "200", description = "Mentee created",
            content = @Content)
    public Mentee createMentee(@RequestBody Mentee mentee) {
        return userService.createMentee(mentee);
    }

    @GetMapping("/mentors")
    @Operation(summary = "List mentors")
    @ApiResponse(responseCode = "200", description = "List of mentors")
    public List<MentorResponse> getAllMentors() {
        return userService.getAllMentors();
    }

    @GetMapping("/mentees")
    @Operation(summary = "List mentees")
    @ApiResponse(responseCode = "200", description = "List of mentees")
    public List<MenteeResponse> getAllMentees() {
        return userService.getAllMentees();
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Delete user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<Void> deleteUser(@Parameter(description = "User id") @PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
