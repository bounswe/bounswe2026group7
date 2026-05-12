package com.group7.backend.config.jsonld;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class JsonLdMediaTypeConfig implements WebMvcConfigurer {

    /**
     * Without this, Spring's ContentNegotiationManager doesn't know about
     * application/activity+json and silently rewrites the response
     * Content-Type back to application/json after our JSON-LD advice
     * already produced an AS 2.0 document body. Registering both wire
     * types as known media types keeps the negotiated Content-Type aligned
     * with the client's Accept header end-to-end.
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .mediaType("ld+json", JsonLdMediaType.APPLICATION_LD_JSON)
                .mediaType("activity+json", JsonLdMediaType.APPLICATION_ACTIVITY_JSON);
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (!(converter instanceof MappingJackson2HttpMessageConverter jackson)) {
                continue;
            }
            List<MediaType> supported = jackson.getSupportedMediaTypes();
            boolean handlesPlainJson = supported.stream().anyMatch(MediaType.APPLICATION_JSON::equals);
            if (!handlesPlainJson) {
                continue;
            }
            List<MediaType> updated = new ArrayList<>(supported);
            // Both wire types are treated identically by the response advice;
            // declaring both here means content-negotiation routes either
            // Accept header through the same converter instead of returning 406.
            if (!updated.contains(JsonLdMediaType.APPLICATION_LD_JSON)) {
                updated.add(JsonLdMediaType.APPLICATION_LD_JSON);
            }
            if (!updated.contains(JsonLdMediaType.APPLICATION_ACTIVITY_JSON)) {
                updated.add(JsonLdMediaType.APPLICATION_ACTIVITY_JSON);
            }
            jackson.setSupportedMediaTypes(updated);
        }
    }
}
