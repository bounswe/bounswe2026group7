package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.AppProperties;
import com.group7.backend.dto.response.FeedPostListItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two non-obvious contracts of the list-item mapping: live
 * like / comment counts surface in the AS 2.0 InteractionCounter
 * aggregates, and the share / bookmark counters default to zero
 * because the list endpoints intentionally don't pay for those
 * aggregates (consumers needing them dereference the post detail IRI).
 */
class FeedPostListItemJsonLdMappingTest {

    private FeedPostListItemJsonLdMapping mapping;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("https://api.example.com");
        mapping = new FeedPostListItemJsonLdMapping(new FeedIriBuilder(props));
    }

    @Test
    void supports_acceptsFeedPostListItem() {
        assertThat(mapping.supports(FeedPostListItem.class)).isTrue();
        assertThat(mapping.supports(String.class)).isFalse();
    }

    @Test
    void apply_emitsBothTypesAndCanonicalIri() {
        Map<String, Object> doc = mapping.apply(sampleItem("en"));

        assertThat(doc.get("@type")).isEqualTo(List.of("Note", "SocialMediaPosting"));
        assertThat(doc.get("@id")).isEqualTo("https://api.example.com/api/feed/posts/42");
    }

    @Test
    void apply_surfacesLiveLikeAndCommentCounts_inInteractionStatistic() {
        FeedPostListItem item = new FeedPostListItem(
                42L, 17L, "Ada", "body", List.of(),
                OffsetDateTime.now(),
                5L,    // likeCount
                3L,    // commentCount
                List.of(),
                List.of(),
                false, false,
                null, null, null, null,
                "en");

        Map<String, Object> doc = mapping.apply(item);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats = (List<Map<String, Object>>) doc.get("interactionStatistic");
        assertThat(stats).hasSize(4);
        // Live counts (like / comment) propagate from the list-item.
        assertThat(stats.get(0).get("interactionType")).isEqualTo("https://schema.org/LikeAction");
        assertThat(stats.get(0).get("userInteractionCount")).isEqualTo(5L);
        assertThat(stats.get(1).get("interactionType")).isEqualTo("https://schema.org/CommentAction");
        assertThat(stats.get(1).get("userInteractionCount")).isEqualTo(3L);
    }

    @Test
    void apply_zeroDefaultsShareAndBookmarkCounters_documentedContract() {
        FeedPostListItem item = new FeedPostListItem(
                42L, 17L, "Ada", "body", List.of(),
                OffsetDateTime.now(),
                5L, 3L,
                List.of(),
                List.of(),
                false, false,
                null, null, null, null,
                null);

        Map<String, Object> doc = mapping.apply(item);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats = (List<Map<String, Object>>) doc.get("interactionStatistic");
        // The list endpoints don't pay for these aggregates — consumers
        // needing live share / bookmark counts dereference the post
        // detail IRI. Pinning the zero default keeps the wire shape
        // stable across list and detail consumers.
        assertThat(stats.get(2).get("interactionType")).isEqualTo("https://schema.org/ShareAction");
        assertThat(stats.get(2).get("userInteractionCount")).isEqualTo(0L);
        assertThat(stats.get(3).get("interactionType")).isEqualTo("https://schema.org/BookmarkAction");
        assertThat(stats.get(3).get("userInteractionCount")).isEqualTo(0L);
    }

    @Test
    void apply_emitsContentMapAndInLanguage_whenLangPresent() {
        Map<String, Object> doc = mapping.apply(sampleItem("tr"));

        assertThat(doc.get("inLanguage")).isEqualTo("tr");
        assertThat(doc.get("contentMap")).isEqualTo(Map.of("tr", "body content"));
    }

    @Test
    void apply_emitsBareHashtagNames_perAS2Spec() {
        Map<String, Object> doc = mapping.apply(sampleItem("en"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) doc.get("tag");
        // The AS 2.0 Hashtag extension accepts bare-string names; the
        // leading `#` is a Mastodon-only override that the issue note
        // explicitly disallows.
        assertThat(tags.get(0).get("name")).isEqualTo("datascience");
        assertThat(tags.get(0).get("type")).isEqualTo("Hashtag");
    }

    // ── Fixtures ────────────────────────────────────────────────

    private static FeedPostListItem sampleItem(String lang) {
        return new FeedPostListItem(
                42L, 17L, "Ada", "body content",
                List.of("datascience"),
                OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                0L, 0L,
                List.of(),
                List.of(),
                false, false,
                null, null, null, null,
                lang);
    }
}
