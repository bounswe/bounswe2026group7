package com.group7.backend.testsupport.builders;

import com.group7.backend.testsupport.E2EClient;
import com.group7.backend.testsupport.UserHandle;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluent builder for {@code POST /api/mentorships/{id}/tasks}. Submission
 * and review are one-shots on the {@link E2EClient} facade.
 */
public final class TaskBuilder {

    private final E2EClient client;
    private Long mentorshipId;
    private String title = "E2E task";
    private String description;
    private OffsetDateTime dueDate;

    public TaskBuilder(E2EClient client) {
        this.client = client;
    }

    public TaskBuilder in(Long mentorshipId) {
        this.mentorshipId = mentorshipId;
        return this;
    }

    public TaskBuilder title(String title) {
        this.title = title;
        return this;
    }

    public TaskBuilder description(String description) {
        this.description = description;
        return this;
    }

    public TaskBuilder dueAt(OffsetDateTime dueDate) {
        this.dueDate = dueDate;
        return this;
    }

    /** Creates the task as the mentor and returns the new task id. */
    public Long assignBy(UserHandle mentor) throws Exception {
        if (mentorshipId == null) {
            throw new IllegalStateException("in() is required");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        if (description != null) body.put("description", description);
        if (dueDate != null) body.put("dueDate", dueDate.toString());

        MvcResult result = client.mockMvc().perform(post("/api/mentorships/" + mentorshipId + "/tasks")
                        .header("Authorization", "Bearer " + mentor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.objectMapper().writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return client.objectMapper().readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }
}
