package com.group7.backend.controller;

import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.UserRepository;
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
import org.springframework.security.core.Authentication;
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
    private final UserRepository userRepository;

    public AttachmentController(AttachmentStorageService attachmentStorageService,
                                UserRepository userRepository) {
        this.attachmentStorageService = attachmentStorageService;
        this.userRepository = userRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a chat attachment",
            description = "Validates and stores a file (jpg/png/gif/webp/pdf/docx/txt; max 5 MB) "
                    + "and returns its attachment summary. Pass the returned `id` back as "
                    + "`attachmentId` when sending a message — the backend reconstructs the URL "
                    + "and enforces that the sender of the message must equal the uploader.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Stored",
                    content = @Content(schema = @Schema(implementation = AttachmentSummary.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file (type/magic/size)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    public ResponseEntity<AttachmentSummary> upload(@RequestParam("file") MultipartFile file,
                                                    Authentication authentication) {
        Long uploaderId = (Long) authentication.getCredentials();
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new ResourceNotFoundException("Uploader not found"));
        AttachmentSummary response = attachmentStorageService.storeAttachment(file, uploader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
