package com.group7.backend.controller;

import com.group7.backend.dto.request.MenteeProfileRequest;
import com.group7.backend.dto.request.MentorProfileRequest;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    // ── Get own profile ─────────────────────────────────────

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
        return ResponseEntity.ok(userService.getOwnProfile(userId));
    }

    // ── Update profile (role-specific endpoints) ────────────

    @PatchMapping("/me/mentor")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Update mentor profile",
            description = "Partially updates the authenticated mentor's profile. Only non-null fields are applied.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated",
                    content = @Content(schema = @Schema(implementation = MentorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a mentor", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ProfileResponse> updateMentorProfile(
            @Valid @RequestBody MentorProfileRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PatchMapping("/me/mentee")
    @PreAuthorize("hasRole('MENTEE')")
    @Operation(summary = "Update mentee profile",
            description = "Partially updates the authenticated mentee's profile. Only non-null fields are applied.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated",
                    content = @Content(schema = @Schema(implementation = MenteeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a mentee", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ProfileResponse> updateMenteeProfile(
            @Valid @RequestBody MenteeProfileRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(userService.updateProfile(userId, request));
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

    // ── Lookup endpoints ────────────────────────────────────

    @GetMapping
    @Operation(summary = "List users",
            description = "Returns paginated user profiles. Mentees only see mentors; mentors see all users.")
    @ApiResponse(responseCode = "200", description = "Paginated list of users")
    public ResponseEntity<Page<ProfileResponse>> getAllUsers(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Pageable pageable = clampPageable(page, size);
        return ResponseEntity.ok(userService.getAllUsersFiltered(requesterId, pageable));
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Get user by ID",
            description = "Returns the profile of the specified user. Mentees cannot view other mentee profiles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile retrieved",
                    content = @Content(schema = @Schema(oneOf = {MentorResponse.class, MenteeResponse.class}))),
            @ApiResponse(responseCode = "403", description = "Profile not visible", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ProfileResponse> getUserById(
            @Parameter(description = "User ID") @PathVariable Long id,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(userService.getProfileById(id, requesterId));
    }

    @GetMapping("/mentors")
    @Operation(summary = "List mentors",
            description = "Returns paginated mentor profiles. Supports page and size query parameters.")
    @ApiResponse(responseCode = "200", description = "Paginated list of mentors")
    public ResponseEntity<Page<MentorResponse>> getAllMentors(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = clampPageable(page, size);
        return ResponseEntity.ok(userService.getAllMentors(pageable));
    }

    @GetMapping("/mentors/all")
    @Operation(summary = "List all mentors (unpaginated)",
            description = "Returns all mentor profiles as a plain list. Use /mentors for paginated results.")
    @ApiResponse(responseCode = "200", description = "List of all mentors")
    public ResponseEntity<List<MentorResponse>> getAllMentorsUnpaginated() {
        return ResponseEntity.ok(userService.getAllMentorsList());
    }

    @GetMapping("/mentees")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "List mentees",
            description = "Returns paginated mentee profiles. Mentor-only. Supports page and size query parameters.")
    @ApiResponse(responseCode = "200", description = "Paginated list of mentees")
    public ResponseEntity<Page<MenteeResponse>> getAllMentees(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = clampPageable(page, size);
        return ResponseEntity.ok(userService.getAllMentees(pageable));
    }

    // ── Delete ──────────────────────────────────────────────

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Delete own account",
            description = "Deletes the authenticated user's own account. Users can only delete themselves.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account deleted"),
            @ApiResponse(responseCode = "403", description = "Cannot delete another user's account", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        if (!id.equals(requesterId)) {
            throw new ProfileNotVisibleException("You can only delete your own account");
        }
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable clampPageable(int page, int size) {
        int clampedSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(Math.max(page, 0), clampedSize);
    }
}
