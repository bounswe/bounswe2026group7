package com.group7.backend.service;

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

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final Map<String, String> CONTENT_TYPE_TO_EXT = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    // Magic bytes for image format validation
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] GIF_MAGIC = {0x47, 0x49, 0x46, 0x38};
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46};

    @Value("${app.upload.dir:/app/uploads/photos}")
    private String uploadDir;

    @Value("${app.upload.max-file-size:5242880}")
    private long maxFileSize; // 5MB default

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(uploadDir);
            Files.createDirectories(path);
            log.info("Upload directory ready: {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadDir, e);
        }
    }

    /**
     * Stores an uploaded image file after validation.
     *
     * @return the public URL to access the stored file
     * @throws IllegalArgumentException if the file fails validation
     */
    public String storeFile(MultipartFile file) {
        validateFile(file);

        String contentType = file.getContentType();
        String extension = CONTENT_TYPE_TO_EXT.getOrDefault(contentType, ".jpg");
        String filename = UUID.randomUUID() + extension;
        Path uploadPath = Paths.get(uploadDir).normalize().toAbsolutePath();
        Path targetPath = uploadPath.resolve(filename).normalize();

        // Prevent path traversal (defensive — UUID shouldn't contain separators, but verify)
        if (!targetPath.startsWith(uploadPath)) {
            throw new IllegalArgumentException("Invalid file path");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file: {}", filename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }

        return baseUrl + "/api/uploads/photos/" + filename;
    }

    /**
     * Deletes a previously stored file by its public URL.
     * Silently ignores if the file doesn't exist or URL is not a local upload.
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("/api/uploads/photos/")) {
            return;
        }
        String filename = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);

        // Sanitize: filename must be a UUID with extension, no path separators
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return;
        }

        try {
            Path uploadPath = Paths.get(uploadDir).normalize().toAbsolutePath();
            Path filePath = uploadPath.resolve(filename).normalize();

            // Defense in depth: ensure resolved path is within upload directory
            if (!filePath.startsWith(uploadPath)) {
                log.warn("Path traversal attempt in deleteFile: {}", filename);
                return;
            }

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted file: {}", filename);
            }
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", filename, e);
        }
    }

    /**
     * Returns the filesystem path for the uploads directory.
     */
    public Path getUploadPath() {
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
                    "Invalid file type. Allowed: JPEG, PNG, GIF, WebP");
        }

        // Validate magic bytes to prevent content-type spoofing
        validateMagicBytes(file, contentType);
    }

    private void validateMagicBytes(MultipartFile file, String contentType) {
        try {
            byte[] header = new byte[12];
            int bytesRead;
            try (InputStream is = file.getInputStream()) {
                bytesRead = is.read(header);
                if (bytesRead < 3) {
                    throw new IllegalArgumentException("File is too small to be a valid image");
                }
            }

            boolean valid = switch (contentType) {
                case "image/jpeg" -> startsWith(header, JPEG_MAGIC);
                case "image/png" -> bytesRead >= 4 && startsWith(header, PNG_MAGIC);
                case "image/gif" -> bytesRead >= 4 && startsWith(header, GIF_MAGIC);
                case "image/webp" -> bytesRead >= 12 && startsWith(header, WEBP_RIFF)
                        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
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

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
