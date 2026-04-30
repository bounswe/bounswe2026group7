package com.group7.backend.config.jsonld;

import org.springframework.http.MediaType;

public final class JsonLdMediaType {

    public static final String APPLICATION_LD_JSON_VALUE = "application/ld+json";
    public static final MediaType APPLICATION_LD_JSON = MediaType.parseMediaType(APPLICATION_LD_JSON_VALUE);

    private JsonLdMediaType() {
    }
}
