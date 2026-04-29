package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Size(max = 512, message = "Attachment URL must not exceed 512 characters")
    @Schema(description = "Optional attachment URL returned by POST /api/messages/attachments",
            example = "http://localhost:8080/api/uploads/attachments/abc.pdf")
    private String attachmentUrl;
}
