package com.group7.backend.service;

import com.group7.backend.dto.feed.FeedPostLimits;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalises raw hashtag strings into a deterministic, deduplicated,
 * insertion-order-preserving set ready for persistence (#348).
 *
 * <p>Standalone {@code @Component} so {@code #347} (if comment hashtags
 * land), {@code #350} (search input must apply the same normalisation
 * to match stored tags), and {@code #349} (fanout filtering by tag
 * overlap) all reuse this single source of truth — inlining as a
 * private service method invites copy-paste drift.
 *
 * <h2>Discipline</h2>
 * <ul>
 *   <li><b>{@link Locale#ROOT} lowercasing.</b> Non-negotiable.
 *       {@code "İSTANBUL".toLowerCase()} differs across JVMs depending
 *       on default locale and would split {@code "istanbul"} and
 *       {@code "i̇stanbul"} into two distinct tags, corrupting the
 *       {@code (post_id, tag)} PK uniqueness.</li>
 *   <li><b>{@link Pattern#UNICODE_CHARACTER_CLASS} regex.</b> Accepts
 *       Turkish characters and any other Unicode letters / digits the
 *       platform's user base writes in. Matches industry-standard
 *       hashtag conventions (Twitter / Instagram are case-insensitive
 *       and accept Unicode word characters).</li>
 *   <li><b>Silent drop of malformed tags</b> (whitespace, invalid chars,
 *       empty after normalisation) — the user gets a best-effort save
 *       rather than a 400 for a stray malformed tag among otherwise
 *       valid input.</li>
 *   <li><b>Hard-cap at {@link FeedPostLimits#MAX_HASHTAGS} valid tags.</b>
 *       Throws {@link IllegalArgumentException} → 400 if exceeded after
 *       normalisation. This is a contract violation, not a normalisation
 *       choice. The DTO {@code @Size(max = MAX_HASHTAGS)} catches the
 *       same condition at the controller boundary first; this cap is
 *       defence-in-depth for any future call site that bypasses the DTO.</li>
 * </ul>
 *
 * <h2>Known edge cases (out of scope)</h2>
 * <ul>
 *   <li>German ß → {@code "FUSSBALL"} lowercased is {@code "fussball"};
 *       Unicode case-folding would map this differently. {@code Locale.ROOT}
 *       lowercase matches Twitter / Instagram behaviour.</li>
 *   <li>Greek final-sigma (Σ → σ vs ς) is locale-dependent at the rare
 *       end of the spectrum and similarly accepted as best-effort here.</li>
 * </ul>
 */
@Component
public class HashtagNormalizer {

    private static final Pattern TAG_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}_]{1," + FeedPostLimits.MAX_HASHTAG_LENGTH + "}$",
                    Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern LEADING_HASHES = Pattern.compile("^#+");

    /**
     * Normalises the input list. {@code null} input returns an empty set.
     * Each element is trimmed, has any leading {@code #} characters
     * stripped, lowercased via {@link Locale#ROOT}, and validated against
     * the regex; malformed elements are silently dropped. Duplicates
     * dedupe (first-seen order preserved by {@link LinkedHashSet}). After
     * normalisation, if the surviving count exceeds
     * {@link FeedPostLimits#MAX_HASHTAGS}, an
     * {@link IllegalArgumentException} is thrown (mapped to 400 by
     * {@code GlobalExceptionHandler}).
     */
    public Set<String> normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalised = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String trimmed = LEADING_HASHES.matcher(s.trim()).replaceFirst("");
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!TAG_PATTERN.matcher(lower).matches()) {
                continue;
            }
            normalised.add(lower);
        }
        if (normalised.size() > FeedPostLimits.MAX_HASHTAGS) {
            throw new IllegalArgumentException(
                    "Hashtag cap exceeded: " + normalised.size()
                            + " (max " + FeedPostLimits.MAX_HASHTAGS + ")");
        }
        return normalised;
    }
}
