package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Mints canonical IRIs for the social-feed entities that surface in the
 * Activity Streams 2.0 / Schema.org JSON-LD representations.
 *
 * <p>The base URL is sourced from {@code app.base-url} (the same property
 * the email link generator and {@code NoteMapping} use) so the IRI shape
 * is identical to whatever {@code GET /api/feed/posts/{id}} would resolve
 * to over the real HTTP API. That equality is load-bearing for AS 2.0
 * dereferenceability: a consumer that follows an IRI we mint here must
 * land on the actual resource, not a 404.
 *
 * <p>Built once per app boot and held as a final field. The trailing slash
 * is stripped exactly once at construction so callers can concatenate the
 * path segment without checking the base shape.
 */
@Component
public class FeedIriBuilder {

    private final String baseUrl;

    public FeedIriBuilder(AppProperties appProperties) {
        String configured = appProperties.getBaseUrl();
        this.baseUrl = configured.endsWith("/")
                ? configured.substring(0, configured.length() - 1)
                : configured;
    }

    /** Canonical IRI for a single feed post. Matches {@code GET /api/feed/posts/{id}}. */
    public String post(Long id) {
        return baseUrl + "/api/feed/posts/" + id;
    }

    /** Canonical IRI for a single comment. Matches {@code GET /api/feed/comments/{id}}. */
    public String comment(Long id) {
        return baseUrl + "/api/feed/comments/" + id;
    }

    /**
     * IRI for the abstract Like edge from a user to a post. Not a real
     * endpoint — the toggle surface lives at
     * {@code POST /api/feed/posts/{id}/like} — but AS 2.0 consumers expect
     * each Like activity to carry a stable id, so we mint one with the
     * composite key shape.
     */
    public String like(Long postId, Long userId) {
        return baseUrl + "/api/feed/posts/" + postId + "/likes/" + userId;
    }

    /** IRI for the abstract Share edge. Same shape rationale as {@link #like}. */
    public String share(Long postId, Long userId) {
        return baseUrl + "/api/feed/posts/" + postId + "/shares/" + userId;
    }

    /** IRI for the abstract Bookmark edge. Same shape rationale as {@link #like}. */
    public String bookmark(Long postId, Long userId) {
        return baseUrl + "/api/feed/posts/" + postId + "/bookmarks/" + userId;
    }

    /** IRI for the abstract Follow edge from one user to another. */
    public String follow(Long followerId, Long followeeId) {
        return baseUrl + "/api/follows/" + followerId + "/" + followeeId;
    }

    /** IRI for the user's bookmark collection (the {@code target} of {@code as:Add}). */
    public String bookmarkCollection(Long userId) {
        return baseUrl + "/api/users/" + userId + "/bookmarks";
    }

    /** Canonical IRI for a user (Schema.org Person). */
    public String person(Long userId) {
        return baseUrl + "/api/users/" + userId;
    }

    /**
     * Resolves the absolute URL form of a relative resource path. Used when
     * the OrderedCollectionPage envelope needs to render the {@code first}
     * / {@code next} / {@code prev} / {@code last} hypermedia links from
     * the controller-level URI plus the framework's pagination state.
     */
    public String absolute(String pathAndQuery) {
        if (pathAndQuery == null || pathAndQuery.isEmpty()) {
            return baseUrl;
        }
        return pathAndQuery.startsWith("http://") || pathAndQuery.startsWith("https://")
                ? pathAndQuery
                : baseUrl + (pathAndQuery.startsWith("/") ? pathAndQuery : "/" + pathAndQuery);
    }
}
