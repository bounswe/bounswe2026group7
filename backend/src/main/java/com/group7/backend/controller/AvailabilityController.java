package com.group7.backend.controller;

import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.request.BulkAvailabilityRequest;
import com.group7.backend.dto.response.AvailabilitySlotResponse;
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

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
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
}
