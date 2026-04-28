package com.group7.backend.config.jsonld;

import java.util.Map;

public interface JsonLdMapping {

    boolean supports(Class<?> bodyType);

    Map<String, Object> apply(Object body);
}
