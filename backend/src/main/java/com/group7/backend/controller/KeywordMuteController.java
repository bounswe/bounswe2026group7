package com.group7.backend.controller;

import com.group7.backend.dto.request.KeywordMuteRequest;
import com.group7.backend.dto.response.KeywordMuteResponse;
import com.group7.backend.service.UserKeywordMuteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Per-user keyword-mute resource. Authenticated user manages their own
 * mutes via this endpoint; the mutes are applied transparently by every
 * feed read path (for-you / following / per-author timeline / search).
 */
@RestController
@RequestMapping("/api/users/me/keyword-mutes")
@Tag(name = "Feed Keyword Mutes",
        description = "Per-user mutes that hide posts whose body contains the muted substring.")
public class KeywordMuteController {

    private final UserKeywordMuteService service;

    public KeywordMuteController(UserKeywordMuteService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's muted keywords")
    public ResponseEntity<List<KeywordMuteResponse>> list(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(service.list(userId));
    }

    @PostMapping
    @Operation(summary = "Add a keyword mute")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Blank, oversize, illegal characters, or cap reached", content = @Content),
            @ApiResponse(responseCode = "409", description = "Keyword already muted", content = @Content)
    })
    public ResponseEntity<KeywordMuteResponse> add(
            @Valid @RequestBody KeywordMuteRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        KeywordMuteResponse saved = service.add(userId, request.keyword());
        URI location = URI.create("/api/users/me/keyword-mutes/" + saved.id());
        return ResponseEntity.created(location).body(saved);
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Remove a keyword mute by id")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "Mute id does not exist for this user", content = @Content)
    })
    public ResponseEntity<Void> remove(
            @Parameter(description = "Mute id") @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
