package com.group7.backend.controller;

import com.group7.backend.entity.Attachment;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AttachmentRepository;
import com.group7.backend.service.AttachmentStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentDownloadControllerTest {

    @Mock private AttachmentStorageService attachmentStorageService;
    @Mock private AttachmentRepository attachmentRepository;

    private AttachmentDownloadController controller;

    @TempDir Path uploadDir;

    private static final Long REQUESTER_ID = 7L;

    @BeforeEach
    void setUp() {
        controller = new AttachmentDownloadController(attachmentStorageService, attachmentRepository);
    }

    private Authentication authFor(Long userId) {
        return new UsernamePasswordAuthenticationToken("user@test.com", userId);
    }

    private Attachment attachmentRow(UUID id, String filename) {
        Mentor uploader = new Mentor();
        uploader.setId(99L);
        Attachment a = new Attachment();
        a.setId(id);
        a.setFilename(filename);
        a.setContentType("application/pdf");
        a.setSizeBytes(3);
        a.setUploader(uploader);
        return a;
    }

    @Test
    void download_returnsFile_whenRequesterIsParticipant() throws Exception {
        UUID id = UUID.randomUUID();
        String filename = id + ".pdf";
        Files.write(uploadDir.resolve(filename), new byte[]{1, 2, 3});

        when(attachmentRepository.findById(id)).thenReturn(Optional.of(attachmentRow(id, filename)));
        when(attachmentRepository.existsAttachmentVisibleToUser(eq(id), eq(REQUESTER_ID)))
                .thenReturn(true);
        when(attachmentStorageService.getUploadPath()).thenReturn(uploadDir);

        ResponseEntity<Resource> response = controller.download(id, authFor(REQUESTER_ID));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains(filename);
        Resource body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.exists()).isTrue();
        assertThat(body.contentLength()).isEqualTo(3);
    }

    @Test
    void download_throwsNotFound_whenRequesterIsNotParticipant() {
        // Uniform 404 with the "id is unknown" branch — the controller does
        // not leak the existence of an attachment id to a non-authorized
        // caller via the response code.
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id))
                .thenReturn(Optional.of(attachmentRow(id, id + ".pdf")));
        when(attachmentRepository.existsAttachmentVisibleToUser(eq(id), eq(REQUESTER_ID)))
                .thenReturn(false);

        assertThatThrownBy(() -> controller.download(id, authFor(REQUESTER_ID)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(attachmentStorageService, never()).getUploadPath();
    }

    @Test
    void download_throwsNotFound_whenAttachmentRowMissing() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.download(id, authFor(REQUESTER_ID)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(attachmentRepository, never()).existsAttachmentVisibleToUser(eq(id), eq(REQUESTER_ID));
    }

    @Test
    void download_throwsNotFound_whenFileMissingFromDisk() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id))
                .thenReturn(Optional.of(attachmentRow(id, id + ".pdf")));
        when(attachmentRepository.existsAttachmentVisibleToUser(eq(id), eq(REQUESTER_ID)))
                .thenReturn(true);
        when(attachmentStorageService.getUploadPath()).thenReturn(uploadDir);

        assertThatThrownBy(() -> controller.download(id, authFor(REQUESTER_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void download_throwsNotFound_whenStoredFilenameEscapesUploadDir() throws Exception {
        // Defence in depth: even if a malicious filename ever landed on a row,
        // the controller refuses to serve a file resolved outside uploadDir.
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id))
                .thenReturn(Optional.of(attachmentRow(id, "../../etc/passwd")));
        when(attachmentRepository.existsAttachmentVisibleToUser(eq(id), eq(REQUESTER_ID)))
                .thenReturn(true);
        when(attachmentStorageService.getUploadPath()).thenReturn(uploadDir);

        assertThatThrownBy(() -> controller.download(id, authFor(REQUESTER_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * {@code resolveMediaType} is a private helper exercised through the
     * download path for normal extensions. The fallback branches (no dot,
     * trailing dot, unknown extension) are not reachable through the
     * controller because the production filename is always
     * {@code uuid + known-extension}. We exercise them directly here so the
     * defence-in-depth defaults are covered.
     */
    @Nested
    class ResolveMediaType {
        @Test
        void unknownExtensionFallsBackToOctetStream() {
            assertThat(invokeResolveMediaType("foo.unknown"))
                    .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        }

        @Test
        void filenameWithoutDotFallsBackToOctetStream() {
            assertThat(invokeResolveMediaType("noext"))
                    .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        }

        @Test
        void filenameWithTrailingDotFallsBackToOctetStream() {
            assertThat(invokeResolveMediaType("trailing."))
                    .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        }

        @Test
        void uppercaseExtensionResolvesNormally() {
            assertThat(invokeResolveMediaType("MIXED.PDF"))
                    .isEqualTo(MediaType.APPLICATION_PDF);
        }

        private MediaType invokeResolveMediaType(String filename) {
            try {
                Method m = AttachmentDownloadController.class
                        .getDeclaredMethod("resolveMediaType", String.class);
                m.setAccessible(true);
                return (MediaType) m.invoke(null, filename);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }
}
