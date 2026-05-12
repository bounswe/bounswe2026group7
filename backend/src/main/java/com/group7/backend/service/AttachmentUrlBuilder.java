package com.group7.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single source of truth for the URL layout of authenticated attachment
 * downloads. Every component that surfaces an attachment URL — chat upload
 * response, message response, JSON-LD payload, feed post response — resolves
 * it here, so URL-shape changes need a single edit.
 *
 * <p>Two download paths share the same {@code attachments} table but enforce
 * different ACLs at the controller level:
 * <ul>
 *   <li>{@link #downloadUrl} → {@code /api/uploads/attachments/{id}}: requires
 *       the caller to be a participant of a conversation containing a message
 *       that references the attachment.</li>
 *   <li>{@link #feedMediaUrl} → {@code /api/uploads/feed-media/{id}}: requires
 *       authentication and that the attachment is referenced by some feed
 *       post's junction row. No per-user filter.</li>
 * </ul>
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

    /**
     * Public URL for a feed-post image. Resolves to the feed-scoped download
     * controller which does not enforce the conversation-participant ACL —
     * only authentication. Same UUID space as {@link #downloadUrl}; the path
     * disambiguates the ACL gate at the controller level.
     */
    public String feedMediaUrl(UUID attachmentId) {
        return baseUrl + "/api/uploads/feed-media/" + attachmentId;
    }
}
