package com.group7.backend.config;

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

/**
 * Adds the conventional {@code X-Total-Count} header to any response whose
 * body is a Spring Data {@link Page}. Coexists with the JSON envelope's
 * {@code totalElements} field — purely additive, never removes anything.
 *
 * <p>Why a header, not just the envelope: it's the de-facto convention
 * (GitHub, Stripe, Atlassian) and lets clients short-circuit on
 * {@code HEAD} / cursor-paginated probes without reading the body.
 *
 * <p>{@link Order} is one rank ahead of {@link com.group7.backend.config.jsonld.JsonLdResponseBodyAdvice}
 * ({@code LOWEST_PRECEDENCE}) so the header is set before any
 * content-type rewriting; the two advices touch disjoint concerns
 * (header vs. body), so the relative order is mostly cosmetic, but
 * keeping the header set first means downstream advices can rely on it.
 *
 * <p><b>{@code supports()} returns {@code true} unconditionally.</b> The
 * controllers in this project return {@code ResponseEntity<Page<T>>},
 * so {@link MethodParameter#getParameterType()} reports
 * {@code ResponseEntity.class}, not {@code Page.class}. A naive
 * {@code Page.class.isAssignableFrom(...)} check would silently skip
 * every paged endpoint. Spring unwraps the {@code ResponseEntity<X>}
 * and passes {@code X} as {@code body}, so the real discriminator lives
 * in {@link #beforeBodyWrite}'s {@code instanceof Page} check — a
 * single instanceof per response is negligible cost.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class PageTotalCountHeaderAdvice implements ResponseBodyAdvice<Object> {

    static final String HEADER_NAME = "X-Total-Count";

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof Page<?> page) {
            response.getHeaders().set(HEADER_NAME, Long.toString(page.getTotalElements()));
        }
        return body;
    }
}
