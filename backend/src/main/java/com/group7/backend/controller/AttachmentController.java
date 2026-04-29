package com.group7.backend.controller;

import com.group7.backend.dto.response.AttachmentUploadResponse;
import com.group7.backend.service.AttachmentStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/messages/attachments")
@Tag(name = "Message Attachments", description = "Upload files referenced by chat messages")
public class AttachmentController {

    private final AttachmentStorageService attachmentStorageService;

    public AttachmentController(AttachmentStorageService attachmentStorageService) {
        this.attachmentStorageService = attachmentStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a chat attachment",
            description = "Validates and stores a file (jpg/png/gif/webp/pdf/docx/txt; "
                    + "max 5 MB) and returns a public URL. The caller then includes that "
                    + "URL as `attachmentUrl` when sending a message.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Stored",
                    content = @Content(schema = @Schema(implementation = AttachmentUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file (type/magic/size)", content = @Content)
    })
    public ResponseEntity<AttachmentUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        AttachmentUploadResponse response = attachmentStorageService.storeAttachment(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
