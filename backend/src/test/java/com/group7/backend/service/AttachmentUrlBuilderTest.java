package com.group7.backend.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentUrlBuilderTest {

    @Test
    void buildsCanonicalDownloadUrl() {
        AttachmentUrlBuilder builder = new AttachmentUrlBuilder("http://localhost:8080");
        UUID id = UUID.randomUUID();
        assertThat(builder.downloadUrl(id))
                .isEqualTo("http://localhost:8080/api/uploads/attachments/" + id);
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        AttachmentUrlBuilder builder = new AttachmentUrlBuilder("https://group7.example/");
        UUID id = UUID.randomUUID();
        assertThat(builder.downloadUrl(id))
                .isEqualTo("https://group7.example/api/uploads/attachments/" + id);
    }
}
