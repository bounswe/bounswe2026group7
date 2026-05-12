package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.dto.request.MenteeProfileRequest;
import com.group7.backend.dto.request.MentorProfileRequest;
import com.group7.backend.dto.request.SearchRole;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorRatingResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.dto.response.UserProfileResponse;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.service.MentorRatingService;
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
    private final MentorRatingService mentorRatingService;

    public UserController(UserService userService, MentorRatingService mentorRatingService) {
        this.userService = userService;
        this.mentorRatingService = mentorRatingService;
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
    public ResponseEntity<UserProfileResponse> getOwnProfile(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(userService.getOwnUserProfile(userId));
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
        Pageable pageable = PageableSupport.clampPageable(page, size);
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
    public ResponseEntity<UserProfileResponse> getUserById(
            @Parameter(description = "User ID") @PathVariable Long id,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(userService.getUserProfile(id, requesterId));
    }

    @GetMapping("/{id:\\d+}/ratings")
    @Operation(summary = "Paginated mentor ratings list (#518)",
            description = "Returns the ratings a mentor has received, newest-first. Powers the "
                    + "'Recent feedback' block on the public mentor profile. Includes ratings "
                    + "with and without comments — the client decides what to display. "
                    + "menteeId surfaces in the response as the rater's user id; the client "
                    + "batch-resolves names via the existing user-summary endpoint when it "
                    + "wants to render attribution. Authenticated callers only — there is no "
                    + "anonymous read of the ratings list.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated rating list (newest first)"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<Page<MentorRatingResponse>> getMentorRatings(
            @Parameter(description = "Mentor user id") @PathVariable Long id,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 50]") @RequestParam(defaultValue = "10") int size) {
        // Reuse the project's existing clamp helper (same one feeding /mentors etc.) —
        // Spring Data caps size at PageableSupport's policy and keeps page >= 0.
        Pageable pageable = PageableSupport.clampPageable(page, Math.min(size, 50));
        return ResponseEntity.ok(mentorRatingService.getMentorRatings(id, pageable));
    }

    @GetMapping("/mentors")
    @Operation(summary = "List mentors",
            description = "Returns paginated mentor profiles. Supports page and size query parameters.")
    @ApiResponse(responseCode = "200", description = "Paginated list of mentors")
    public ResponseEntity<Page<MentorResponse>> getAllMentors(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(userService.getAllMentors(pageable));
    }

    @GetMapping("/mentors/all")
    @Operation(summary = "List all mentors (unpaginated)",
            description = "Returns all mentor profiles as a plain list. Use /mentors for paginated results.")
    @ApiResponse(responseCode = "200", description = "List of all mentors")
    public ResponseEntity<List<MentorResponse>> getAllMentorsUnpaginated() {
        return ResponseEntity.ok(userService.getAllMentorsList());
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by keyword and filters",
            description = "DB-level search across the user directory with composable filters "
                    + "(#262). Mentees may search MENTOR only; mentors may search MENTEE only; "
                    + "admins may search either role. Same-role search returns 403. "
                    + "Short keyword (length < 3 after trim) is treated as no-keyword "
                    + "(pg_trgm requires ≥3 alphanumerics for index acceleration). "
                    + "hasAvailability=true requires the requester to have at least one "
                    + "availability slot of their own; admins cannot use this filter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated search results"),
            @ApiResponse(responseCode = "400",
                    description = "Invalid filter combination "
                            + "(admin + hasAvailability=true, or requester missing slots)",
                    content = @Content),
            @ApiResponse(responseCode = "403",
                    description = "Same-role search not permitted",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Requester not found", content = @Content)
    })
    public ResponseEntity<Page<ProfileResponse>> searchUsers(
            @Parameter(description = "Search target role")
            @RequestParam SearchRole role,
            @Parameter(description = "Optional keyword; ignored if length < 3 after trim")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filter by interest labels (OR semantics)")
            @RequestParam(required = false) List<String> interests,
            @Parameter(description = "Filter by skill labels (OR semantics)")
            @RequestParam(required = false) List<String> skills,
            @Parameter(description = "Filter by major (matches preferred_mentee_major OR field)")
            @RequestParam(required = false) String major,
            @Parameter(description = "Restrict to candidates whose availability overlaps the requester's slots")
            @RequestParam(defaultValue = "false") boolean hasAvailability,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(userService.searchUsers(
                role, q, interests, skills, major, hasAvailability, requesterId, pageable));
    }

    @GetMapping("/mentees")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "List mentees",
            description = "Returns paginated mentee profiles. Mentor-only. Supports page and size query parameters.")
    @ApiResponse(responseCode = "200", description = "Paginated list of mentees")
    public ResponseEntity<Page<MenteeResponse>> getAllMentees(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableSupport.clampPageable(page, size);
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
}
