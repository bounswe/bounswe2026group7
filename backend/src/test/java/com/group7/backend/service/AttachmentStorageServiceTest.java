package com.group7.backend.service;

import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.entity.Attachment;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.exception.RateLimitExceededException;
import com.group7.backend.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentStorageServiceTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @TempDir Path tempDir;

    @Mock private AttachmentRepository attachmentRepository;

    private AttachmentStorageService service;
    private User uploader;

    @BeforeEach
    void setUp() {
        AttachmentUrlBuilder urlBuilder = new AttachmentUrlBuilder("http://localhost:8080");
        service = new AttachmentStorageService(attachmentRepository, urlBuilder, Clock.systemUTC());
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxFileSize", 5L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "maxPerUserPerHour", 30);
        service.init();

        uploader = new Mentor();
        uploader.setId(7L);
        uploader.setFirstName("Mira");

        // The save returns the row with @PrePersist applied — mimic that here
        // so the service can read back id + filename. Lenient because the
        // validation-failure tests never reach save().
        lenient().when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── happy path ───────────────────────────────────────────────────────

    @Test
    void storesValidPdf_andPersistsAttachmentRow() {
        byte[] body = pdfBody("Hello PDF");
        MockMultipartFile file = pdfUpload(body);

        AttachmentSummary response = service.storeAttachment(file, uploader);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getDownloadUrl())
                .isEqualTo("http://localhost:8080/api/uploads/attachments/" + response.getId());
        assertThat(response.getFilename()).startsWith(response.getId().toString()).endsWith(".pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getSizeBytes()).isEqualTo(body.length);
        assertThat(Files.exists(tempDir.resolve(response.getFilename()))).isTrue();

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        Attachment persisted = captor.getValue();
        assertThat(persisted.getId()).isEqualTo(response.getId());
        assertThat(persisted.getUploader().getId()).isEqualTo(7L);
        assertThat(persisted.getContentType()).isEqualTo("application/pdf");
        assertThat(persisted.getSizeBytes()).isEqualTo(body.length);
    }

    @Test
    void idsAreUniquePerUpload() {
        AttachmentSummary first = service.storeAttachment(pdfUpload(pdfBody("a")), uploader);
        AttachmentSummary second = service.storeAttachment(pdfUpload(pdfBody("b")), uploader);
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getFilename()).isNotEqualTo(second.getFilename());
    }

    // ── magic-byte coverage for every accepted content type ──────────────

    @ParameterizedTest(name = "stores {1}")
    @MethodSource("validUploads")
    void storesValidUpload(String filename, String contentType, byte[] body, String expectedExt) {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, body);
        AttachmentSummary response = service.storeAttachment(file, uploader);
        assertThat(response.getFilename()).endsWith(expectedExt);
    }

    static Stream<Arguments> validUploads() {
        return Stream.of(
                Arguments.of("photo.jpg", "image/jpeg",
                        pad12(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}), ".jpg"),
                Arguments.of("p.png", "image/png",
                        pad12(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}), ".png"),
                Arguments.of("p.gif", "image/gif",
                        pad12(new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61}), ".gif"),
                Arguments.of("p.webp", "image/webp",
                        new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50}, ".webp"),
                Arguments.of("p.docx", DOCX_MIME,
                        pad12(new byte[]{0x50, 0x4B, 0x03, 0x04}), ".docx"),
                Arguments.of("notes.txt", "text/plain",
                        "this is a plain text note\n".getBytes(), ".txt")
        );
    }

    @ParameterizedTest(name = "rejects {1} with bad magic")
    @MethodSource("invalidMagicUploads")
    void rejectsBadMagic(String filename, String contentType, byte[] body) {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, body);
        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attachmentRepository, never()).save(any());
    }

    static Stream<Arguments> invalidMagicUploads() {
        byte[] zeros = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        return Stream.of(
                Arguments.of("p.jpg", "image/jpeg", zeros),
                Arguments.of("p.png", "image/png", zeros),
                Arguments.of("p.gif", "image/gif", zeros),
                Arguments.of("p.pdf", "application/pdf", zeros),
                Arguments.of("p.docx", DOCX_MIME, zeros),
                // RIFF prefix wrong, WEBP fourcc still right.
                Arguments.of("p.webp", "image/webp",
                        new byte[]{0x00, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50}),
                // RIFF prefix right, fourcc not WEBP.
                Arguments.of("p.webp", "image/webp",
                        new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x41, 0x56, 0x45})
        );
    }

    @ParameterizedTest(name = "rejects {1} shorter than its magic prefix")
    @MethodSource("magicHungryContentTypes")
    void rejectsFileShorterThanMagicWindow(String filename, String contentType) {
        byte[] tooShort = {0x01};
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, tooShort);
        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> magicHungryContentTypes() {
        return Stream.of(
                Arguments.of("p.jpg", "image/jpeg"),
                Arguments.of("p.png", "image/png"),
                Arguments.of("p.gif", "image/gif"),
                Arguments.of("p.webp", "image/webp"),
                Arguments.of("p.pdf", "application/pdf"),
                Arguments.of("p.docx", DOCX_MIME)
        );
    }

    @ParameterizedTest(name = "rejects WEBP with byte at offset {0} corrupted")
    @MethodSource("webpFourccCorruptions")
    void rejectsWebpWithEachIndividualLetterMismatch(int corruptedOffset, byte[] body) {
        MockMultipartFile file = new MockMultipartFile("file", "p.webp", "image/webp", body);
        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> webpFourccCorruptions() {
        // RIFF + 4-byte size + WEBP — flip one byte of the WEBP fourcc per row.
        return Stream.of(
                Arguments.of(8, bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x00, 0x45, 0x42, 0x50)),
                Arguments.of(9, bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x00, 0x42, 0x50)),
                Arguments.of(10, bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x00, 0x50)),
                Arguments.of(11, bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x00))
        );
    }

    // ── validateFile rejection paths ─────────────────────────────────────

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsNullFile() {
        assertThatThrownBy(() -> service.storeAttachment(null, uploader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsFileWithNullContentType() {
        MockMultipartFile noType = new MockMultipartFile("file", "x", null, new byte[]{1});
        assertThatThrownBy(() -> service.storeAttachment(noType, uploader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid attachment type");
    }

    @Test
    void rejectsDisallowedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "exec.exe", "application/x-msdownload", new byte[]{0x4D, 0x5A});
        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid attachment type");
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void rejectsContentTypeMagicMismatch() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "this is not a pdf".getBytes());
        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match declared type");
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void rejectsBinaryUploadedAsTextPlain() {
        byte[] binary = {0x00, 0x01, 0x02, 0x03, (byte) 0xFE};
        MockMultipartFile file = new MockMultipartFile("file", "fake.txt", "text/plain", binary);
        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTextPlainContainingDelByte() {
        // 0x7F (DEL) lies between printable ASCII (≤ 0x7E) and the high-byte
        // band (≥ 0x80), so it covers the b <= 0x7E false branch in
        // isProbablyText that no other test reaches.
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.txt", "text/plain", new byte[]{'o', 'k', 0x7F});
        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsTextPlainWithTabCrAndUtf8HighBytes() {
        // Exercises the three whitespace short-circuit paths inside
        // isProbablyText (tab, CR, LF) and the high-byte UTF-8 branch in a
        // single body. The validUploads parameterized run only sees
        // printable ASCII + LF, which would otherwise leave half of the
        // whitespace conditions unevaluated.
        byte[] body = {
                'h', 'i',
                0x09, 0x0A, 0x0D,
                (byte) 0xC3, (byte) 0xA9 // 'é' in UTF-8
        };
        MockMultipartFile file = new MockMultipartFile("file", "n.txt", "text/plain", body);
        AttachmentSummary response = service.storeAttachment(file, uploader);
        assertThat(response.getFilename()).endsWith(".txt");
    }

    @Test
    void rejectsOversizedFile() {
        ReflectionTestUtils.setField(service, "maxFileSize", 16L);
        byte[] tooLarge = pdfBody("xxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        MockMultipartFile file = pdfUpload(tooLarge);
        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    // ── quota ────────────────────────────────────────────────────────────

    @Test
    void rejectsUploadWhenPerUserHourlyQuotaIsExceeded() throws IOException {
        ReflectionTestUtils.setField(service, "maxPerUserPerHour", 3);
        when(attachmentRepository.countByUploaderSince(eq(uploader.getId()), any(OffsetDateTime.class)))
                .thenReturn(3L);

        MockMultipartFile file = pdfUpload(pdfBody("payload"));

        assertThatThrownBy(() -> service.storeAttachment(file, uploader))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("at most 3 files per hour");

        verify(attachmentRepository, never()).save(any());
        // The on-disk file must not have been created either — quota check
        // runs before any I/O.
        try (var stream = Files.list(tempDir)) {
            assertThat(stream.count()).isZero();
        }
    }

    @Test
    void permitsUploadWhenUnderQuota() {
        ReflectionTestUtils.setField(service, "maxPerUserPerHour", 3);
        when(attachmentRepository.countByUploaderSince(eq(uploader.getId()), any(OffsetDateTime.class)))
                .thenReturn(2L);

        AttachmentSummary response = service.storeAttachment(pdfUpload(pdfBody("payload")), uploader);
        assertThat(response.getId()).isNotNull();
    }

    // ── path-traversal defence + helper exposure ─────────────────────────

    @Test
    void resolveUploadTargetRejectsFilenameThatEscapesUploadDir() {
        // Production filenames are always "uuid + extension" so this branch is
        // defence in depth. Exercise it directly via the package-private helper.
        Path safe = (Path) ReflectionTestUtils.invokeMethod(
                service, "resolveUploadTarget", "safe.pdf");
        assertThat(safe).isNotNull();
        assertThat(safe.toString()).startsWith(tempDir.toAbsolutePath().toString());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "resolveUploadTarget", "../escape.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file path");
    }

    @Test
    void exposesUploadPath() {
        assertThat(service.getUploadPath()).isEqualTo(tempDir);
    }

    // ── rollback-cleanup hook (F1) ───────────────────────────────────────

    @Test
    void rollbackHookDeletesFileWhenSurroundingTransactionAborts() {
        Path written = withActiveSynchronization(() -> {
            AttachmentSummary summary = service.storeAttachment(pdfUpload(pdfBody("p")), uploader);
            Path file = tempDir.resolve(summary.getFilename());
            assertThat(file).exists();
            return file;
        }, TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(written).doesNotExist();
    }

    @Test
    void rollbackHookIsNoOpWhenSurroundingTransactionCommits() {
        Path written = withActiveSynchronization(() -> {
            AttachmentSummary summary = service.storeAttachment(pdfUpload(pdfBody("p")), uploader);
            return tempDir.resolve(summary.getFilename());
        }, TransactionSynchronization.STATUS_COMMITTED);

        assertThat(written).exists();
    }

    @Test
    void rollbackHookSurvivesFileAlreadyDeleted() throws IOException {
        // Models the "previous sweep already reclaimed the file" race: the
        // hook must complete normally even when deleteIfExists returns false.
        TransactionSynchronizationManager.initSynchronization();
        try {
            AttachmentSummary summary = service.storeAttachment(pdfUpload(pdfBody("p")), uploader);
            Files.delete(tempDir.resolve(summary.getFilename()));

            assertThatNoExceptionWhenAfterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private MockMultipartFile pdfUpload(byte[] body) {
        return new MockMultipartFile("file", "doc.pdf", "application/pdf", body);
    }

    private static byte[] pdfBody(String trailing) {
        byte[] header = {0x25, 0x50, 0x44, 0x46}; // "%PDF"
        return concat(header, trailing.getBytes());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * Pads the given header out to 12 bytes — the read window used by
     * {@code validateMagicBytes}. Lets fixture rows declare just the
     * meaningful prefix.
     */
    private static byte[] pad12(byte[] header) {
        byte[] body = new byte[12];
        System.arraycopy(header, 0, body, 0, Math.min(header.length, body.length));
        return body;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    /**
     * Drives Spring's {@link TransactionSynchronizationManager} in unit
     * scope: opens a synchronization, runs {@code body} (which registers
     * one), fires {@code afterCompletion(status)} on every registered
     * hook, then clears synchronization state. Returns {@code body}'s
     * result.
     */
    private <T> T withActiveSynchronization(java.util.function.Supplier<T> body, int status) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            T result = body.get();
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCompletion(status);
            }
            return result;
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static void assertThatNoExceptionWhenAfterCompletion(int status) {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(status);
        }
    }
}
