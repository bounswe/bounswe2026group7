package com.group7.backend.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Static helpers for the parallel label/URI list shape that profile
 * payloads use on the wire. Profile fields like {@code interests} arrive as
 * two equally-indexed lists ({@code interests}, {@code interestUris}); the
 * domain stores them paired as {@link TaggedTerm} entries.
 *
 * <p>Centralising the conversion here means the entity setters, the
 * {@code @AssertTrue} validator on request DTOs, and the service-layer
 * apply-fields helpers all agree on the same invariant: when a URI list is
 * supplied, it MUST have the same length as its label list.
 */
public final class TaggedTermLists {

    private TaggedTermLists() {}

    /**
     * Pairs labels with URIs into TaggedTerm entries.
     *
     * @param labels label list; null means "field absent" — returns null
     * @param uris   parallel URI list, or null for legacy free-text payload
     *               (yields entries with null identifierUri)
     * @return List of TaggedTerm entries, or null when {@code labels} is null
     * @throws IllegalArgumentException when {@code uris} is non-null and
     *         its length differs from {@code labels.size()}. Request DTOs
     *         catch this earlier via {@link #aligned(List, List)}; the throw
     *         here is defence in depth for non-DTO call sites.
     */
    public static List<TaggedTerm> combine(List<String> labels, List<String> uris) {
        if (labels == null) {
            return null;
        }
        if (uris != null && uris.size() != labels.size()) {
            throw new IllegalArgumentException(
                    "URI list length must equal label list length");
        }
        List<TaggedTerm> entries = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            entries.add(new TaggedTerm(labels.get(i), uris == null ? null : uris.get(i)));
        }
        return entries;
    }

    /**
     * True if a parallel label/URI list pair is consistent for use with
     * {@link #combine(List, List)}: a null URI list is always fine; an
     * empty URI list aligns only with an empty (or null) label list. Used
     * by the {@code @AssertTrue} request-DTO validators to reject
     * mismatched payloads with HTTP 400 before the service layer runs.
     */
    public static boolean aligned(List<?> labels, List<?> uris) {
        if (uris == null) {
            return true;
        }
        if (labels == null) {
            return false;
        }
        return labels.size() == uris.size();
    }
}
