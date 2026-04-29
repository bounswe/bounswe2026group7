package com.group7.backend.config.jsonld;

import com.group7.backend.config.AppProperties;
import com.group7.backend.dto.response.MessageResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps {@link MessageResponse} to a Schema.org {@code Note} JSON-LD payload.
 * Auto-discovered by {@code JsonLdResponseBodyAdvice} via the
 * {@link JsonLdMapping} bean type.
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
        result.put("@context", JsonLdContext.SCHEMA_ORG);
        result.put("@type", "Note");
        if (message.getId() != null) {
            result.put("@id",
                    baseUrl + "/api/mentorships/" + message.getMentorshipId()
                            + "/messages/" + message.getId());
        }
        putIfPresent(result, "text", message.getContent());
        if (message.getSenderId() != null) {
            Map<String, Object> author = new LinkedHashMap<>();
            author.put("@type", "Person");
            author.put("@id", baseUrl + "/api/users/" + message.getSenderId());
            putIfPresent(author, "givenName", message.getSenderFirstName());
            putIfPresent(author, "familyName", message.getSenderLastName());
            result.put("author", author);
        }
        if (message.getSentAt() != null) {
            result.put("dateCreated", message.getSentAt().toString());
        }
        if (message.getReadAt() != null) {
            result.put("dateRead", message.getReadAt().toString());
        }
        putIfPresent(result, "associatedMedia", message.getAttachmentUrl());
        return result;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        map.put(key, value);
    }
}
