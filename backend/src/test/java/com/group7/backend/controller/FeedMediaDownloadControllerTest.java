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

/**
 * Unit slice for {@link FeedMediaDownloadController} (#485). Mirrors the
 * shape of {@code AttachmentDownloadControllerTest} but exercises the
 * feed-scoped ACL ({@code existsAsFeedAttachment}) and asserts the
 * publicly-cacheable response shape ({@code Cache-Control: public}).
 *
 * <p>Auth is enforced at the Spring Security URL-pattern layer, not in the
 * controller body, so anonymous-call behaviour is verified at the
 * integration level rather than here.
 */
@ExtendWith(MockitoExtension.class)
class FeedMediaDownloadControllerTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private AttachmentStorageService attachmentStorageService;

    private FeedMediaDownloadController controller;

    @TempDir Path uploadDir;

    @BeforeEach
    void setUp() {
        controller = new FeedMediaDownloadController(attachmentRepository, attachmentStorageService);
    }

    private Attachment imageRow(UUID id, String filename, String contentType) {
        Mentor uploader = new Mentor();
        uploader.setId(99L);
        Attachment a = new Attachment();
        a.setId(id);
        a.setFilename(filename);
        a.setContentType(contentType);
        a.setSizeBytes(8);
        a.setUploader(uploader);
        return a;
    }

    @Test
    void download_returnsImage_whenAttachmentIsFeedReferenced() throws Exception {
        UUID id = UUID.randomUUID();
        String filename = id + ".png";
        Files.write(uploadDir.resolve(filename), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        when(attachmentRepository.findById(id)).thenReturn(Optional.of(imageRow(id, filename, "image/png")));
        when(attachmentRepository.existsAsFeedAttachment(eq(id))).thenReturn(true);
        when(attachmentStorageService.getUploadPath()).thenReturn(uploadDir);

        ResponseEntity<Resource> response = controller.download(id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains(filename);
        // public + max-age=3600 — feed images are visible to any authenticated
        // viewer, so shared HTTP caches are allowed (unlike chat downloads).
        assertThat(response.getHeaders().getCacheControl())
                .isEqualTo("max-age=3600, public");
        Resource body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.contentLength()).isEqualTo(8);
    }

    @Test
    void download_throwsNotFound_whenAttachmentRowMissing() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.download(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(attachmentRepository, never()).existsAsFeedAttachment(eq(id));
    }

    @Test
    void download_throwsNotFound_whenAttachmentIsChatOnly() {
        // The id exists in the attachments table but no feed_post_attachments
        // row references it — caller used the wrong path. Uniform 404 so the
        // feed-media endpoint cannot probe chat attachment existence.
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id))
                .thenReturn(Optional.of(imageRow(id, id + ".pdf", "application/pdf")));
        when(attachmentRepository.existsAsFeedAttachment(eq(id))).thenReturn(false);

        assertThatThrownBy(() -> controller.download(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(attachmentStorageService, never()).getUploadPath();
    }

    @Test
    void download_throwsNotFound_whenFileMissingFromDisk() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id))
                .thenReturn(Optional.of(imageRow(id, id + ".png", "image/png")));
        when(attachmentRepository.existsAsFeedAttachment(eq(id))).thenReturn(true);
        when(attachmentStorageService.getUploadPath()).thenReturn(uploadDir);

        assertThatThrownBy(() -> controller.download(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void download_throwsNotFound_whenStoredFilenameEscapesUploadDir() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id))
                .thenReturn(Optional.of(imageRow(id, "../../etc/passwd", "image/png")));
        when(attachmentRepository.existsAsFeedAttachment(eq(id))).thenReturn(true);
        when(attachmentStorageService.getUploadPath()).thenReturn(uploadDir);

        assertThatThrownBy(() -> controller.download(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * {@code resolveMediaType} is private; we exercise the image branches and
     * the defence-in-depth fallbacks directly. Production reaches only the
     * five image branches because {@code FeedPostService} rejects non-image
     * uploads before they ever land in {@code feed_post_attachments}.
     */
    @Nested
    class ResolveMediaType {
        @Test
        void jpegExtensionMapsToImageJpeg() {
            assertThat(invoke("x.jpg")).isEqualTo(MediaType.IMAGE_JPEG);
            assertThat(invoke("x.jpeg")).isEqualTo(MediaType.IMAGE_JPEG);
        }

        @Test
        void webpExtensionMapsToImageWebp() {
            assertThat(invoke("x.webp")).isEqualTo(MediaType.parseMediaType("image/webp"));
        }

        @Test
        void uppercaseExtensionResolves() {
            assertThat(invoke("MIXED.PNG")).isEqualTo(MediaType.IMAGE_PNG);
        }

        @Test
        void unknownExtensionFallsBackToOctetStream() {
            assertThat(invoke("x.pdf")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
            assertThat(invoke("x.txt")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        }

        @Test
        void filenameWithoutDotFallsBackToOctetStream() {
            assertThat(invoke("noext")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        }

        @Test
        void filenameWithTrailingDotFallsBackToOctetStream() {
            assertThat(invoke("trailing.")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        }

        private MediaType invoke(String filename) {
            try {
                Method m = FeedMediaDownloadController.class
                        .getDeclaredMethod("resolveMediaType", String.class);
                m.setAccessible(true);
                return (MediaType) m.invoke(null, filename);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }
}
