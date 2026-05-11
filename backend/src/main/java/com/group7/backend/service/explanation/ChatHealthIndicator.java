package com.group7.backend.service.explanation;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Configuration check for the OpenAI chat integration on
 * {@code /actuator/health}. See
 * {@link com.group7.backend.service.embedding.EmbeddingHealthIndicator}
 * for the rationale on not synthetically calling the API.
 *
 * <ul>
 *   <li><b>UP</b> when the {@link ChatModel} bean is wired.</li>
 *   <li><b>OUT_OF_SERVICE</b> when the bean is absent.</li>
 * </ul>
 */
@Component
public class ChatHealthIndicator implements HealthIndicator {

    private final ObjectProvider<ChatModel> chatModelProvider;

    public ChatHealthIndicator(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public Health health() {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            return Health.outOfService()
                    .withDetail("reason", "ChatModel bean unavailable")
                    .build();
        }
        return Health.up().build();
    }
}
