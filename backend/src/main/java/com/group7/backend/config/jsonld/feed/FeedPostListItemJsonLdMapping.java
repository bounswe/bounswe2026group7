package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.jsonld.JsonLdMapping;
import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.dto.response.FeedPostListItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the slimmer {@link FeedPostListItem} (returned by the For-You,
 * Following, and search list endpoints) with the same AS 2.0 / Schema.org
 * shape as the detail-DTO mapping. Routing both DTOs through the shared
 * renderer in {@link FeedPostJsonLdMapping.FeedJsonLd} keeps the wire
 * format identical between a single-post fetch and a post embedded in an
 * {@code OrderedCollectionPage}.
 *
 * <p>The list-item DTO carries the live like / comment counts and the
 * ranker's factor codes; the detail DTO does not. We surface the counts
 * here so paged JSON-LD responses are not falsely zeroed (counters in the
 * detail mapping are zeroed because that DTO truly does not know them).
 * The factor codes are dropped from JSON-LD on purpose — they're internal
 * ranking telemetry, not user-facing AS 2.0 content.
 */
@Component
public class FeedPostListItemJsonLdMapping implements JsonLdMapping {

    private final FeedIriBuilder iri;

    public FeedPostListItemJsonLdMapping(FeedIriBuilder iri) {
        this.iri = iri;
    }

    @Override
    public boolean supports(Class<?> bodyType) {
        return FeedPostListItem.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        FeedPostListItem item = (FeedPostListItem) body;
        return FeedPostJsonLdMapping.FeedJsonLd.renderPost(
                iri,
                item.id(),
                item.authorId(),
                item.authorFirstName(),
                item.body(),
                item.hashtags(),
                item.createdAt() == null ? null : item.createdAt().toString(),
                null,
                item.lang(),
                attachmentsToJsonLd(item.attachments()),
                item.likeCount(),
                item.commentCount(),
                // ListItem doesn't carry share / bookmark counts (the list
                // endpoints intentionally don't pay for those aggregates).
                // Emit zeros for shape stability; consumers needing live
                // share / bookmark counts dereference the post detail IRI.
                0L,
                0L);
    }

    private static List<Map<String, Object>> attachmentsToJsonLd(List<AttachmentSummary> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .map(a -> {
                    Map<String, Object> attachment = new LinkedHashMap<>();
                    attachment.put("type", "Image");
                    if (a.getDownloadUrl() != null) {
                        attachment.put("url", a.getDownloadUrl());
                    }
                    if (a.getContentType() != null) {
                        attachment.put("mediaType", a.getContentType());
                    }
                    return attachment;
                })
                .toList();
    }
}
