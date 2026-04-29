package com.group7.backend.config.jsonld;

import com.group7.backend.config.AppProperties;
import com.group7.backend.dto.response.MessageResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps {@link MessageResponse} to a W3C Activity Streams 2.0
 * {@code Create} activity wrapping a {@code Note} object — matching the
 * canonical AS 2.0 example documented in the project wiki's "Use of Standards"
 * page (Sally posts a note).
 *
 * <p>Activity Streams 2.0 was the natural fit for chat messages over plain
 * Schema.org because every message IS a {@code Create} activity (an actor
 * publishing content), and the wiki specifically illustrates this pattern as
 * the messaging idiom. Auto-discovered by {@code JsonLdResponseBodyAdvice}.
 *
 * <p>Reference: <a href="https://www.w3.org/TR/activitystreams-core/">W3C
 * Activity Streams 2.0 Core</a> (Recommendation 23 May 2017).
 */
@Component
public class NoteMapping implements JsonLdMapping {

    private final String baseUrl;

    public NoteMapping(AppProperties appProperties) {
        String configured = appProperties.getBaseUrl();
        this.baseUrl = configured.endsWith("/")
                ? configured.substring(0, configured.length() - 1)
                : configured;
    }

    @Override
    public boolean supports(Class<?> bodyType) {
        return MessageResponse.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        MessageResponse message = (MessageResponse) body;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("@context", JsonLdContext.ACTIVITY_STREAMS);
        result.put("@type", "Create");
        if (message.getId() != null) {
            result.put("@id", activityIri(message));
        }

        result.put("actor", buildActor(message));
        result.put("object", buildNote(message));
        if (message.getSentAt() != null) {
            result.put("published", message.getSentAt().toString());
        }
        return result;
    }

    private Map<String, Object> buildActor(MessageResponse message) {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("@type", "Person");
        if (message.getSenderId() != null) {
            actor.put("@id", baseUrl + "/api/users/" + message.getSenderId());
        }
        String displayName = displayName(message);
        if (displayName != null) {
            actor.put("name", displayName);
        }
        return actor;
    }

    private Map<String, Object> buildNote(MessageResponse message) {
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("@type", "Note");
        note.put("@id", noteIri(message));
        if (message.getContent() != null && !message.getContent().isBlank()) {
            note.put("content", message.getContent());
        }
        if (message.getSenderId() != null) {
            note.put("attributedTo", baseUrl + "/api/users/" + message.getSenderId());
        }
        if (message.getSentAt() != null) {
            note.put("published", message.getSentAt().toString());
        }
        if (message.getAttachmentUrl() != null && !message.getAttachmentUrl().isBlank()) {
            // AS 2.0 'attachment' carries linked media for the Note.
            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("@type", "Document");
            attachment.put("url", message.getAttachmentUrl());
            note.put("attachment", attachment);
        }
        return note;
    }

    private String activityIri(MessageResponse message) {
        return noteIri(message) + "#create";
    }

    private String noteIri(MessageResponse message) {
        return baseUrl + "/api/conversations/" + message.getConversationId()
                + "/messages/" + message.getId();
    }

    private static String displayName(MessageResponse message) {
        String first = message.getSenderFirstName();
        String last = message.getSenderLastName();
        if (first == null && last == null) {
            return null;
        }
        if (first == null) {
            return last;
        }
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }
}
