package com.group7.backend.service;

import com.group7.backend.dto.response.AttachmentUploadResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores chat attachments under a directory served at
 * {@code /api/uploads/attachments/**}. Mirrors {@link FileStorageService}'s
 * validation pattern (magic-byte check, UUID filenames, path-traversal defense)
 * but accepts both image and document MIME types per issue #245.
 */
@Service
public class AttachmentStorageService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    private static final Map<String, String> CONTENT_TYPE_TO_EXT = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "application/pdf", ".pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx",
            "text/plain", ".txt"
    );

    // Magic bytes used to verify the declared content type matches the file body.
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] GIF_MAGIC = {0x47, 0x49, 0x46, 0x38};
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};       // "%PDF"
    private static final byte[] DOCX_MAGIC = {0x50, 0x4B, 0x03, 0x04};      // ZIP container

    @Value("${app.upload.attachments-dir:/app/uploads/attachments}")
    private String uploadDir;

    @Value("${app.upload.max-file-size:5242880}")
    private long maxFileSize;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

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
     * Validates and persists an uploaded attachment, returning a public URL
     * plus metadata. Throws {@link IllegalArgumentException} on validation
     * failure (which {@code GlobalExceptionHandler} maps to {@code 400}).
     */
    public AttachmentUploadResponse storeAttachment(MultipartFile file) {
        validateFile(file);

        String contentType = file.getContentType();
        String extension = CONTENT_TYPE_TO_EXT.getOrDefault(contentType, "");
        String filename = UUID.randomUUID() + extension;

        Path uploadPath = Paths.get(uploadDir).normalize().toAbsolutePath();
        Path targetPath = uploadPath.resolve(filename).normalize();
        if (!targetPath.startsWith(uploadPath)) {
            throw new IllegalArgumentException("Invalid file path");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored attachment: {} ({} bytes)", filename, file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store attachment", e);
        }

        String url = baseUrl + "/api/uploads/attachments/" + filename;
        return AttachmentUploadResponse.of(url, filename, contentType, file.getSize());
    }

    Path getUploadPath() {
        return Paths.get(uploadDir);
    }

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
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        bytesRead >= DOCX_MAGIC.length && startsWith(header, DOCX_MAGIC);
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
