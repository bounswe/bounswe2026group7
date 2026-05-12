package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.jsonld.JsonLdContext;
import com.group7.backend.config.jsonld.JsonLdMapping;
import com.group7.backend.config.jsonld.JsonLdMediaType;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wraps {@link Page Page&lt;T&gt;} responses as an Activity Streams 2.0
 * {@code OrderedCollectionPage} when the client requests JSON-LD via
 * either {@code application/ld+json} or {@code application/activity+json}.
 *
 * <p>The previous behaviour was to downgrade Page bodies to plain JSON.
 * That broke the standards-coverage commitment for the feed list
 * endpoints (For-You, Following, search, author posts), all of which
 * paginate. This advice closes the gap by:
 *
 * <ol>
 *   <li>Lifting every page element through the registered
 *       {@link JsonLdMapping} so the items inside {@code orderedItems} are
 *       individual JSON-LD documents (minus their {@code @context}, which
 *       the wrapper carries once).</li>
 *   <li>Synthesising the {@code first} / {@code prev} / {@code next} /
 *       {@code last} hypermedia links from the current request URI plus
 *       the page's pagination metadata.</li>
 *   <li>Setting {@code totalItems} from the page's total element count so
 *       AS 2.0 consumers can render "page N of M" without scanning all
 *       pages.</li>
 * </ol>
 *
 * <p>Order is {@link Ordered#LOWEST_PRECEDENCE} minus one so this runs
 * <i>after</i> {@code JsonLdResponseBodyAdvice} for individual bodies but
 * still inside the {@code ResponseBodyAdvice} chain. The exact ordering
 * doesn't matter for Page bodies because {@code JsonLdResponseBodyAdvice}
 * passes Page through unchanged — we own the transformation here.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class OrderedCollectionPageJsonLdAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(OrderedCollectionPageJsonLdAdvice.class);

    private final List<JsonLdMapping> mappings;

    public OrderedCollectionPageJsonLdAdvice(List<JsonLdMapping> mappings) {
        this.mappings = List.copyOf(mappings);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(body instanceof Page<?> page)) {
            return body;
        }
        if (!JsonLdMediaType.isJsonLd(selectedContentType)) {
            return body;
        }

        Optional<JsonLdMapping> mapping = findMappingForPage(page);
        if (mapping.isEmpty()) {
            // Downgrade to plain JSON: every consumer for which we lack a
            // mapping would otherwise see a malformed AS 2.0 document.
            // Same fallback behaviour as JsonLdResponseBodyAdvice.
            log.debug("OrderedCollectionPage downgrade: no JSON-LD mapping for elements");
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return body;
        }
        JsonLdMapping bound = mapping.get();

        List<Map<String, Object>> orderedItems = new ArrayList<>(page.getContent().size());
        for (Object item : page.getContent()) {
            if (item == null) {
                continue;
            }
            Map<String, Object> mapped = new LinkedHashMap<>(bound.apply(item));
            // The outer envelope carries @context; stripping it from each
            // inner item keeps the document shape clean and aligns with
            // the AS 2.0 examples for OrderedCollectionPage.
            mapped.remove("@context");
            orderedItems.add(mapped);
        }

        URI requestUri = uriFor(request);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("@context", List.of(
                JsonLdContext.ACTIVITY_STREAMS,
                JsonLdContext.SCHEMA_ORG));
        envelope.put("@type", "OrderedCollectionPage");
        envelope.put("id", requestUri.toString());
        envelope.put("partOf", stripPageQueryParams(requestUri).toString());
        envelope.put("totalItems", page.getTotalElements());

        // Page numbers are 0-based in Spring Data, but AS 2.0 imposes no
        // convention; we preserve Spring's numbering so the synthesised
        // URLs match what the controller already publishes elsewhere.
        int currentPage = page.getNumber();
        int size = page.getSize();
        int totalPages = Math.max(page.getTotalPages(), 1);

        envelope.put("first", pageUri(requestUri, 0, size).toString());
        envelope.put("last", pageUri(requestUri, totalPages - 1, size).toString());
        if (currentPage > 0) {
            envelope.put("prev", pageUri(requestUri, currentPage - 1, size).toString());
        }
        if (currentPage < totalPages - 1) {
            envelope.put("next", pageUri(requestUri, currentPage + 1, size).toString());
        }
        envelope.put("orderedItems", orderedItems);

        // The downstream Jackson converter respects an already-set
        // Content-Type but it's set on a different headers reference than
        // Spring's outer ServerHttpResponse for some converter flavours.
        // Reaching through the underlying servlet response and writing
        // the header directly forces the wire Content-Type to match what
        // the client negotiated (e.g., application/activity+json) instead
        // of silently downgrading to application/json.
        if (response instanceof org.springframework.http.server.ServletServerHttpResponse servletResponse) {
            servletResponse.getServletResponse().setContentType(selectedContentType.toString());
        } else {
            response.getHeaders().setContentType(selectedContentType);
        }
        return envelope;
    }

    private Optional<JsonLdMapping> findMappingForPage(Page<?> page) {
        for (Object element : page.getContent()) {
            if (element == null) {
                continue;
            }
            Class<?> elementType = element.getClass();
            return mappings.stream().filter(m -> m.supports(elementType)).findFirst();
        }
        // Empty page — pick the first mapping that exists so a zero-result
        // search still emits a well-formed OrderedCollectionPage. The
        // mapping isn't applied to anything (no items), so any choice is
        // structurally equivalent; we just need a non-null arbiter to
        // signal "we know how to render this list type".
        return mappings.stream().findFirst();
    }

    private static URI uriFor(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest http = servletRequest.getServletRequest();
            return UriComponentsBuilder
                    .fromHttpUrl(http.getRequestURL().toString())
                    .query(http.getQueryString())
                    .build(true)
                    .toUri();
        }
        return request.getURI();
    }

    private static URI pageUri(URI base, int page, int size) {
        return UriComponentsBuilder.fromUri(base)
                .replaceQueryParam("page", page)
                .replaceQueryParam("size", size)
                .build(true)
                .toUri();
    }

    private static URI stripPageQueryParams(URI base) {
        return UriComponentsBuilder.fromUri(base)
                .replaceQueryParam("page")
                .replaceQueryParam("size")
                .build(true)
                .toUri();
    }
}
