package com.group7.backend.dto.response;

import com.group7.backend.entity.Attachment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Wire-shape for a chat attachment. Returned as the body of
 * {@code POST /api/messages/attachments} (so the client learns the id to
 * attach to a subsequent send) and embedded in {@link MessageResponse}
 * (so a recipient renders the attachment without a second request).
 *
 * <p>{@code downloadUrl} is reconstructed server-side from a single helper
 * so clients never need to know the URL layout.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Metadata for a chat attachment, including its download URL")
public class AttachmentSummary {

    @Schema(description = "Attachment ID — pass this back as `attachmentId` when sending a message",
            example = "5b9c1f0a-2c2c-4cf2-8f1d-9d4f1a0e6b5e")
    private UUID id;

    @Schema(description = "Authenticated download URL",
            example = "http://localhost:8080/api/uploads/attachments/5b9c1f0a-2c2c-4cf2-8f1d-9d4f1a0e6b5e")
    private String downloadUrl;

    @Schema(description = "Stored filename (UUID + extension)", example = "5b9c1f0a-2c2c-4cf2-8f1d-9d4f1a0e6b5e.pdf")
    private String filename;

    @Schema(description = "Validated content type", example = "application/pdf")
    private String contentType;

    @Schema(description = "File size in bytes", example = "204800")
    private long sizeBytes;

    public static AttachmentSummary of(Attachment attachment, String downloadUrl) {
        AttachmentSummary s = new AttachmentSummary();
        s.setId(attachment.getId());
        s.setDownloadUrl(downloadUrl);
        s.setFilename(attachment.getFilename());
        s.setContentType(attachment.getContentType());
        s.setSizeBytes(attachment.getSizeBytes());
        return s;
    }
}
