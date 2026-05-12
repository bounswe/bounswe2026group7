package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.jsonld.JsonLdContext;
import com.group7.backend.config.jsonld.JsonLdMapping;
import com.group7.backend.dto.response.FeedCommentResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link FeedCommentResponse} as an AS 2.0 {@code Note} that
 * links to its parent post via {@code inReplyTo}. Same context order
 * (Activity Streams first, Schema.org second) as the post mapping so
 * consumers don't have to switch ontologies mid-thread.
 *
 * <p>Soft-deleted comments surface in the underlying DTO with a null body
 * and {@code isDeleted=true}. We honour that here by omitting the
 * {@code content} field entirely — AS 2.0 consumers should treat that as
 * a redacted post placeholder, which mirrors the UI's "[comment removed]"
 * rendering.
 */
@Component
public class FeedCommentJsonLdMapping implements JsonLdMapping {

    private final FeedIriBuilder iri;

    public FeedCommentJsonLdMapping(FeedIriBuilder iri) {
        this.iri = iri;
    }

    @Override
    public boolean supports(Class<?> bodyType) {
        return FeedCommentResponse.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        FeedCommentResponse comment = (FeedCommentResponse) body;

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@context", List.of(
                JsonLdContext.ACTIVITY_STREAMS,
                JsonLdContext.SCHEMA_ORG));
        doc.put("@type", "Note");
        doc.put("@id", iri.comment(comment.id()));
        doc.put("id", iri.comment(comment.id()));
        doc.put("inReplyTo", iri.post(comment.postId()));

        if (comment.authorId() != null) {
            Map<String, Object> actor = new LinkedHashMap<>();
            actor.put("@type", "Person");
            actor.put("@id", iri.person(comment.authorId()));
            if (comment.authorFirstName() != null && !comment.authorFirstName().isBlank()) {
                actor.put("name", comment.authorFirstName());
            }
            doc.put("attributedTo", actor);
        }

        if (comment.createdAt() != null) {
            doc.put("published", comment.createdAt().toString());
        }
        if (comment.isEdited() && comment.updatedAt() != null) {
            doc.put("updated", comment.updatedAt().toString());
        }

        // Soft-deleted comments arrive with body == null and isDeleted == true.
        // Omit `content` rather than emit an empty string so consumers can
        // distinguish "redacted" from "literally empty" — Mastodon and the
        // AS 2.0 examples use this convention for tombstoned content.
        if (!comment.isDeleted() && comment.body() != null && !comment.body().isBlank()) {
            doc.put("content", comment.body());
        }
        return doc;
    }
}
