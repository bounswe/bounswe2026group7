package com.group7.backend.controller;

import com.group7.backend.dto.response.BanResponse;
import com.group7.backend.service.BanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin override surface for the auto-ban system (#134, req 2.2.4).
 * All endpoints require {@code ROLE_ADMIN}; non-admin callers receive 403
 * via {@link org.springframework.security.access.prepost.PreAuthorize}.
 */
@RestController
@RequestMapping("/api/admin/bans")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Bans", description = "Admin override and audit for the auto-ban system (#134).")
public class BanAdminController {

    private final BanService banService;

    public BanAdminController(BanService banService) {
        this.banService = banService;
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "List a user's bans (newest first)",
            description = "Returns the full audit history of bans for the given user, "
                    + "including active, lifted, and expired entries.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ban history",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = BanResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content)
    })
    public ResponseEntity<List<BanResponse>> listForUser(
            @Parameter(description = "User id whose bans to list") @PathVariable Long userId) {
        List<BanResponse> body = banService.listBansForUser(userId).stream()
                .map(BanResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{banId}")
    @Operation(summary = "Lift a ban",
            description = "Marks the ban as lifted by the calling admin and notifies the user. "
                    + "Idempotent: lifting an already-lifted ban returns the same row.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ban lifted",
                    content = @Content(schema = @Schema(implementation = BanResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "Ban not found", content = @Content)
    })
    public ResponseEntity<BanResponse> liftBan(
            @Parameter(description = "Ban id to lift") @PathVariable Long banId,
            Authentication authentication) {
        Long adminId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(BanResponse.from(banService.liftBan(banId, adminId)));
    }
}
