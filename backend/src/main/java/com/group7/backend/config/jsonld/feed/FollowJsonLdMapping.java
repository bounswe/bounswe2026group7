package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.jsonld.JsonLdContext;
import com.group7.backend.config.jsonld.JsonLdMapping;
import com.group7.backend.entity.Follow;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link Follow} entity as a W3C Activity Streams 2.0
 * {@code Follow} activity. The activity's {@code actor} is the follower
 * and the {@code object} is the followee — directly matching the AS 2.0
 * spec example for follow-graph relationships.
 */
@Component
public class FollowJsonLdMapping implements JsonLdMapping {

    private final FeedIriBuilder iri;

    public FollowJsonLdMapping(FeedIriBuilder iri) {
        this.iri = iri;
    }

    @Override
    public boolean supports(Class<?> bodyType) {
        return Follow.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        Follow follow = (Follow) body;
        Long followerId = follow.getId() == null ? null : follow.getId().getFollowerId();
        Long followeeId = follow.getId() == null ? null : follow.getId().getFolloweeId();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@context", List.of(JsonLdContext.ACTIVITY_STREAMS));
        doc.put("@type", "Follow");
        if (followerId != null && followeeId != null) {
            doc.put("@id", iri.follow(followerId, followeeId));
            doc.put("id", iri.follow(followerId, followeeId));
            doc.put("actor", iri.person(followerId));
            doc.put("object", iri.person(followeeId));
        }
        if (follow.getCreatedAt() != null) {
            doc.put("published", follow.getCreatedAt().toString());
        }
        return doc;
    }
}
