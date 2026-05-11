package com.group7.backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the {@link RestClient} that {@code SemanticSimilarityService}
 * uses to call the OpenAI embeddings endpoint.
 *
 * <p>Lifted into its own bean so tests can swap in a builder backed by
 * {@code MockRestServiceServer} via the same pattern that
 * {@code TaxonomyClientsConfig} established for ESCO / Wikidata.
 *
 * <p>Gated by {@code app.recommendations.follow.signals.semantic-affinity-enabled=true}
 * so the bean (and the downstream service + signal) doesn't load when
 * the signal is dark — keeps a missing OPENAI_API_KEY from being a
 * hard startup error in dev.
 */
@Configuration
@ConditionalOnProperty(name = "app.recommendations.follow.signals.semantic-affinity-enabled",
        havingValue = "true")
public class OpenAiClientConfig {

    @Bean
    @Qualifier("openAiRestClient")
    public RestClient openAiRestClient(SemanticSimilarityProperties cfg) {
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(Duration.ofMillis(cfg.request().timeoutMs()))
                        .withReadTimeout(Duration.ofMillis(cfg.request().timeoutMs())));

        return RestClient.builder()
                .baseUrl(cfg.openai().baseUrl())
                .requestFactory(factory)
                .build();
    }
}
