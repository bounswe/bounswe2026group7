package com.group7.backend.service.embedding;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compact, null-safe templates for the text that feeds the embedding
 * model. Lives outside the signal hierarchy because multiple consumers
 * need to derive the same string from a {@link FeedPost} (or a viewer's
 * interest set) to produce the same cache key:
 *
 * <ul>
 *   <li>{@code SemanticMatchSignal} embeds these strings during scoring.</li>
 *   <li>{@code ForYouScoringPipeline} re-derives them when computing
 *       post-to-post embedding distances for MMR diversity.</li>
 * </ul>
 *
 * <p>Mirrors the mentor-side {@link MentorProfileText}: owning the
 * template here keeps the strategy pattern clean and the cache key
 * deterministic across consumers.
 */
public final class FeedPostEmbeddingText {

    private FeedPostEmbeddingText() {}

    /**
     * Post side: body + space-joined hashtags. Skips blank segments so a
     * tags-only post still embeds cleanly (an image-only post with three
     * tags is "{@code #tag1 #tag2 #tag3}" — short, but it stays in the
     * candidate window of the embedding cache).
     */
    public static String forPost(FeedPost post) {
        if (post == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (notBlank(post.getBody())) {
            sb.append(post.getBody().strip());
        }
        if (post.getHashtags() != null && !post.getHashtags().isEmpty()) {
            String joined = post.getHashtags().stream()
                    .map(FeedPostHashtag::getId)
                    .map(id -> id.getTag())
                    .filter(FeedPostEmbeddingText::notBlank)
                    .sorted()
                    .collect(Collectors.joining(" "));
            if (notBlank(joined)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(joined);
            }
        }
        return sb.toString().strip();
    }

    /**
     * Viewer side: space-joined interest hashtags. Normalized tags are
     * already stripped of {@code #} and lowercased, so this is a stable
     * sorted join. Empty when the viewer has no declared interests.
     */
    public static String forViewerInterests(Set<String> interestHashtags) {
        if (interestHashtags == null || interestHashtags.isEmpty()) {
            return "";
        }
        return interestHashtags.stream()
                .filter(FeedPostEmbeddingText::notBlank)
                .sorted()
                .collect(Collectors.joining(" "));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
