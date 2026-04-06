package com.group7.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FileStorageServiceTest {

    private FileStorageService service;

    @TempDir
    Path tempDir;

    // Minimal valid JPEG (FF D8 FF)
    private static final byte[] VALID_JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F', 0x00
    };

    // Minimal valid PNG (89 50 4E 47)
    private static final byte[] VALID_PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D
    };

    // Minimal valid GIF (47 49 46 38)
    private static final byte[] VALID_GIF = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00,
            0x01, 0x00, 0x00, 0x00
    };

    // Valid RIFF header but NOT WebP (no WEBP marker at bytes 8-11)
    private static final byte[] RIFF_NOT_WEBP = {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00,
            'A', 'V', 'I', ' '
    };

    // Valid WebP
    private static final byte[] VALID_WEBP = {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00,
            'W', 'E', 'B', 'P'
    };

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxFileSize", 5242880L); // 5MB
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
        service.init();
    }

    // ── storeFile success ──

    @Test
    void storeFile_validJpeg_returnsUrlAndCreatesFile() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", VALID_JPEG);
        String url = service.storeFile(file);

        assertThat(url).startsWith("http://localhost:8080/api/uploads/photos/");
        assertThat(url).endsWith(".jpg");

        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();
    }

    @Test
    void storeFile_validPng_returnsUrlWithPngExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", VALID_PNG);
        String url = service.storeFile(file);
        assertThat(url).endsWith(".png");
    }

    @Test
    void storeFile_validGif_returnsUrlWithGifExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.gif", "image/gif", VALID_GIF);
        String url = service.storeFile(file);
        assertThat(url).endsWith(".gif");
    }

    @Test
    void storeFile_validWebp_returnsUrlWithWebpExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.webp", "image/webp", VALID_WEBP);
        String url = service.storeFile(file);
        assertThat(url).endsWith(".webp");
    }

    @Test
    void storeFile_generatesUniqueFilenames() {
        MockMultipartFile file1 = new MockMultipartFile("file", "a.jpg", "image/jpeg", VALID_JPEG);
        MockMultipartFile file2 = new MockMultipartFile("file", "a.jpg", "image/jpeg", VALID_JPEG);
        String url1 = service.storeFile(file1);
        String url2 = service.storeFile(file2);
        assertThat(url1).isNotEqualTo(url2);
    }

    @Test
    void storeFile_ignoresOriginalFilename() {
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd", "image/jpeg", VALID_JPEG);
        String url = service.storeFile(file);
        // Filename should be UUID, not the original
        assertThat(url).doesNotContain("passwd");
        assertThat(url).doesNotContain("..");
    }

    // ── storeFile validation failures ──

    @Test
    void storeFile_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> service.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void storeFile_nullFile_throws() {
        assertThatThrownBy(() -> service.storeFile(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storeFile_disallowedContentType_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});
        assertThatThrownBy(() -> service.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void storeFile_textFile_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "hack.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> service.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void storeFile_spoofedContentType_jpegWithWrongBytes_throws() {
        byte[] notJpeg = {0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", notJpeg);
        assertThatThrownBy(() -> service.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void storeFile_spoofedContentType_pngClaimedAsJpeg_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", VALID_PNG);
        assertThatThrownBy(() -> service.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void storeFile_riffAviClaimedAsWebp_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "video.webp", "image/webp", RIFF_NOT_WEBP);
        assertThatThrownBy(() -> service.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void storeFile_exceedsMaxSize_throws() {
        ReflectionTestUtils.setField(service, "maxFileSize", 10L); // 10 bytes
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", VALID_JPEG);
        assertThatThrownBy(() -> service.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void storeFile_tooSmallToValidate_throws() {
        byte[] tiny = {(byte) 0xFF, (byte) 0xD8}; // only 2 bytes, JPEG needs 3
        MockMultipartFile file = new MockMultipartFile("file", "tiny.jpg", "image/jpeg", tiny);
        assertThatThrownBy(() -> service.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too small");
    }

    // ── deleteFile ──

    @Test
    void deleteFile_existingFile_removesIt() throws IOException {
        // Store a file first
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", VALID_JPEG);
        String url = service.storeFile(file);

        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();

        service.deleteFile(url);
        assertThat(Files.exists(tempDir.resolve(filename))).isFalse();
    }

    @Test
    void deleteFile_nonExistentFile_doesNotThrow() {
        assertThatCode(() -> service.deleteFile("http://localhost:8080/api/uploads/photos/nonexistent.jpg"))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteFile_nullUrl_doesNotThrow() {
        assertThatCode(() -> service.deleteFile(null)).doesNotThrowAnyException();
    }

    @Test
    void deleteFile_externalUrl_doesNothing() {
        assertThatCode(() -> service.deleteFile("https://external.com/photo.jpg"))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteFile_pathTraversalAttempt_doesNothing() {
        assertThatCode(() -> service.deleteFile("http://localhost:8080/api/uploads/photos/../../etc/passwd"))
                .doesNotThrowAnyException();
    }
}
