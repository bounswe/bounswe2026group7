package com.group7.backend.controller;

import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.request.BulkAvailabilityRequest;
import com.group7.backend.dto.response.MenteeAvailabilitySlotResponse;
import com.group7.backend.service.MenteeAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/mentee-availability")
@PreAuthorize("hasRole('MENTEE')")
@Tag(name = "Mentee Availability", description = "Mentee weekly availability management")
public class MenteeAvailabilityController {

    private final MenteeAvailabilityService menteeAvailabilityService;

    public MenteeAvailabilityController(MenteeAvailabilityService menteeAvailabilityService) {
        this.menteeAvailabilityService = menteeAvailabilityService;
    }

    @GetMapping
    @Operation(
            summary = "Get own availability",
            description = "Returns all weekly availability slots of the authenticated mentee, sorted by day and time."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability slots",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MenteeAvailabilitySlotResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Mentee not found", content = @Content)
    })
    public ResponseEntity<List<MenteeAvailabilitySlotResponse>> getOwnSlots(Authentication authentication) {
        Long menteeId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(menteeAvailabilityService.getSlots(menteeId));
    }

    @PutMapping
    @Operation(
            summary = "Bulk update own availability",
            description = "Replaces all existing availability slots of the authenticated mentee with the provided set."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated availability slots",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MenteeAvailabilitySlotResponse.class)))),
            @ApiResponse(responseCode = "409", description = "Overlapping slots or invalid time range", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentee not found", content = @Content)
    })
    public ResponseEntity<List<MenteeAvailabilitySlotResponse>> bulkUpdate(
            @Valid @RequestBody BulkAvailabilityRequest request,
            Authentication authentication) {
        Long menteeId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(menteeAvailabilityService.bulkUpdate(menteeId, request.getSlots()));
    }

    @PostMapping
    @Operation(
            summary = "Add own availability slot",
            description = "Adds a single availability slot for the authenticated mentee."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Slot created",
                    content = @Content(schema = @Schema(implementation = MenteeAvailabilitySlotResponse.class))),
            @ApiResponse(responseCode = "409", description = "Overlapping slot or invalid time range", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentee not found", content = @Content)
    })
    public ResponseEntity<MenteeAvailabilitySlotResponse> addSlot(
            @Valid @RequestBody AvailabilitySlotRequest request,
            Authentication authentication) {
        Long menteeId = (Long) authentication.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED).body(menteeAvailabilityService.addSlot(menteeId, request));
    }

    @DeleteMapping("/{slotId}")
    @Operation(
            summary = "Remove own availability slot",
            description = "Removes a single availability slot owned by the authenticated mentee."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Slot removed"),
            @ApiResponse(responseCode = "404", description = "Slot not found", content = @Content)
    })
    public ResponseEntity<Void> removeSlot(@PathVariable Long slotId, Authentication authentication) {
        Long menteeId = (Long) authentication.getCredentials();
        menteeAvailabilityService.removeSlot(menteeId, slotId);
        return ResponseEntity.noContent().build();
    }
}
