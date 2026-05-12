package com.group7.backend.service.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Configuration check for the OpenAI embedding integration on
 * {@code /actuator/health}.
 *
 * <ul>
 *   <li><b>UP</b> when the {@link EmbeddingModel} bean is wired.</li>
 *   <li><b>OUT_OF_SERVICE</b> when the bean is absent — a configuration
 *       issue, typically a missing {@code OPENAI_API_KEY}.</li>
 * </ul>
 *
 * <p>This indicator deliberately does not make a synthetic OpenAI call:
 * every health probe (Kubernetes liveness, ALB target group, Docker
 * healthcheck) would otherwise translate into billable OpenAI usage,
 * and exception class names from the OpenAI SDK would leak into the
 * {@code reason} detail surfaced on {@code /actuator/health}. Runtime
 * reachability is observable via the Micrometer counters
 * {@code openai.embedding.calls{outcome=success|failure}} and the
 * service's own {@code semantic-unavailable} factor on responses, both
 * of which are scraped by the existing monitoring stack.
 */
@Component
public class EmbeddingHealthIndicator implements HealthIndicator {

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public EmbeddingHealthIndicator(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    @Override
    public Health health() {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            return Health.outOfService()
                    .withDetail("reason", "EmbeddingModel bean unavailable")
                    .build();
        }
        return Health.up().build();
    }
}
