package com.group7.backend.testsupport.builders;

import com.group7.backend.testsupport.E2EClient;
import com.group7.backend.testsupport.UserHandle;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluent builder for {@code PUT /api/availability} — bulk-replaces the
 * authenticated mentor's recurring availability slots.
 */
public final class AvailabilityBuilder {

    private final E2EClient client;
    private final List<Map<String, Object>> slots = new ArrayList<>();

    public AvailabilityBuilder(E2EClient client) {
        this.client = client;
    }

    /** Adds a recurring weekly slot. Multiple calls accumulate. */
    public AvailabilityBuilder slot(DayOfWeek day, LocalTime start, LocalTime end) {
        slots.add(Map.of(
                "dayOfWeek", day.name(),
                "startTime", start.toString(),
                "endTime", end.toString(),
                "recurring", true));
        return this;
    }

    /** Persists the accumulated slots as the given mentor. */
    public void save(UserHandle mentor) throws Exception {
        Map<String, Object> body = Map.of("slots", slots);
        client.mockMvc().perform(put("/api/availability")
                        .header("Authorization", "Bearer " + mentor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.objectMapper().writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
