package com.group7.backend.controller;

import com.group7.backend.entity.Attachment;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AttachmentRepository;
import com.group7.backend.service.AttachmentStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Authenticated download endpoint for chat attachments. Replaces the previous
 * public static-resource handler at the same URL prefix: requests must carry
 * a valid JWT and the caller must be a participant of any conversation that
 * contains a message referencing the requested attachment.
 *
 * <p>Path traversal is structurally impossible — the path variable is a UUID
 * (Spring rejects malformed values with 404 before this method runs) and the
 * on-disk filename is derived from the persisted attachment row, never from
 * client input. The {@code startsWith(uploadDir)} check is a second line of
 * defence in case the upload directory ever moves at runtime.
 */
@RestController
@RequestMapping("/api/uploads/attachments")
@Tag(name = "Message Attachments", description = "Authenticated download of chat attachments")
public class AttachmentDownloadController {

    private static final Map<String, MediaType> EXTENSION_TO_TYPE = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.parseMediaType("image/webp"),
            "pdf", MediaType.APPLICATION_PDF,
            "docx", MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "txt", MediaType.TEXT_PLAIN
    );

    private final AttachmentStorageService attachmentStorageService;
    private final AttachmentRepository attachmentRepository;

    public AttachmentDownloadController(AttachmentStorageService attachmentStorageService,
                                        AttachmentRepository attachmentRepository) {
        this.attachmentStorageService = attachmentStorageService;
        this.attachmentRepository = attachmentRepository;
    }

    @GetMapping("/{attachmentId}")
    @Operation(summary = "Download a chat attachment",
            description = "Returns the bytes of an attachment uploaded by a participant of a "
                    + "conversation. The caller must be authenticated and a participant of any "
                    + "conversation containing a message that references this attachment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Attachment bytes"),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Attachment not found, or the requester is not a participant of any conversation referencing it", content = @Content)
    })
    public ResponseEntity<Resource> download(@PathVariable UUID attachmentId,
                                             Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();

        // Both "id is unknown" and "id exists but the requester is not a
        // participant of any conversation referencing it" return 404 — making
        // the response uniform in shape so the controller does not leak the
        // existence of an attachment id to a non-authorized caller.
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

        if (!attachmentRepository.existsAttachmentVisibleToUser(attachmentId, requesterId)) {
            throw new ResourceNotFoundException("Attachment not found");
        }

        Path uploadDir = attachmentStorageService.getUploadPath().toAbsolutePath().normalize();
        Path filePath = uploadDir.resolve(attachment.getFilename()).normalize();
        if (!filePath.startsWith(uploadDir)) {
            throw new ResourceNotFoundException("Attachment not found");
        }
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists() || !resource.isReadable()) {
            throw new ResourceNotFoundException("Attachment not found");
        }

        return ResponseEntity.ok()
                .contentType(resolveMediaType(attachment.getFilename()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                // Defense-in-depth: even if the magic-byte check at upload time were
                // ever bypassed by a polyglot file, the browser must not sniff the
                // body and reinterpret it as HTML/JS.
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + attachment.getFilename() + "\"")
                .body(resource);
    }

    private static MediaType resolveMediaType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXTENSION_TO_TYPE.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
    }
}
