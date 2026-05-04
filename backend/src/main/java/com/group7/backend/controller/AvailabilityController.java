package com.group7.backend.controller;

import com.group7.backend.dto.request.AvailabilityOverrideRequest;
import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.request.BulkAvailabilityRequest;
import com.group7.backend.dto.response.AvailabilityOverrideResponse;
import com.group7.backend.dto.response.AvailabilitySlotResponse;
import com.group7.backend.service.AvailabilityOverrideService;
import com.group7.backend.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authorization model for this controller (decided per issue #274):
 * <ul>
 *   <li>{@code GET /{mentorId}} — open to any authenticated user. Mentor weekly
 *       availability is part of the discoverable mentor profile; mentees need it
 *       before deciding whether to send a mentorship request. Global
 *       {@code .anyRequest().authenticated()} in {@code SecurityConfig} is the
 *       only gate. Deliberate, not an oversight.</li>
 *   <li>{@code PUT}, {@code POST}, {@code DELETE} — restricted to {@code MENTOR}
 *       role via {@code @PreAuthorize}; the affected mentor id is read from the
 *       authenticated principal, so a mentor can only mutate their own slots.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/availability")
@Tag(name = "Availability", description = "Mentor weekly availability management")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final AvailabilityOverrideService overrideService;

    public AvailabilityController(AvailabilityService availabilityService,
                                  AvailabilityOverrideService overrideService) {
        this.availabilityService = availabilityService;
        this.overrideService = overrideService;
    }

    /**
     * Returns the mentor's weekly availability. Visible to any authenticated
     * user — see class-level javadoc and issue #274 for the policy decision.
     */
    @GetMapping("/{mentorId}")
    @Operation(
            summary = "Get mentor availability",
            description = "Returns all availability slots for the specified mentor, sorted by day and time. "
                    + "Visible to any authenticated user — mentor availability is part of the discoverable profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability slots",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AvailabilitySlotResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Mentor not found", content = @Content)
    })
    public ResponseEntity<List<AvailabilitySlotResponse>> getSlots(
            @Parameter(description = "Mentor ID") @PathVariable Long mentorId) {
        return ResponseEntity.ok(availabilityService.getSlots(mentorId));
    }

    @PutMapping
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Bulk update availability",
            description = "Replaces all existing availability slots with the provided set. "
                    + "Validates that no slots overlap on the same day."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated availability slots",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AvailabilitySlotResponse.class)))),
            @ApiResponse(responseCode = "409", description = "Overlapping slots or invalid time range", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentor not found", content = @Content)
    })
    public ResponseEntity<List<AvailabilitySlotResponse>> bulkUpdate(
            @Valid @RequestBody BulkAvailabilityRequest request,
            Authentication authentication) {
        Long mentorId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(availabilityService.bulkUpdate(mentorId, request.getSlots()));
    }

    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Add availability slot",
            description = "Adds a single availability slot. Rejects if it overlaps with an existing slot on the same day."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Slot created",
                    content = @Content(schema = @Schema(implementation = AvailabilitySlotResponse.class))),
            @ApiResponse(responseCode = "409", description = "Overlapping slot or invalid time range", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentor not found", content = @Content)
    })
    public ResponseEntity<AvailabilitySlotResponse> addSlot(
            @Valid @RequestBody AvailabilitySlotRequest request,
            Authentication authentication) {
        Long mentorId = (Long) authentication.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED).body(availabilityService.addSlot(mentorId, request));
    }

    @DeleteMapping("/{slotId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Remove availability slot",
            description = "Removes a single availability slot owned by the authenticated mentor."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Slot removed"),
            @ApiResponse(responseCode = "404", description = "Slot not found", content = @Content)
    })
    public ResponseEntity<Void> removeSlot(
            @Parameter(description = "Slot ID") @PathVariable Long slotId,
            Authentication authentication) {
        Long mentorId = (Long) authentication.getCredentials();
        availabilityService.removeSlot(mentorId, slotId);
        return ResponseEntity.noContent().build();
    }

    // ── One-off availability overrides (issue #250) ──────────────────────────

    @GetMapping("/{mentorId}/overrides")
    @Operation(
            summary = "List mentor availability overrides",
            description = "Returns the mentor's one-off availability overrides "
                    + "(AVAILABLE additions or UNAVAILABLE blocks), paginated and "
                    + "ordered by start time. Visible to any authenticated user — "
                    + "mentor availability is part of the discoverable profile, "
                    + "matching the policy of the recurring schedule above."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated overrides",
                    content = @Content(schema = @Schema(implementation = AvailabilityOverrideResponse.class))),
            @ApiResponse(responseCode = "404", description = "Mentor not found", content = @Content)
    })
    public ResponseEntity<Page<AvailabilityOverrideResponse>> listOverrides(
            @Parameter(description = "Mentor ID") @PathVariable Long mentorId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = clampPageable(page, size);
        return ResponseEntity.ok(overrideService.list(mentorId, pageable));
    }

    @PostMapping("/overrides")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Add availability override",
            description = "Adds a one-off override (AVAILABLE addition or UNAVAILABLE block) "
                    + "for the authenticated mentor. Same-kind overlapping ranges are rejected; "
                    + "cross-kind overlap is allowed by design (an UNAVAILABLE can carve a hole "
                    + "out of an AVAILABLE region or out of the recurring weekly schedule)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Override created",
                    content = @Content(schema = @Schema(implementation = AvailabilityOverrideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (missing field or endAt <= startAt)",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentor not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Overlapping same-kind override", content = @Content)
    })
    public ResponseEntity<AvailabilityOverrideResponse> addOverride(
            @Valid @RequestBody AvailabilityOverrideRequest request,
            Authentication authentication) {
        Long mentorId = (Long) authentication.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(overrideService.add(mentorId, request));
    }

    @DeleteMapping("/overrides/{overrideId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Remove availability override",
            description = "Removes a single override owned by the authenticated mentor. "
                    + "Returns 403 if the override belongs to a different mentor (rather than 404, "
                    + "which would leak whether the id exists)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Override removed"),
            @ApiResponse(responseCode = "403", description = "Override belongs to a different mentor",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Override not found", content = @Content)
    })
    public ResponseEntity<Void> removeOverride(
            @Parameter(description = "Override ID") @PathVariable Long overrideId,
            Authentication authentication) {
        Long mentorId = (Long) authentication.getCredentials();
        overrideService.remove(mentorId, overrideId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Page+size clamp matching the convention used by {@code UserController}
     * and {@code MatchingController} (size in {@code [1, 100]}, page ≥ 0).
     * Inlined here for now; consolidate to a shared {@code PageableSupport}
     * helper once #323's prep extraction lands on dev.
     */
    private static Pageable clampPageable(int page, int size) {
        int clampedSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(Math.max(page, 0), clampedSize);
    }
}
