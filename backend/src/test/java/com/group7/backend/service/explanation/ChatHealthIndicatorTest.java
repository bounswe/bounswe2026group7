package com.group7.backend.service.explanation;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHealthIndicatorTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(Supplier<T> s) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenAnswer(inv -> s.get());
        return p;
    }

    @Test
    void noModel_outOfService() {
        var ind = new ChatHealthIndicator(provider(() -> null));
        var h = ind.health();
        assertThat(h.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(h.getDetails()).containsEntry("reason", "ChatModel bean unavailable");
    }

    @Test
    void modelPresent_up_andDoesNotCallTheModel() {
        // Probe is a configuration check, not a synthetic monitor — never
        // billable OpenAI calls from /actuator/health.
        var model = mock(ChatModel.class);
        var ind = new ChatHealthIndicator(provider(() -> model));
        var h = ind.health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        verify(model, never()).call(any(Prompt.class));
    }
}
