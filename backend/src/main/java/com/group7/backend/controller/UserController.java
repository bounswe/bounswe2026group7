package com.group7.backend.controller;

import com.group7.backend.dto.request.UpdateProfileRequest;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    // ── Photo upload ─────────────────────────────────────────

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload profile photo",
            description = "Uploads a profile photo (JPEG, PNG, GIF, or WebP, max 5MB). "
                    + "Replaces the existing photo if one is set.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Photo uploaded",
                    content = @Content(schema = @Schema(oneOf = {MentorResponse.class, MenteeResponse.class}))),
            @ApiResponse(responseCode = "400", description = "Invalid file (wrong type, too large, or empty)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    public ResponseEntity<ProfileResponse> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        ProfileResponse updated = userService.uploadProfilePhoto(userId, file);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/me/photo")
    @Operation(summary = "Delete profile photo",
            description = "Removes the authenticated user's profile photo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Photo removed",
                    content = @Content(schema = @Schema(oneOf = {MentorResponse.class, MenteeResponse.class}))),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    public ResponseEntity<ProfileResponse> deletePhoto(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        ProfileResponse updated = userService.deleteProfilePhoto(userId);
        return ResponseEntity.ok(updated);
    }

    // ── Existing endpoints ──────────────────────────────────

    @GetMapping
    @Operation(summary = "List users", description = "Returns all user profiles with role-specific fields.")
    @ApiResponse(responseCode = "200", description = "List of users",
            content = @Content(array = @ArraySchema(schema = @Schema(oneOf = {MentorResponse.class, MenteeResponse.class}))))
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

    @GetMapping("/mentors")
    @Operation(summary = "List mentors", description = "Returns all mentor profiles.")
    @ApiResponse(responseCode = "200", description = "List of mentors",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MentorResponse.class))))
    public List<MentorResponse> getAllMentors() {
        return userService.getAllMentors();
    }

    @GetMapping("/mentees")
    @Operation(summary = "List mentees", description = "Returns all mentee profiles.")
    @ApiResponse(responseCode = "200", description = "List of mentees",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MenteeResponse.class))))
    public List<MenteeResponse> getAllMentees() {
        return userService.getAllMentees();
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Delete user", description = "Deletes the authenticated user's own account. Users can only delete themselves.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "403", description = "Cannot delete another user's account", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<Void> deleteUser(@Parameter(description = "User id") @PathVariable Long id,
                                           Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        if (!requesterId.equals(id)) {
            throw new ProfileNotVisibleException("You can only delete your own account");
        }
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
