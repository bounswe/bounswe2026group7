package com.group7.backend.config.jsonld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wraps controller responses as JSON-LD when the client requests
 * {@code Accept: application/ld+json}.
 *
 * Contract: if the response Content-Type is {@code application/ld+json},
 * the body IS valid JSON-LD. When we cannot produce a JSON-LD shape (no
 * registered mapping, heterogeneous collection, {@link Page} wrapper, etc.),
 * the Content-Type is downgraded to {@code application/json} and the body
 * passes through unchanged.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class JsonLdResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(JsonLdResponseBodyAdvice.class);

    private final List<JsonLdMapping> mappings;

    public JsonLdResponseBodyAdvice(List<JsonLdMapping> mappings) {
        this.mappings = List.copyOf(mappings);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !mappings.isEmpty();
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body == null) {
            return null;
        }
        if (!JsonLdMediaType.APPLICATION_LD_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }

        // Page<T> wrapping in JSON-LD requires modeling pagination as a typed
        // resource (e.g., schema.org ItemList). Deferred to a Wave 2 slice.
        if (body instanceof Page<?>) {
            return downgrade(body, response, "Page<T> body is not JSON-LD-shaped");
        }

        if (body instanceof Collection<?> collection) {
            return transformCollection(collection, response);
        }

        // UserProfileResponse (#343) wraps the existing sealed ProfileResponse
        // with follower / following counts. The JSON-LD mapping registry is
        // keyed off the inner profile's class (PersonMapping handles
        // MentorResponse / MenteeResponse). Unwrap before lookup so the
        // existing schema.org Person shape is preserved; the follower /
        // following counts are not part of schema.org Person and are
        // intentionally dropped from the JSON-LD response.
        Object effectiveBody = (body instanceof com.group7.backend.dto.response.UserProfileResponse wrapper
                && wrapper.getProfile() != null)
                ? wrapper.getProfile()
                : body;

        return findMappingFor(effectiveBody.getClass())
                .<Object>map(m -> m.apply(effectiveBody))
                .orElseGet(() -> downgrade(body, response,
                        "no JSON-LD mapping for " + effectiveBody.getClass().getSimpleName()));
    }

    private Optional<JsonLdMapping> findMappingFor(Class<?> bodyType) {
        return mappings.stream().filter(m -> m.supports(bodyType)).findFirst();
    }

    private Object transformCollection(Collection<?> collection, ServerHttpResponse response) {
        if (collection.isEmpty()) {
            return wrapAsGraph(List.of());
        }

        Object firstNonNull = null;
        for (Object element : collection) {
            if (element != null) {
                firstNonNull = element;
                break;
            }
        }
        if (firstNonNull == null) {
            return downgrade(collection, response, "collection contains only null elements");
        }

        Optional<JsonLdMapping> maybeMapping = findMappingFor(firstNonNull.getClass());
        if (maybeMapping.isEmpty()) {
            return downgrade(collection, response,
                    "no JSON-LD mapping for collection elements of " + firstNonNull.getClass().getSimpleName());
        }
        JsonLdMapping mapping = maybeMapping.get();

        for (Object element : collection) {
            if (element != null && !mapping.supports(element.getClass())) {
                return downgrade(collection, response,
                        "heterogeneous collection (first=" + firstNonNull.getClass().getSimpleName()
                                + ", found=" + element.getClass().getSimpleName() + ")");
            }
        }

        List<Map<String, Object>> items = new ArrayList<>(collection.size());
        for (Object element : collection) {
            if (element == null) {
                continue;
            }
            Map<String, Object> mapped = new LinkedHashMap<>(mapping.apply(element));
            mapped.remove("@context");
            items.add(mapped);
        }
        return wrapAsGraph(items);
    }

    private static Object downgrade(Object body, ServerHttpResponse response, String reason) {
        log.debug("ld+json downgrade to application/json: {}", reason);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return body;
    }

    private static Map<String, Object> wrapAsGraph(List<?> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("@context", JsonLdContext.SCHEMA_ORG);
        result.put("@graph", items);
        return result;
    }
}
