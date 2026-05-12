package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.dto.request.AdminBanRequest;
import com.group7.backend.dto.request.AdminUserBanStatusFilter;
import com.group7.backend.dto.request.AdminUserRoleFilter;
import com.group7.backend.dto.response.AdminUserDetailResponse;
import com.group7.backend.dto.response.AdminUserListItem;
import com.group7.backend.dto.response.BanResponse;
import com.group7.backend.service.AdminUserQueryService;
import com.group7.backend.service.BanService;
import com.group7.backend.service.SpamDetectionService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only operations")
public class AdminController {

    private final BanService banService;
    private final SpamDetectionService spamDetectionService;
    private final AdminUserQueryService adminUserQueryService;

    public AdminController(BanService banService,
                           SpamDetectionService spamDetectionService,
                           AdminUserQueryService adminUserQueryService) {
        this.banService = banService;
        this.spamDetectionService = spamDetectionService;
        this.adminUserQueryService = adminUserQueryService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get authenticated admin context")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "userId", authentication.getCredentials(),
                "email", authentication.getPrincipal(),
                "role", "ADMIN"
        ));
    }

    @GetMapping("/users")
    @Operation(summary = "List users for the admin panel (#569)",
            description = "Paginated listing across all roles with optional role / "
                    + "ban-status / keyword filters. Page size is clamped to 100. "
                    + "Ordered newest user first (createdAt desc, id desc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of users"),
            @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content)
    })
    public ResponseEntity<Page<AdminUserListItem>> listUsers(
            @Parameter(description = "Restrict to a single role. Omit for all roles.")
            @RequestParam(required = false) AdminUserRoleFilter role,
            @Parameter(description = "ACTIVE = users with a current ban "
                    + "(lifted_at IS NULL AND expires_at > now); "
                    + "NONE = users without one. Omit to include both.")
            @RequestParam(required = false) AdminUserBanStatusFilter banStatus,
            @Parameter(description = "Keyword matched against firstName / lastName / "
                    + "email (case-insensitive). Inputs shorter than 3 alphanumerics "
                    + "are ignored.")
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(
                adminUserQueryService.listUsers(role, banStatus, q, pageable));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get a user's admin drill-down view (#569)",
            description = "Returns the role-specific profile (Mentor / Mentee / Admin) "
                    + "plus full ban history (newest first) and the spam-bot flag. "
                    + "Admin-on-admin reads are intentional for this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User detail"),
            @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(
            @Parameter(description = "Target user id") @PathVariable Long id) {
        return ResponseEntity.ok(adminUserQueryService.getUserDetail(id));
    }

    @PostMapping("/users/{userId}/ban")
    @Operation(summary = "Ban a user (#280)",
            description = "Imposes a ban with the given reason and duration. Stacks on top "
                    + "of any existing active ban; the row with the latest expiresAt wins "
                    + "the ban-active gate. Banned users receive 403 BANNED_UNTIL on login.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ban created",
                    content = @Content(schema = @Schema(implementation = BanResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid duration", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<BanResponse> banUser(
            @Parameter(description = "Target user id") @PathVariable Long userId,
            @Valid @RequestBody AdminBanRequest body,
            Authentication authentication) {
        Long adminId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(BanResponse.from(
                banService.imposeAdminBan(userId, adminId, body.getReason(), body.getDurationHours())));
    }

    @PostMapping("/users/{userId}/unban")
    @Operation(summary = "Unban a user (#280)",
            description = "Lifts the user's currently-active ban. Returns 404 if no active "
                    + "ban exists so the admin gets a clear signal rather than a silent no-op.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active ban lifted",
                    content = @Content(schema = @Schema(implementation = BanResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "No active ban for user",
                    content = @Content)
    })
    public ResponseEntity<BanResponse> unbanUser(
            @Parameter(description = "Target user id") @PathVariable Long userId,
            Authentication authentication) {
        Long adminId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(BanResponse.from(banService.unbanUser(userId, adminId)));
    }

    @PostMapping("/users/{userId}/clear-bot-flag")
    @Operation(summary = "Clear suspected-bot flag and lift the related auto-ban (#345)",
            description = "Resets isSuspectedBot to false, clears suspectedAt, and idempotently"
                    + " lifts any active auto-ban so the user can log in again.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Flag cleared", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<Void> clearBotFlag(
            @Parameter(description = "Target user id") @PathVariable Long userId,
            Authentication authentication) {
        Long adminId = (Long) authentication.getCredentials();
        spamDetectionService.clearFlag(userId, adminId);
        return ResponseEntity.noContent().build();
    }
}
