package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Result of a successful attachment upload")
public class AttachmentUploadResponse {

    @Schema(description = "Public URL to GET the attachment",
            example = "http://localhost:8080/api/uploads/attachments/abc-123.pdf")
    private String url;

    @Schema(description = "Stored filename (UUID + extension)", example = "abc-123.pdf")
    private String filename;

    @Schema(description = "Content-Type that was validated for the upload",
            example = "application/pdf")
    private String contentType;

    @Schema(description = "Stored size in bytes", example = "204800")
    private long sizeBytes;

    public static AttachmentUploadResponse of(String url,
                                              String filename,
                                              String contentType,
                                              long sizeBytes) {
        AttachmentUploadResponse r = new AttachmentUploadResponse();
        r.setUrl(url);
        r.setFilename(filename);
        r.setContentType(contentType);
        r.setSizeBytes(sizeBytes);
        return r;
    }
}
