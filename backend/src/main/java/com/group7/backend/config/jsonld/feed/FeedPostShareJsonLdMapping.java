package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.jsonld.JsonLdContext;
import com.group7.backend.config.jsonld.JsonLdMapping;
import com.group7.backend.entity.FeedPostShare;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link FeedPostShare} entity as a W3C Activity Streams 2.0
 * {@code Announce} activity — the AS 2.0 canonical type for "share /
 * boost / retweet" semantics. Mirrors Mastodon's wire shape for boosts.
 *
 * <p>Quote-share commentary (the {@code body} column on
 * {@link FeedPostShare}, populated by the new {@code /reposts} endpoint
 * in #484) lands as {@code as:content} on the Announce activity itself.
 * AS 2.0 permits this — the wrapper Activity may carry its own content
 * field that's distinct from the object's content. Bare reposts (no
 * commentary) omit the field.
 */
@Component
public class FeedPostShareJsonLdMapping implements JsonLdMapping {

    private final FeedIriBuilder iri;

    public FeedPostShareJsonLdMapping(FeedIriBuilder iri) {
        this.iri = iri;
    }

    @Override
    public boolean supports(Class<?> bodyType) {
        return FeedPostShare.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        FeedPostShare share = (FeedPostShare) body;

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@context", List.of(JsonLdContext.ACTIVITY_STREAMS));
        doc.put("@type", "Announce");
        if (share.getPostId() != null && share.getSharerId() != null) {
            doc.put("@id", iri.share(share.getPostId(), share.getSharerId()));
            doc.put("id", iri.share(share.getPostId(), share.getSharerId()));
            doc.put("actor", iri.person(share.getSharerId()));
            doc.put("object", iri.post(share.getPostId()));
        }
        if (share.getCreatedAt() != null) {
            doc.put("published", share.getCreatedAt().toString());
        }
        // Quote-share commentary (when the share is a repost with a body
        // string). The shipped FeedPostShare entity carries a `body` field
        // for the eventual #484 quote-share work; surface it as
        // as:content if present so consumers don't need a separate fetch.
        String commentary = share.getBody();
        if (commentary != null && !commentary.isBlank()) {
            doc.put("content", commentary);
        }
        return doc;
    }
}
