package com.group7.backend.config.jsonld;

import org.springframework.http.MediaType;

public final class JsonLdMediaType {

    public static final String APPLICATION_LD_JSON_VALUE = "application/ld+json";
    public static final MediaType APPLICATION_LD_JSON = MediaType.parseMediaType(APPLICATION_LD_JSON_VALUE);

    /**
     * ActivityPub / Activity Streams 2.0 wire type. The AS 2.0 spec defines
     * this as the canonical type for AS 2.0 documents, and Fediverse clients
     * (Mastodon, etc.) prefer it over the generic application/ld+json. Treated
     * as a synonym by the JSON-LD response advice — any client opting in via
     * either header receives the same AS 2.0 document on the wire.
     */
    public static final String APPLICATION_ACTIVITY_JSON_VALUE = "application/activity+json";
    public static final MediaType APPLICATION_ACTIVITY_JSON = MediaType.parseMediaType(APPLICATION_ACTIVITY_JSON_VALUE);

    /**
     * True for either of the two AS 2.0 / JSON-LD wire types. The response
     * advice opts in to wrapping when the negotiated content type satisfies
     * this predicate.
     */
    public static boolean isJsonLd(MediaType contentType) {
        return contentType != null
                && (APPLICATION_LD_JSON.isCompatibleWith(contentType)
                || APPLICATION_ACTIVITY_JSON.isCompatibleWith(contentType));
    }

    private JsonLdMediaType() {
    }
}
