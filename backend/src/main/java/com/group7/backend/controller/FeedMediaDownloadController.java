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
 * Authenticated download endpoint for feed-post image attachments (#485).
 *
 * <p>Sibling to {@link AttachmentDownloadController}; both serve files from
 * the same upload directory, but the gate is intentionally different:
 * <ul>
 *   <li>The chat controller verifies the caller is a participant of a
 *       conversation referencing the attachment ({@code existsAttachmentVisibleToUser}).</li>
 *   <li>This controller only verifies the attachment is referenced by some
 *       feed post's junction row ({@code existsAsFeedAttachment}). Feed
 *       images are public to any authenticated viewer — there is no
 *       per-user ACL.</li>
 * </ul>
 *
 * <p>{@code Authentication} is not a parameter because nothing here uses the
 * caller's identity beyond Spring Security's URL-pattern enforcement
 * ({@code /api/uploads/feed-media/**} falls through to
 * {@code .anyRequest().authenticated()}). Adding the parameter would be
 * misleading. Anonymous callers receive 401 from the security filter chain
 * before reaching this handler.
 *
 * <p>Path traversal is structurally impossible — the path variable is a
 * UUID (Spring rejects malformed values with 404 before this method runs)
 * and the on-disk filename is derived from the persisted attachment row,
 * never from client input. The {@code startsWith(uploadDir)} check is a
 * second line of defence in case the upload directory ever moves at
 * runtime.
 *
 * <p>Cache header differs from chat: {@code public} (vs the chat path's
 * {@code private}) reflects that feed images are visible to any
 * authenticated user, so a shared HTTP cache is acceptable.
 *
 * <p>The map of allowed extensions is the image subset of the chat
 * controller's table — non-image content types are rejected at post
 * create / update time in {@code FeedPostService}, but if a file with a
 * surprising extension somehow lands here we fall through to
 * {@code application/octet-stream} (forcing the browser to treat it as a
 * download, not render it inline).
 */
@RestController
@RequestMapping("/api/uploads/feed-media")
@Tag(name = "Feed Media",
        description = "Authenticated download of images attached to feed posts (#485)")
public class FeedMediaDownloadController {

    private static final Map<String, MediaType> EXTENSION_TO_IMAGE_TYPE = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.parseMediaType("image/webp"));

    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageService attachmentStorageService;

    public FeedMediaDownloadController(AttachmentRepository attachmentRepository,
                                       AttachmentStorageService attachmentStorageService) {
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorageService = attachmentStorageService;
    }

    @GetMapping("/{attachmentId}")
    @Operation(summary = "Download a feed-post image",
            description = "Returns the bytes of an image attached to a feed post. Any "
                    + "authenticated user may download. Attachments not referenced by any "
                    + "feed post (chat-only uploads, orphan-window uploads) are rejected with "
                    + "404 — the response shape is uniform so callers cannot probe whether "
                    + "the id exists in another context.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image bytes"),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Attachment not found, or not referenced by any feed post",
                    content = @Content)
    })
    public ResponseEntity<Resource> download(@PathVariable UUID attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

        if (!attachmentRepository.existsAsFeedAttachment(attachmentId)) {
            // Uniform 404 — callers cannot use this path to probe whether an
            // id exists as a chat attachment.
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
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
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
        return EXTENSION_TO_IMAGE_TYPE.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
    }
}
