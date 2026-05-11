package com.group7.backend.testsupport.builders;

import com.group7.backend.testsupport.E2EClient;
import com.group7.backend.testsupport.UserHandle;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluent builder for {@code POST /api/mentorship-requests} (mentee-initiated
 * mentorship request creation). Accept / reject / cancel are one-shots on
 * the {@link E2EClient} facade — they don't benefit from a builder.
 */
public final class MentorshipRequestBuilder {

    private final E2EClient client;
    private UserHandle from;
    private UserHandle to;
    private String message;

    public MentorshipRequestBuilder(E2EClient client) {
        this.client = client;
    }

    public MentorshipRequestBuilder from(UserHandle mentee) {
        this.from = mentee;
        return this;
    }

    public MentorshipRequestBuilder to(UserHandle mentor) {
        this.to = mentor;
        return this;
    }

    public MentorshipRequestBuilder message(String message) {
        this.message = message;
        return this;
    }

    /** Creates the request and returns the new request id. */
    public Long create() throws Exception {
        if (from == null || to == null) {
            throw new IllegalStateException("from() and to() are required before create()");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("mentorId", to.id());
        if (message != null) {
            body.put("message", message);
        }
        MvcResult result = client.mockMvc().perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + from.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.objectMapper().writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return client.objectMapper().readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }
}
