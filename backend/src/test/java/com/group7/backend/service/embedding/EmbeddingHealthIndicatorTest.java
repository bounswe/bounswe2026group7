package com.group7.backend.service.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingHealthIndicatorTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(Supplier<T> s) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenAnswer(inv -> s.get());
        return p;
    }

    @Test
    void noModel_outOfService() {
        var ind = new EmbeddingHealthIndicator(provider(() -> null));
        var h = ind.health();
        assertThat(h.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(h.getDetails()).containsEntry("reason", "EmbeddingModel bean unavailable");
    }

    @Test
    void modelPresent_up_andDoesNotCallTheModel() {
        // The indicator is a configuration check — it must NOT call the
        // model. Hitting OpenAI on every health probe would translate
        // anonymous /actuator/health traffic into billable usage.
        var model = mock(EmbeddingModel.class);
        var ind = new EmbeddingHealthIndicator(provider(() -> model));
        var h = ind.health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        verify(model, never()).embed(org.mockito.ArgumentMatchers.anyString());
    }
}
