package com.group7.backend.service;

import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.entity.Attachment;
import com.group7.backend.entity.User;
import com.group7.backend.exception.RateLimitExceededException;
import com.group7.backend.repository.AttachmentRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores chat attachments under a directory served at
 * {@code /api/uploads/attachments/**}. Each successful upload produces both
 * a row in {@code attachments} and a file on disk; either both land or
 * neither does (the file is reclaimed by a transaction synchronization if
 * the row's transaction rolls back).
 *
 * <p>Validation pattern mirrors {@link FileStorageService} (magic-byte check,
 * UUID filenames, path-traversal defence) but accepts both image and
 * document MIME types per issue #245.
 */
@Service
public class AttachmentStorageService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorageService.class);

    // The DOCX MIME literal is verbose and reused below; lift it to a constant
    // so the allowlist + extension table + magic-byte switch all reference one
    // string.
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", DOCX_MIME, "text/plain"
    );

    private static final Map<String, String> CONTENT_TYPE_TO_EXT = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "application/pdf", ".pdf",
            DOCX_MIME, ".docx",
            "text/plain", ".txt"
    );

    // Magic bytes used to verify the declared content type matches the file body.
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] GIF_MAGIC = {0x47, 0x49, 0x46, 0x38};
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};       // "%PDF"
    private static final byte[] DOCX_MAGIC = {0x50, 0x4B, 0x03, 0x04};      // ZIP container

    private final AttachmentRepository attachmentRepository;
    private final AttachmentUrlBuilder urlBuilder;
    private final Clock clock;

    @Value("${app.upload.attachments-dir:/app/uploads/attachments}")
    private String uploadDir;

    @Value("${app.upload.max-file-size:5242880}")
    private long maxFileSize;

    @Value("${app.upload.max-per-user-per-hour:30}")
    private int maxPerUserPerHour;

    public AttachmentStorageService(AttachmentRepository attachmentRepository,
                                    AttachmentUrlBuilder urlBuilder,
                                    Clock clock) {
        this.attachmentRepository = attachmentRepository;
        this.urlBuilder = urlBuilder;
        this.clock = clock;
    }

    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(uploadDir);
            Files.createDirectories(path);
            log.info("Attachment upload directory ready: {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not create attachment directory: " + uploadDir, e);
        }
    }

    /**
     * Validates and persists an uploaded attachment, returning the
     * {@link AttachmentSummary} for the created row. Throws
     * {@link IllegalArgumentException} on validation failure (mapped to 400)
     * or {@link RateLimitExceededException} when the per-user hourly quota is
     * exhausted (mapped to 429).
     */
    @Transactional
    public AttachmentSummary storeAttachment(MultipartFile file, User uploader) {
        validateFile(file);
        enforcePerUserHourlyQuota(uploader);

        UUID id = UUID.randomUUID();
        String filename = id + extensionFor(file.getContentType());
        Path target = resolveUploadTarget(filename);

        writeFileWithRollbackCleanup(file, target);
        Attachment saved = persistRow(id, filename, file, uploader);

        return AttachmentSummary.of(saved, urlBuilder.downloadUrl(saved.getId()));
    }

    /**
     * Resolves the configured upload directory. Used by
     * {@code AttachmentDownloadController} to stream files back to authenticated
     * participants of the owning conversation.
     */
    public Path getUploadPath() {
        return Paths.get(uploadDir);
    }

    // ── storeAttachment helpers ─────────────────────────────────────────────

    /**
     * Bounds disk-fill DoS until a global rate-limit middleware lands. The
     * count is over the rolling hour preceding {@code now()}.
     */
    private void enforcePerUserHourlyQuota(User uploader) {
        OffsetDateTime since = OffsetDateTime.now(clock).minus(Duration.ofHours(1));
        long recent = attachmentRepository.countByUploaderSince(uploader.getId(), since);
        if (recent >= maxPerUserPerHour) {
            throw new RateLimitExceededException(
                    "Upload quota exceeded — you may upload at most "
                            + maxPerUserPerHour + " files per hour");
        }
    }

    private static String extensionFor(String contentType) {
        return CONTENT_TYPE_TO_EXT.getOrDefault(contentType, "");
    }

    private Path resolveUploadTarget(String filename) {
        Path uploadPath = Paths.get(uploadDir).normalize().toAbsolutePath();
        Path target = uploadPath.resolve(filename).normalize();
        if (!target.startsWith(uploadPath)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return target;
    }

    /**
     * Writes the multipart body to {@code target}, then registers a
     * transaction synchronization that deletes the file if the surrounding
     * transaction does not commit. This pairs the file's lifetime with the
     * row's lifetime: a rollback after this method returns reclaims both.
     *
     * <p>If {@link Files#copy} fails mid-write a partial file may remain on
     * disk; the orphan scheduler keys off DB rows and would never reach it,
     * so we attempt an explicit cleanup before re-raising. Failures of that
     * cleanup are best-effort logged — we do not mask the original copy
     * exception by throwing from the cleanup path.
     */
    private void writeFileWithRollbackCleanup(MultipartFile file, Path target) {
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanup) {
                log.warn("Failed to remove partial attachment file after copy failure: {}",
                        target, cleanup);
            }
            throw new RuntimeException("Failed to store attachment", e);
        }
        log.info("Stored attachment file: {} ({} bytes)", target.getFileName(), file.getSize());
        registerRollbackFileCleanup(target);
    }

    private static void registerRollbackFileCleanup(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    if (Files.deleteIfExists(path)) {
                        log.info("Reclaimed orphan attachment file after rollback: {}", path);
                    }
                } catch (IOException e) {
                    log.warn("Failed to reclaim orphan attachment file after rollback: {}", path, e);
                }
            }
        });
    }

    private Attachment persistRow(UUID id, String filename, MultipartFile file, User uploader) {
        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setFilename(filename);
        attachment.setContentType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setUploader(uploader);
        Attachment saved = attachmentRepository.save(attachment);
        log.info("Persisted attachment row: id={}, uploaderId={}", saved.getId(), uploader.getId());
        return saved;
    }

    // ── upload validation ───────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    "File size exceeds maximum of " + (maxFileSize / 1024 / 1024) + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid attachment type. Allowed: jpeg, png, gif, webp, pdf, docx, txt");
        }
        validateMagicBytes(file, contentType);
    }

    private void validateMagicBytes(MultipartFile file, String contentType) {
        try {
            byte[] header = new byte[12];
            int bytesRead;
            try (InputStream is = file.getInputStream()) {
                bytesRead = is.read(header);
                if (bytesRead < 0) {
                    throw new IllegalArgumentException("File is empty");
                }
            }

            boolean valid = switch (contentType) {
                case "image/jpeg" -> bytesRead >= JPEG_MAGIC.length && startsWith(header, JPEG_MAGIC);
                case "image/png"  -> bytesRead >= PNG_MAGIC.length && startsWith(header, PNG_MAGIC);
                case "image/gif"  -> bytesRead >= GIF_MAGIC.length && startsWith(header, GIF_MAGIC);
                case "image/webp" -> bytesRead >= 12 && startsWith(header, WEBP_RIFF)
                        && header[8] == 'W' && header[9] == 'E'
                        && header[10] == 'B' && header[11] == 'P';
                case "application/pdf" -> bytesRead >= PDF_MAGIC.length && startsWith(header, PDF_MAGIC);
                case DOCX_MIME -> bytesRead >= DOCX_MAGIC.length && startsWith(header, DOCX_MAGIC);
                case "text/plain" -> isProbablyText(header, bytesRead);
                default -> false;
            };

            if (!valid) {
                throw new IllegalArgumentException(
                        "File content does not match declared type: " + contentType);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file for validation", e);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Plain-text validation: reject if any byte in the prefix is a control
     * character outside the standard whitespace set (tab, LF, CR).
     * Conservative — rejects most binary files mislabeled as text/plain.
     */
    private static boolean isProbablyText(byte[] header, int bytesRead) {
        for (int i = 0; i < bytesRead; i++) {
            byte b = header[i];
            if (b == 0x09 || b == 0x0A || b == 0x0D) continue;     // tab/LF/CR
            if (b >= 0x20 && b <= 0x7E) continue;                  // printable ASCII
            if ((b & 0xFF) >= 0x80) continue;                      // UTF-8 high bytes
            return false;
        }
        return bytesRead > 0;
    }
}
