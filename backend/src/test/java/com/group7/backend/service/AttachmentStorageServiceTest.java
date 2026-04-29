package com.group7.backend.service;

import com.group7.backend.dto.response.AttachmentUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentStorageServiceTest {

    @TempDir Path tempDir;

    private AttachmentStorageService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxFileSize", 5L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
        service.init();
    }

    @Test
    void storesValidPdf() throws Exception {
        byte[] body = pdfBody("Hello PDF");
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", body);

        AttachmentUploadResponse response = service.storeAttachment(file);

        assertThat(response.getUrl()).startsWith("http://localhost:8080/api/uploads/attachments/");
        assertThat(response.getUrl()).endsWith(".pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getSizeBytes()).isEqualTo(body.length);
        assertThat(Files.exists(tempDir.resolve(response.getFilename()))).isTrue();
    }

    @Test
    void storesValidJpeg() throws Exception {
        byte[] body = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", body);

        AttachmentUploadResponse response = service.storeAttachment(file);
        assertThat(response.getUrl()).endsWith(".jpg");
    }

    @Test
    void storesValidTextPlain() {
        byte[] body = "this is a plain text note\n".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", body);

        AttachmentUploadResponse response = service.storeAttachment(file);
        assertThat(response.getUrl()).endsWith(".txt");
    }

    @Test
    void rejectsDisallowedType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "exec.exe", "application/x-msdownload", new byte[]{0x4D, 0x5A});

        assertThatThrownBy(() -> service.storeAttachment(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid attachment type");
    }

    @Test
    void rejectsContentTypeMagicMismatch() {
        byte[] notReallyAPdf = "this is not a pdf".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", notReallyAPdf);

        assertThatThrownBy(() -> service.storeAttachment(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match declared type");
    }

    @Test
    void rejectsBinaryUploadedAsTextPlain() {
        byte[] binary = new byte[]{0x00, 0x01, 0x02, 0x03, (byte) 0xFE};
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.txt", "text/plain", binary);

        assertThatThrownBy(() -> service.storeAttachment(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.storeAttachment(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsOversizedFile() {
        ReflectionTestUtils.setField(service, "maxFileSize", 16L);
        byte[] tooLarge = pdfBody("xxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", tooLarge);

        assertThatThrownBy(() -> service.storeAttachment(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void filenamesAreUnique() {
        byte[] body = pdfBody("payload");
        MockMultipartFile a = new MockMultipartFile("file", "a.pdf", "application/pdf", body);
        MockMultipartFile b = new MockMultipartFile("file", "b.pdf", "application/pdf", body);

        String firstUrl = service.storeAttachment(a).getUrl();
        String secondUrl = service.storeAttachment(b).getUrl();
        assertThat(firstUrl).isNotEqualTo(secondUrl);
    }

    private static byte[] pdfBody(String trailing) {
        byte[] header = new byte[]{0x25, 0x50, 0x44, 0x46};  // %PDF
        byte[] tail = trailing.getBytes();
        byte[] result = new byte[header.length + tail.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(tail, 0, result, header.length, tail.length);
        return result;
    }
}
