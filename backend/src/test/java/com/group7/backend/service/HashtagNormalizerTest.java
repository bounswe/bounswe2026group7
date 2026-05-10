package com.group7.backend.service;

import com.group7.backend.dto.feed.FeedPostLimits;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for {@link HashtagNormalizer} (#348).
 *
 * <p>The normaliser is a pure function (no DB, no clock, no Spring
 * context); each test exercises one shape of input.
 */
class HashtagNormalizerTest {

    private final HashtagNormalizer normalizer = new HashtagNormalizer();

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test
    void simpleTags_lowercased_andOrderPreserved() {
        Set<String> result = normalizer.normalize(List.of("DataScience", "AI", "ML"));
        assertThat(result).containsExactly("datascience", "ai", "ml");
    }

    @Test
    void leadingHash_isStripped() {
        Set<String> result = normalizer.normalize(List.of("#data", "#NLP", "#ai"));
        assertThat(result).containsExactly("data", "nlp", "ai");
    }

    @Test
    void multipleLeadingHashes_areAllStripped() {
        Set<String> result = normalizer.normalize(List.of("##data", "###nlp"));
        assertThat(result).containsExactly("data", "nlp");
    }

    @Test
    void surroundingWhitespace_isTrimmed() {
        Set<String> result = normalizer.normalize(List.of("  data  ", "\tnlp\n"));
        assertThat(result).containsExactly("data", "nlp");
    }

    // ── Unicode / Turkish ──────────────────────────────────────────────────

    @Test
    void turkishCharacters_arePreserved() {
        Set<String> result = normalizer.normalize(
                List.of("#yapayzeka", "#mühendislik", "#öğrenci"));
        assertThat(result).containsExactly("yapayzeka", "mühendislik", "öğrenci");
    }

    @Test
    void mixedCaseUnicode_isLowercased_andDeduped() {
        // Locale.ROOT lowercase: "YAPAYZEKA" → "yapayzeka"
        Set<String> result = normalizer.normalize(List.of("#YapayZeka", "#YAPAYZEKA", "#yapayzeka"));
        assertThat(result).containsExactly("yapayzeka");
    }

    // ── Drops + dedupe ─────────────────────────────────────────────────────

    @Test
    void nullInput_returnsEmpty() {
        assertThat(normalizer.normalize(null)).isEmpty();
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertThat(normalizer.normalize(List.of())).isEmpty();
    }

    @Test
    void nullEntries_areSilentlyDropped() {
        Set<String> result = normalizer.normalize(Arrays.asList("data", null, "nlp"));
        assertThat(result).containsExactly("data", "nlp");
    }

    @Test
    void emptyAndWhitespaceEntries_areDropped() {
        Set<String> result = normalizer.normalize(List.of("", "   ", "#", "##", "data"));
        assertThat(result).containsExactly("data");
    }

    @Test
    void multiWordEntries_areDropped() {
        // " " is not a valid hashtag character; "data science" fails the regex.
        Set<String> result = normalizer.normalize(List.of("data science", "#machine learning", "data"));
        assertThat(result).containsExactly("data");
    }

    @Test
    void specialCharacterEntries_areDropped() {
        Set<String> result = normalizer.normalize(List.of("data!", "AI?", "hash#tag", "good"));
        assertThat(result).containsExactly("good");
    }

    @Test
    void duplicateAfterNormalisation_isDeduped_firstWins() {
        Set<String> result = normalizer.normalize(List.of("#Data", "data", "DATA", "DAtA"));
        assertThat(result).containsExactly("data");
    }

    // ── Length limits ──────────────────────────────────────────────────────

    @Test
    void tagAtMaxLength_isAccepted() {
        String maxLength = "a".repeat(FeedPostLimits.MAX_HASHTAG_LENGTH);
        Set<String> result = normalizer.normalize(List.of(maxLength));
        assertThat(result).containsExactly(maxLength);
    }

    @Test
    void tagOverMaxLength_isDropped() {
        String tooLong = "a".repeat(FeedPostLimits.MAX_HASHTAG_LENGTH + 1);
        Set<String> result = normalizer.normalize(List.of(tooLong, "ok"));
        assertThat(result).containsExactly("ok");
    }

    // ── Cap enforcement ────────────────────────────────────────────────────

    @Test
    void atMostMaxHashtags_isAccepted() {
        List<String> input = IntStream.range(0, FeedPostLimits.MAX_HASHTAGS)
                .mapToObj(i -> "tag" + i)
                .collect(Collectors.toList());
        Set<String> result = normalizer.normalize(input);
        assertThat(result).hasSize(FeedPostLimits.MAX_HASHTAGS);
    }

    @Test
    void overCap_throwsIllegalArgumentException() {
        List<String> input = IntStream.range(0, FeedPostLimits.MAX_HASHTAGS + 1)
                .mapToObj(i -> "tag" + i)
                .collect(Collectors.toList());
        assertThatThrownBy(() -> normalizer.normalize(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hashtag cap exceeded");
    }

    @Test
    void cap_isAppliedAfterNormalisation_notBefore() {
        // 12 raw tags but 5 are duplicates after lowercase + dedupe, so 7
        // valid distinct tags remain — under the cap of 10, no exception.
        List<String> input = List.of(
                "Data", "data", "DATA",          // dedup → "data"
                "AI", "ai",                       // dedup → "ai"
                "ML", "ml", "ml",                 // dedup → "ml"
                "NLP", "Stats", "Cloud", "Web"   // 4 more
        );
        Set<String> result = normalizer.normalize(input);
        assertThat(result).hasSize(7);
    }
}
