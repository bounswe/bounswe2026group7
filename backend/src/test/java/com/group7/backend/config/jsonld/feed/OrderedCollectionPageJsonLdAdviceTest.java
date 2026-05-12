package com.group7.backend.config.jsonld.feed;

import com.group7.backend.config.AppProperties;
import com.group7.backend.config.jsonld.JsonLdMapping;
import com.group7.backend.config.jsonld.JsonLdMediaType;
import com.group7.backend.dto.response.FeedPostListItem;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the OrderedCollectionPage envelope contract: empty pages still
 * produce well-formed AS 2.0 documents, the four hypermedia paging links
 * appear or disappear based on the page's position, the wrapped items
 * stripped of their inner @context, and the response Content-Type is
 * pinned to the negotiated AS 2.0 wire type instead of silently
 * downgrading to application/json.
 */
class OrderedCollectionPageJsonLdAdviceTest {

    private OrderedCollectionPageJsonLdAdvice advice;
    private FeedPostListItemJsonLdMapping itemMapping;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("https://api.example.com/");
        FeedIriBuilder iri = new FeedIriBuilder(props);
        itemMapping = new FeedPostListItemJsonLdMapping(iri);
        advice = new OrderedCollectionPageJsonLdAdvice(List.<JsonLdMapping>of(itemMapping));
    }

    @Test
    void wraps_emptyPageIntoOrderedCollectionPage_withZeroTotalItems() {
        Page<FeedPostListItem> page = Page.empty(PageRequest.of(0, 10));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) callAdvice(
                page, "/api/feed/for-you", "page=0&size=10",
                JsonLdMediaType.APPLICATION_ACTIVITY_JSON);

        assertThat(envelope.get("@type")).isEqualTo("OrderedCollectionPage");
        assertThat(envelope.get("totalItems")).isEqualTo(0L);
        assertThat(envelope.get("orderedItems")).isEqualTo(List.of());
        // Single empty page → prev / next are both absent; first / last
        // still point at this page so consumers can render a coherent
        // empty-state with a stable id.
        assertThat(envelope).doesNotContainKey("prev");
        assertThat(envelope).doesNotContainKey("next");
        assertThat(envelope.get("first")).isEqualTo("https://api.example.com/api/feed/for-you?page=0&size=10");
        assertThat(envelope.get("last")).isEqualTo("https://api.example.com/api/feed/for-you?page=0&size=10");
    }

    @Test
    void wraps_firstPageOfMany_emitsNextAndLastButNotPrev() {
        Page<FeedPostListItem> page = new PageImpl<>(
                List.of(listItem(101L), listItem(102L)),
                PageRequest.of(0, 2),
                7L);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) callAdvice(
                page, "/api/feed/for-you", "page=0&size=2",
                JsonLdMediaType.APPLICATION_LD_JSON);

        assertThat(envelope.get("totalItems")).isEqualTo(7L);
        // 7 items / size 2 → 4 pages (0..3). Page 0 has next + last but no prev.
        assertThat(envelope).doesNotContainKey("prev");
        assertThat(envelope.get("next")).isEqualTo("https://api.example.com/api/feed/for-you?page=1&size=2");
        assertThat(envelope.get("first")).isEqualTo("https://api.example.com/api/feed/for-you?page=0&size=2");
        assertThat(envelope.get("last")).isEqualTo("https://api.example.com/api/feed/for-you?page=3&size=2");
    }

    @Test
    void wraps_middlePage_emitsAllFourPagingLinks() {
        Page<FeedPostListItem> page = new PageImpl<>(
                List.of(listItem(201L)),
                PageRequest.of(1, 2),
                7L);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) callAdvice(
                page, "/api/feed/for-you", "page=1&size=2",
                JsonLdMediaType.APPLICATION_ACTIVITY_JSON);

        assertThat(envelope.get("prev")).isEqualTo("https://api.example.com/api/feed/for-you?page=0&size=2");
        assertThat(envelope.get("next")).isEqualTo("https://api.example.com/api/feed/for-you?page=2&size=2");
        assertThat(envelope.get("first")).isEqualTo("https://api.example.com/api/feed/for-you?page=0&size=2");
        assertThat(envelope.get("last")).isEqualTo("https://api.example.com/api/feed/for-you?page=3&size=2");
    }

    @Test
    void wraps_lastPage_emitsPrevAndFirstButNotNext() {
        Page<FeedPostListItem> page = new PageImpl<>(
                List.of(listItem(301L)),
                PageRequest.of(3, 2),
                7L);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) callAdvice(
                page, "/api/feed/for-you", "page=3&size=2",
                JsonLdMediaType.APPLICATION_ACTIVITY_JSON);

        assertThat(envelope.get("prev")).isEqualTo("https://api.example.com/api/feed/for-you?page=2&size=2");
        assertThat(envelope).doesNotContainKey("next");
        assertThat(envelope.get("last")).isEqualTo("https://api.example.com/api/feed/for-you?page=3&size=2");
    }

    @Test
    void wraps_singlePageResult_emitsNeitherPrevNorNext() {
        Page<FeedPostListItem> page = new PageImpl<>(
                List.of(listItem(101L), listItem(102L)),
                PageRequest.of(0, 10),
                2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) callAdvice(
                page, "/api/feed/for-you", "page=0&size=10",
                JsonLdMediaType.APPLICATION_ACTIVITY_JSON);

        assertThat(envelope).doesNotContainKey("prev");
        assertThat(envelope).doesNotContainKey("next");
        // Single-page result → first == last == this page.
        assertThat(envelope.get("first")).isEqualTo(envelope.get("last"));
    }

    @Test
    void wraps_pinsContentTypeToTheNegotiatedAsJsonType() {
        Page<FeedPostListItem> page = Page.empty(PageRequest.of(0, 10));
        MockHttpServletResponse rawResponse = new MockHttpServletResponse();
        ServletServerHttpResponse springResponse = new ServletServerHttpResponse(rawResponse);

        advice.beforeBodyWrite(
                page, null,
                JsonLdMediaType.APPLICATION_ACTIVITY_JSON,
                null,
                buildRequest("/api/feed/for-you", "page=0&size=10"),
                springResponse);

        // The advice reaches through ServletServerHttpResponse and pins the
        // Content-Type on the raw servlet response. Without this hop, the
        // Jackson converter silently rewrites to application/json after we
        // wrap the Page<T>; this assertion is the regression guard.
        assertThat(rawResponse.getContentType())
                .isEqualTo(JsonLdMediaType.APPLICATION_ACTIVITY_JSON_VALUE);
    }

    @Test
    void passesThrough_pageBody_whenAcceptIsPlainJson() {
        Page<FeedPostListItem> page = Page.empty(PageRequest.of(0, 10));

        Object result = callAdvice(
                page, "/api/feed/for-you", "page=0&size=10",
                MediaType.APPLICATION_JSON);

        // Plain-JSON consumers see the original Page envelope unchanged.
        // Without this carve-out, every list endpoint would have to opt
        // out individually to stay backward-compatible.
        assertThat(result).isSameAs(page);
    }

    @Test
    void stripsInnerContextFromEachItem() {
        Page<FeedPostListItem> page = new PageImpl<>(
                List.of(listItem(101L)),
                PageRequest.of(0, 10),
                1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) callAdvice(
                page, "/api/feed/for-you", "page=0&size=10",
                JsonLdMediaType.APPLICATION_ACTIVITY_JSON);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) envelope.get("orderedItems");
        assertThat(items).hasSize(1);
        // The outer envelope carries @context once; inner items must drop
        // it so the document parses as a single JSON-LD graph rather than
        // a nested mess of redundant contexts.
        assertThat(items.get(0)).doesNotContainKey("@context");
    }

    // ── Helpers ────────────────────────────────────────────────

    private Object callAdvice(Page<?> body, String path, String query, MediaType selected) {
        ServerHttpRequest request = buildRequest(path, query);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());
        return advice.beforeBodyWrite(body, null, selected, null, request, response);
    }

    private static ServletServerHttpRequest buildRequest(String path, String query) {
        MockHttpServletRequest http = new MockHttpServletRequest("GET", path);
        http.setScheme("https");
        http.setServerName("api.example.com");
        http.setServerPort(443);
        http.setRequestURI(path);
        http.setQueryString(query);
        return new ServletServerHttpRequest(http);
    }

    private static FeedPostListItem listItem(Long id) {
        return new FeedPostListItem(
                id, 99L, "Ada", "post body " + id, List.of(),
                OffsetDateTime.now(), 0L, 0L, List.of(),
                List.of(),
                false, false,
                null, null, null, null,
                null);
    }
}
