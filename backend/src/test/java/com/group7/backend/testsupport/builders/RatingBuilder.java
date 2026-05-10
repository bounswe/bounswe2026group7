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
 * Fluent builder for {@code POST /api/mentorships/{id}/ratings} — the
 * minimal Rating endpoint introduced for issue #254.
 */
public final class RatingBuilder {

    private final E2EClient client;
    private Long mentorshipId;
    private UserHandle by;
    private Integer stars;
    private String comment;

    public RatingBuilder(E2EClient client) {
        this.client = client;
    }

    public RatingBuilder in(Long mentorshipId) {
        this.mentorshipId = mentorshipId;
        return this;
    }

    public RatingBuilder by(UserHandle user) {
        this.by = user;
        return this;
    }

    public RatingBuilder stars(int stars) {
        this.stars = stars;
        return this;
    }

    public RatingBuilder comment(String comment) {
        this.comment = comment;
        return this;
    }

    /** Submits the rating and returns the new rating id. */
    public Long submit() throws Exception {
        if (mentorshipId == null || by == null || stars == null) {
            throw new IllegalStateException("in(), by(), stars() are required");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("stars", stars);
        if (comment != null) body.put("comment", comment);

        MvcResult result = client.mockMvc().perform(post("/api/mentorships/" + mentorshipId + "/ratings")
                        .header("Authorization", "Bearer " + by.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.objectMapper().writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return client.objectMapper().readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }
}
