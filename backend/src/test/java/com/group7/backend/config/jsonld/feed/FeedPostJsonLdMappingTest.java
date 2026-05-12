package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.AppProperties;
import com.group7.backend.config.jsonld.JsonLdContext;
import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.dto.response.FeedPostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the AS 2.0 / Schema.org shape produced for a single feed post.
 * Each assertion guards a specific contract spelled out in the standards
 * coverage commitment on the project wiki and in the issue's acceptance
 * criteria.
 */
class FeedPostJsonLdMappingTest {

    private FeedPostJsonLdMapping mapping;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("https://api.example.com/");
        mapping = new FeedPostJsonLdMapping(new FeedIriBuilder(props));
    }

    @Test
    void supports_acceptsFeedPostResponse_andNothingElse() {
        assertThat(mapping.supports(FeedPostResponse.class)).isTrue();
        assertThat(mapping.supports(String.class)).isFalse();
    }

    @Test
    void apply_emitsContextSchemaOrgAndActivityStreams_inThatOrder() {
        Map<String, Object> doc = mapping.apply(samplePost());

        assertThat(doc.get("@context")).isInstanceOf(List.class);
        // AS 2.0 first, Schema.org second — preserves the precedence
        // documented in the issue body.
        assertThat(doc.get("@context")).isEqualTo(List.of(
                JsonLdContext.ACTIVITY_STREAMS, JsonLdContext.SCHEMA_ORG));
    }

    @Test
    void apply_emitsBothTypesAndCanonicalIri() {
        Map<String, Object> doc = mapping.apply(samplePost());

        assertThat(doc.get("@type")).isEqualTo(List.of("Note", "SocialMediaPosting"));
        assertThat(doc.get("@id")).isEqualTo("https://api.example.com/api/feed/posts/42");
        // `id` is the AS 2.0 alias; both must resolve to the same canonical
        // URL so consumers indexing on either field don't fork.
        assertThat(doc.get("id")).isEqualTo(doc.get("@id"));
    }

    @Test
    void apply_emitsAttributedToWithPersonIri() {
        Map<String, Object> doc = mapping.apply(samplePost());

        Object actor = doc.get("attributedTo");
        assertThat(actor).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> person = (Map<String, Object>) actor;
        assertThat(person).containsEntry("@type", "Person");
        assertThat(person).containsEntry("@id", "https://api.example.com/api/users/17");
        assertThat(person).containsEntry("name", "Ada");
    }

    @Test
    void apply_emitsContentMapAndInLanguage_whenLangPresent() {
        FeedPostResponse post = samplePost("en");
        Map<String, Object> doc = mapping.apply(post);

        assertThat(doc).containsEntry("content", post.body());
        assertThat(doc.get("contentMap")).isEqualTo(Map.of("en", post.body()));
        assertThat(doc).containsEntry("inLanguage", "en");
    }

    @Test
    void apply_omitsContentMapAndInLanguage_whenLangAbsent() {
        FeedPostResponse post = samplePost(null);
        Map<String, Object> doc = mapping.apply(post);

        assertThat(doc).containsEntry("content", post.body());
        assertThat(doc).doesNotContainKey("contentMap");
        assertThat(doc).doesNotContainKey("inLanguage");
    }

    @Test
    void apply_emitsHashtagsAsAS2Hashtag_withHashPrefix() {
        Map<String, Object> doc = mapping.apply(samplePost());

        Object tags = doc.get("tag");
        assertThat(tags).isInstanceOf(List.class);
        List<?> tagList = (List<?>) tags;
        // Hashtag values in AS 2.0 use the leading `#` so consumers can
        // render them verbatim. The stored DB value drops the `#` for
        // index hygiene; we re-add it on the wire.
        assertThat(tagList).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) tagList.get(0);
        assertThat(first).containsEntry("type", "Hashtag");
        assertThat(first).containsEntry("name", "#datascience");
    }

    @Test
    void apply_emitsInteractionStatistic_withAllFourCounters() {
        Map<String, Object> doc = mapping.apply(samplePost());

        Object stats = doc.get("interactionStatistic");
        assertThat(stats).isInstanceOf(List.class);
        List<?> statList = (List<?>) stats;
        assertThat(statList).hasSize(4);
        // The detail DTO never carries live counts; counters are emitted
        // with zeros for shape stability so consumers can rely on the
        // four-tuple always existing.
        @SuppressWarnings("unchecked")
        Map<String, Object> likeCounter = (Map<String, Object>) statList.get(0);
        assertThat(likeCounter).containsEntry("@type", "InteractionCounter");
        assertThat(likeCounter).containsEntry("interactionType", "https://schema.org/LikeAction");
        assertThat(likeCounter).containsEntry("userInteractionCount", 0L);
    }

    @Test
    void apply_emitsAttachments_whenPostHasMedia() {
        FeedPostResponse post = samplePostWithAttachment();
        Map<String, Object> doc = mapping.apply(post);

        Object attachments = doc.get("attachment");
        assertThat(attachments).isInstanceOf(List.class);
        List<?> list = (List<?>) attachments;
        assertThat(list).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) list.get(0);
        assertThat(first).containsEntry("type", "Image");
        assertThat(first).containsEntry("url", "https://api.example.com/api/uploads/feed-media/1");
        assertThat(first).containsEntry("mediaType", "image/png");
    }

    @Test
    void apply_omitsUpdated_whenPostNotEdited() {
        FeedPostResponse post = samplePost();   // isEdited=false in fixture
        Map<String, Object> doc = mapping.apply(post);

        assertThat(doc).doesNotContainKey("updated");
    }

    @Test
    void apply_emitsUpdated_whenPostEdited() {
        OffsetDateTime created = OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime edited = created.plusHours(2);
        FeedPostResponse post = new FeedPostResponse(
                42L, 17L, "Ada", "edited body", List.of(),
                created, edited, true, false, List.of(), false, false, null);

        Map<String, Object> doc = mapping.apply(post);

        assertThat(doc.get("updated")).isEqualTo(edited.toString());
    }

    // ── Fixtures ────────────────────────────────────────────────

    private static FeedPostResponse samplePost() {
        return samplePost(null);
    }

    private static FeedPostResponse samplePost(String lang) {
        OffsetDateTime created = OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        return new FeedPostResponse(
                42L, 17L, "Ada",
                "Excited to share thoughts on data science.",
                List.of("datascience", "nlp"),
                created, created, false, false, List.of(), false, false, lang);
    }

    private static FeedPostResponse samplePostWithAttachment() {
        OffsetDateTime created = OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        AttachmentSummary attachment = new AttachmentSummary();
        attachment.setDownloadUrl("https://api.example.com/api/uploads/feed-media/1");
        attachment.setContentType("image/png");
        return new FeedPostResponse(
                42L, 17L, "Ada", "see this",
                List.of(),
                created, created, false, false, List.of(attachment), false, false, "en");
    }
}
