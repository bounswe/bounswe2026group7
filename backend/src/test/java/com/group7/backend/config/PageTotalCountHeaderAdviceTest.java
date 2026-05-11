package com.group7.backend.config;

import com.group7.backend.config.jsonld.JsonLdResponseBodyAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link PageTotalCountHeaderAdvice}. Verifies:
 * <ul>
 *   <li>{@code supports()} returns {@code true} unconditionally — see the
 *       javadoc on the advice for why this is correct vs. checking the
 *       return type.</li>
 *   <li>{@code beforeBodyWrite()} sets {@code X-Total-Count} from the
 *       page's {@code totalElements} when the body is a {@link Page}.</li>
 *   <li>Non-{@link Page} bodies don't trigger the header.</li>
 *   <li>The advice's {@code @Order} is strictly ahead of
 *       {@link JsonLdResponseBodyAdvice} so the header is set before
 *       any content-type rewriting.</li>
 * </ul>
 */
class PageTotalCountHeaderAdviceTest {

    private final PageTotalCountHeaderAdvice advice = new PageTotalCountHeaderAdvice();

    @Test
    void supports_returnsTrueUnconditionally() {
        // The discriminator lives in beforeBodyWrite (instanceof Page) so
        // supports() must not gate on returnType — see advice javadoc.
        assertThat(advice.supports(null, null)).isTrue();
    }

    @Test
    void beforeBodyWrite_setsHeaderForPageBody() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 20), 42L);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        when(response.getHeaders()).thenReturn(headers);

        Object out = advice.beforeBodyWrite(page, null, null, null, null, response);

        assertThat(out).isSameAs(page);
        assertThat(headers.getFirst("X-Total-Count")).isEqualTo("42");
    }

    @Test
    void beforeBodyWrite_doesNotSetHeaderForNonPageBody() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite("not a page", null, null, null, null, response);

        assertThat(headers.getFirst("X-Total-Count")).isNull();
    }

    @Test
    void beforeBodyWrite_handlesEmptyPage() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(page, null, null, null, null, response);

        assertThat(headers.getFirst("X-Total-Count")).isEqualTo("0");
    }

    @Test
    void order_isAheadOfJsonLdAdvice() {
        // Two advices, sorted by AnnotationAwareOrderComparator. Our advice
        // is LOWEST_PRECEDENCE - 10, JsonLd is LOWEST_PRECEDENCE. After
        // sorting, ours comes first — proves the relative ordering at the
        // annotation level.
        JsonLdResponseBodyAdvice jsonLd = new JsonLdResponseBodyAdvice(List.of());
        List<Object> ordered = new java.util.ArrayList<>(List.of(jsonLd, advice));
        AnnotationAwareOrderComparator.sort(ordered);
        assertThat(ordered.get(0)).isSameAs(advice);
        assertThat(ordered.get(1)).isSameAs(jsonLd);
    }
}
