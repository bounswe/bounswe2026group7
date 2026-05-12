package com.group7.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for {@link com.group7.backend.service.embedding.SemanticSimilarityService}.
 *
 * <p>Auto-discovered via {@code @ConfigurationPropertiesScan} on
 * {@code BackendApplication}; binds {@code app.embedding.*} from
 * application.properties. Lives in its own record (separate from the
 * upcoming {@code MentorRecommendationProperties}) so the embedding
 * service can be reused by follow-recommendation and feed surfaces
 * without dragging mentor-specific weights along.
 *
 * @param model    OpenAI embedding model name; also baked into the cache
 *                 key so an upgrade (e.g. 3-small → 3-large) partitions
 *                 cleanly without manual eviction
 * @param cache    Caffeine cache sizing
 * @param failOpen when true, embedding failures degrade gracefully
 *                 (signal scored 0, factor {@code semantic-unavailable})
 *                 instead of bubbling up. Strongly recommended to leave
 *                 on — a partial recommendation is better than a 500.
 */
@ConfigurationProperties(prefix = "app.embedding")
@Validated
public record SemanticSimilarityProperties(
        @NotBlank String model,
        @Valid Cache cache,
        boolean failOpen
) {

    public record Cache(@Positive int maxSize, @Positive int ttlHours) {}
}
