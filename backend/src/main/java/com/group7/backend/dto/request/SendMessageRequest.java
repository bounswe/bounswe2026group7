package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Body for sending a chat message within a mentorship")
public class SendMessageRequest {

    @NotBlank
    @Size(max = 4000, message = "Message content must not exceed 4000 characters")
    @Schema(description = "Message text",
            example = "Hi, I prepared the notes you asked about.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "Optional attachment id returned by POST /api/messages/attachments. "
            + "The backend reconstructs the URL and enforces that the sender must equal the "
            + "uploader of this attachment.",
            example = "5b9c1f0a-2c2c-4cf2-8f1d-9d4f1a0e6b5e")
    private UUID attachmentId;
}
