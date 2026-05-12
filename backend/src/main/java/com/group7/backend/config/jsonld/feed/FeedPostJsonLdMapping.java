package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.jsonld.JsonLdContext;
import com.group7.backend.config.jsonld.JsonLdMapping;
import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.dto.response.FeedPostResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link FeedPostResponse} as a W3C Activity Streams 2.0
 * {@code Note} also typed as a Schema.org {@code SocialMediaPosting},
 * matching the standards-coverage commitment in the project wiki.
 *
 * <h2>Shape decisions</h2>
 * <ul>
 *   <li><b>{@code @context} order</b>: Activity Streams 2.0 first, then
 *       Schema.org. Matches Mastodon's pattern and prevents Schema.org's
 *       {@code id} from shadowing AS 2.0's when a consumer interprets the
 *       document with a single context.</li>
 *   <li><b>Multi-typing</b>: {@code @type: ["Note", "SocialMediaPosting"]}
 *       — the post is both a generic AS 2.0 actor-published object and a
 *       schema.org typed posting. Fediverse consumers see the Note;
 *       search-engine crawlers see the SocialMediaPosting.</li>
 *   <li><b>{@code contentMap} only when {@code lang} is non-null</b>: AS 2.0
 *       requires {@code contentMap} to be a BCP-47-keyed map. Legacy posts
 *       that predate the column on {@code feed_posts} have {@code lang}
 *       null and are surfaced with plain {@code content} only.</li>
 *   <li><b>Counts as {@code Schema.org InteractionCounter}</b>: the issue
 *       acceptance criteria explicitly call out
 *       {@code interactionStatistic}, and the counter-objects pattern is
 *       what every consumer (including Schema.org's own examples)
 *       expects.</li>
 * </ul>
 *
 * <p>Auto-discovered by {@code JsonLdResponseBodyAdvice} on app boot.
 */
@Component
public class FeedPostJsonLdMapping implements JsonLdMapping {

    private final FeedIriBuilder iri;

    public FeedPostJsonLdMapping(FeedIriBuilder iri) {
        this.iri = iri;
    }

    @Override
    public boolean supports(Class<?> bodyType) {
        return FeedPostResponse.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        FeedPostResponse post = (FeedPostResponse) body;
        return FeedJsonLd.renderPost(
                iri,
                post.id(),
                post.authorId(),
                post.authorFirstName(),
                post.body(),
                post.hashtags(),
                post.createdAt() == null ? null : post.createdAt().toString(),
                renderUpdatedAt(post),
                post.lang(),
                attachmentsToJsonLd(post.attachments()),
                // FeedPostResponse doesn't carry interaction counts — they
                // live on FeedPostInteractionState. Emit the counters with
                // zeros so the JSON-LD shape is stable across endpoints;
                // consumers needing live counts call
                // GET /api/feed/posts/{id}/interaction-state.
                0L, 0L, 0L, 0L);
    }

    private static String renderUpdatedAt(FeedPostResponse post) {
        if (!post.isEdited() || post.updatedAt() == null) {
            return null;
        }
        return post.updatedAt().toString();
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

    /**
     * Shared rendering for both the detail-DTO and the list-item DTO so the
     * wire shape is identical between {@code GET /api/feed/posts/{id}} and
     * the items inside an OrderedCollectionPage from {@code /api/feed/for-you}.
     * Kept package-private so {@link FeedPostListItemJsonLdMapping} can reuse
     * it without exposing the implementation to the broader package.
     */
    static final class FeedJsonLd {

        static Map<String, Object> renderPost(FeedIriBuilder iri,
                                              Long postId,
                                              Long authorId,
                                              String authorFirstName,
                                              String content,
                                              List<String> hashtags,
                                              String publishedIso,
                                              String updatedIso,
                                              String lang,
                                              List<Map<String, Object>> attachments,
                                              long likeCount,
                                              long commentCount,
                                              long shareCount,
                                              long bookmarkCount) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("@context", List.of(
                    JsonLdContext.ACTIVITY_STREAMS,
                    JsonLdContext.SCHEMA_ORG));
            doc.put("@type", List.of("Note", "SocialMediaPosting"));
            doc.put("@id", iri.post(postId));
            doc.put("id", iri.post(postId));

            Map<String, Object> actor = new LinkedHashMap<>();
            actor.put("@type", "Person");
            actor.put("@id", iri.person(authorId));
            if (authorFirstName != null && !authorFirstName.isBlank()) {
                actor.put("name", authorFirstName);
            }
            doc.put("attributedTo", actor);

            if (publishedIso != null) {
                doc.put("published", publishedIso);
            }
            if (updatedIso != null) {
                doc.put("updated", updatedIso);
            }
            if (content != null && !content.isBlank()) {
                doc.put("content", content);
            }
            if (lang != null && !lang.isBlank() && content != null && !content.isBlank()) {
                // AS 2.0 contentMap is a JSON object keyed by BCP-47 tag.
                doc.put("contentMap", Map.of(lang, content));
                doc.put("inLanguage", lang);
            }
            if (hashtags != null && !hashtags.isEmpty()) {
                doc.put("tag", hashtags.stream()
                        .map(h -> {
                            Map<String, Object> tag = new LinkedHashMap<>();
                            tag.put("type", "Hashtag");
                            tag.put("name", "#" + h);
                            return tag;
                        })
                        .toList());
            }
            if (attachments != null && !attachments.isEmpty()) {
                doc.put("attachment", attachments);
            }

            // Schema.org InteractionCounter is what consumers expect for
            // aggregate counters; the full AS 2.0 `likes`/`shares`
            // OrderedCollection alternative is heavier and not what
            // consumers want to dereference from a list response.
            doc.put("interactionStatistic", List.of(
                    counter("https://schema.org/LikeAction", likeCount),
                    counter("https://schema.org/CommentAction", commentCount),
                    counter("https://schema.org/ShareAction", shareCount),
                    counter("https://schema.org/BookmarkAction", bookmarkCount)));
            return doc;
        }

        private static Map<String, Object> counter(String interactionType, long count) {
            Map<String, Object> counter = new LinkedHashMap<>();
            counter.put("@type", "InteractionCounter");
            counter.put("interactionType", interactionType);
            counter.put("userInteractionCount", count);
            return counter;
        }

        private FeedJsonLd() {
        }
    }
}
