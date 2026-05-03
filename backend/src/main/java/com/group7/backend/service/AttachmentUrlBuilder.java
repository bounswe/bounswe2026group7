package com.group7.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single source of truth for the URL layout of authenticated chat-attachment
 * downloads. Every component that surfaces an attachment URL — upload
 * response, message response, JSON-LD payload — resolves it here, so URL-shape
 * changes need a single edit.
 */
@Component
public class AttachmentUrlBuilder {

    private final String baseUrl;

    public AttachmentUrlBuilder(@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    public String downloadUrl(UUID attachmentId) {
        return baseUrl + "/api/uploads/attachments/" + attachmentId;
    }
}
