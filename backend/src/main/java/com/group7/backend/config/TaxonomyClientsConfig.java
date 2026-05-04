package com.group7.backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the two RestClients used by TaxonomyService for ESCO and Wikidata
 * lookups. Lifting them out of the service makes the service testable: tests
 * can substitute clients backed by MockRestServiceServer to drive the upstream
 * responses without spinning up a real HTTP server.
 */
@Configuration
public class TaxonomyClientsConfig {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Wikimedia's User-Agent policy
     * (<a href="https://meta.wikimedia.org/wiki/User-Agent_policy">policy</a>)
     * blocks generic library User-Agents like {@code Java-http-client/...}.
     * The same identifier is sent to ESCO for symmetry; ESCO doesn't enforce
     * this but it makes the upstream graph's request log self-documenting.
     */
    private static final String USER_AGENT =
            "group7-backend/1.0 (https://github.com/bounswe/bounswe2026group7) Spring-RestClient";

    @Bean
    @Qualifier("escoRestClient")
    public RestClient escoRestClient(
            @Value("${app.taxonomy.esco-base-url:https://ec.europa.eu/esco/api}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", USER_AGENT)
                .requestFactory(buildFactory())
                .build();
    }

    @Bean
    @Qualifier("wikidataRestClient")
    public RestClient wikidataRestClient(
            @Value("${app.taxonomy.wikidata-base-url:https://www.wikidata.org}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", USER_AGENT)
                .requestFactory(buildFactory())
                .build();
    }

    private static ClientHttpRequestFactory buildFactory() {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(HTTP_TIMEOUT)
                        .withReadTimeout(HTTP_TIMEOUT));
    }
}
