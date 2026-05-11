package com.group7.backend.service.embedding;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeedPostEmbeddingTextTest {

    @Test
    void nullPost_returnsEmptyString() {
        assertThat(FeedPostEmbeddingText.forPost(null)).isEmpty();
    }

    @Test
    void postWithBodyAndHashtags_concatenatesBothInAlphabeticalTagOrder() {
        FeedPost p = post("Excited about ML breakthroughs!", List.of("ml", "ai", "data"));
        assertThat(FeedPostEmbeddingText.forPost(p))
                .isEqualTo("Excited about ML breakthroughs! ai data ml");
    }

    @Test
    void postWithEmptyBody_returnsHashtagsOnly() {
        FeedPost p = post("", List.of("ml", "ai"));
        assertThat(FeedPostEmbeddingText.forPost(p)).isEqualTo("ai ml");
    }

    @Test
    void postWithBodyAndNoTags_returnsBodyOnly() {
        FeedPost p = post("Solo thoughts", List.of());
        assertThat(FeedPostEmbeddingText.forPost(p)).isEqualTo("Solo thoughts");
    }

    @Test
    void postWithNothing_returnsEmptyString() {
        FeedPost p = post("", List.of());
        assertThat(FeedPostEmbeddingText.forPost(p)).isEmpty();
    }

    @Test
    void postWithWhitespaceBody_skipsBlankBodySegment() {
        FeedPost p = post("   ", List.of("ai"));
        assertThat(FeedPostEmbeddingText.forPost(p)).isEqualTo("ai");
    }

    @Test
    void viewerInterests_alphabeticalSpaceJoined() {
        assertThat(FeedPostEmbeddingText.forViewerInterests(Set.of("ml", "ai", "data")))
                .isEqualTo("ai data ml");
    }

    @Test
    void viewerInterestsEmpty_returnsEmptyString() {
        assertThat(FeedPostEmbeddingText.forViewerInterests(null)).isEmpty();
        assertThat(FeedPostEmbeddingText.forViewerInterests(Set.of())).isEmpty();
    }

    @Test
    void viewerInterestsWithBlankEntries_skipsBlanks() {
        assertThat(FeedPostEmbeddingText.forViewerInterests(Set.of("ai", " ", "")))
                .isEqualTo("ai");
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static FeedPost post(String body, List<String> tags) {
        FeedPost p = new FeedPost(99L, body);
        p.setId(1L);
        LinkedHashSet<FeedPostHashtag> hashtags = new LinkedHashSet<>();
        for (String t : tags) {
            hashtags.add(new FeedPostHashtag(p, t));
        }
        p.setHashtags(hashtags);
        return p;
    }
}
