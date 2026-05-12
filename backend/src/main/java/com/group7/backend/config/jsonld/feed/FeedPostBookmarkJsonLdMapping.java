package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.jsonld.JsonLdContext;
import com.group7.backend.config.jsonld.JsonLdMapping;
import com.group7.backend.entity.FeedPostBookmark;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link FeedPostBookmark} entity as a W3C Activity Streams 2.0
 * {@code Add} activity targeting the user's bookmark collection. The AS 2.0
 * convention for "save / bookmark" semantics is {@code Add} with the object
 * being the bookmarked resource and the {@code target} being the actor's
 * collection — closer to the original Mastodon spec for personal-list
 * additions than {@code Like}.
 */
@Component
public class FeedPostBookmarkJsonLdMapping implements JsonLdMapping {

    private final FeedIriBuilder iri;

    public FeedPostBookmarkJsonLdMapping(FeedIriBuilder iri) {
        this.iri = iri;
    }

    @Override
    public boolean supports(Class<?> bodyType) {
        return FeedPostBookmark.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        FeedPostBookmark bookmark = (FeedPostBookmark) body;
        Long postId = bookmark.getId() == null ? null : bookmark.getId().getPostId();
        Long userId = bookmark.getId() == null ? null : bookmark.getId().getUserId();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@context", List.of(JsonLdContext.ACTIVITY_STREAMS));
        doc.put("@type", "Add");
        if (postId != null && userId != null) {
            doc.put("@id", iri.bookmark(postId, userId));
            doc.put("id", iri.bookmark(postId, userId));
            doc.put("actor", iri.person(userId));
            doc.put("object", iri.post(postId));
            doc.put("target", iri.bookmarkCollection(userId));
        }
        if (bookmark.getCreatedAt() != null) {
            doc.put("published", bookmark.getCreatedAt().toString());
        }
        return doc;
    }
}
