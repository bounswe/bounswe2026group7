package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.AppProperties;
import com.group7.backend.dto.response.FeedCommentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the comment mapping's two non-obvious contracts: the
 * {@code inReplyTo} link to the parent post IRI and the soft-delete
 * tombstone branch (deleted comments omit {@code content} entirely
 * rather than emitting an empty string).
 */
class FeedCommentJsonLdMappingTest {

    private FeedCommentJsonLdMapping mapping;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("https://api.example.com");
        mapping = new FeedCommentJsonLdMapping(new FeedIriBuilder(props));
    }

    @Test
    void supports_acceptsFeedCommentResponse() {
        assertThat(mapping.supports(FeedCommentResponse.class)).isTrue();
        assertThat(mapping.supports(String.class)).isFalse();
    }

    @Test
    void apply_emitsNoteWithInReplyToParentPost() {
        FeedCommentResponse c = comment(101L, 7L, 1L, "great post", false);
        Map<String, Object> doc = mapping.apply(c);

        assertThat(doc.get("@type")).isEqualTo("Note");
        assertThat(doc.get("@id")).isEqualTo("https://api.example.com/api/feed/comments/101");
        assertThat(doc.get("inReplyTo")).isEqualTo("https://api.example.com/api/feed/posts/7");
        assertThat(doc.get("content")).isEqualTo("great post");
    }

    @Test
    void apply_emitsAttributedToPersonActor() {
        FeedCommentResponse c = comment(101L, 7L, 1L, "x", false);
        Map<String, Object> doc = mapping.apply(c);

        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) doc.get("attributedTo");
        assertThat(actor.get("@type")).isEqualTo("Person");
        assertThat(actor.get("@id")).isEqualTo("https://api.example.com/api/users/1");
        assertThat(actor.get("name")).isEqualTo("Ada");
    }

    @Test
    void apply_softDeletedComment_omitsContentEntirely() {
        FeedCommentResponse tombstone = new FeedCommentResponse(
                101L, 7L, 1L, "Ada", null,
                OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                false, false, true, 0L, false);   // isDeleted = true

        Map<String, Object> doc = mapping.apply(tombstone);

        // Mastodon-style consumers distinguish redacted from literally
        // empty by whether `content` is present at all; emitting an empty
        // string would conflate the two cases.
        assertThat(doc).doesNotContainKey("content");
    }

    @Test
    void apply_omitsUpdated_whenCommentNotEdited() {
        FeedCommentResponse c = comment(101L, 7L, 1L, "x", false);
        Map<String, Object> doc = mapping.apply(c);

        assertThat(doc).doesNotContainKey("updated");
    }

    @Test
    void apply_emitsUpdated_whenCommentEdited() {
        OffsetDateTime created = OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime edited = created.plusHours(2);
        FeedCommentResponse c = new FeedCommentResponse(
                101L, 7L, 1L, "Ada", "edited body",
                created, edited,
                true, false, false, 0L, false);   // isEdited = true

        Map<String, Object> doc = mapping.apply(c);

        assertThat(doc.get("updated")).isEqualTo(edited.toString());
    }

    // ── Fixtures ────────────────────────────────────────────────

    private static FeedCommentResponse comment(Long id, Long postId, Long authorId,
                                                String body, boolean edited) {
        OffsetDateTime t = OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        return new FeedCommentResponse(
                id, postId, authorId, "Ada", body,
                t, t,
                edited, false, false, 0L, false);
    }
}
