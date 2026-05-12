package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.jsonld.JsonLdContext;
import com.group7.backend.config.jsonld.JsonLdMapping;
import com.group7.backend.entity.FeedPostLike;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link FeedPostLike} entity as a W3C Activity Streams 2.0
 * {@code Like} activity. The activity's {@code actor} is the user who
 * performed the like and the {@code object} is the post they liked.
 *
 * <p>The toggle endpoints currently return a counter aggregate
 * ({@code FeedPostInteractionState}) rather than the entity itself, so
 * no controller-level path emits this mapping today. It's registered
 * eagerly because (a) the SPI is auto-discovery based and any future
 * endpoint that surfaces a {@code FeedPostLike} row gets the AS 2.0
 * shape for free; and (b) the mapping is the documented standards
 * surface in the wiki's coverage table, which a consumer can build
 * against even without a corresponding endpoint.
 */
@Component
public class FeedPostLikeJsonLdMapping implements JsonLdMapping {

    private final FeedIriBuilder iri;

    public FeedPostLikeJsonLdMapping(FeedIriBuilder iri) {
        this.iri = iri;
    }

    @Override
    public boolean supports(Class<?> bodyType) {
        return FeedPostLike.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        FeedPostLike like = (FeedPostLike) body;
        Long postId = like.getId() == null ? null : like.getId().getPostId();
        Long userId = like.getId() == null ? null : like.getId().getUserId();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@context", List.of(JsonLdContext.ACTIVITY_STREAMS));
        doc.put("@type", "Like");
        if (postId != null && userId != null) {
            doc.put("@id", iri.like(postId, userId));
            doc.put("id", iri.like(postId, userId));
            doc.put("actor", iri.person(userId));
            doc.put("object", iri.post(postId));
        }
        if (like.getCreatedAt() != null) {
            doc.put("published", like.getCreatedAt().toString());
        }
        return doc;
    }
}
