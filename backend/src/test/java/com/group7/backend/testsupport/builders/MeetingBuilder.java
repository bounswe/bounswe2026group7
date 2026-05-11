package com.group7.backend.testsupport.builders;

import com.fasterxml.jackson.databind.JsonNode;
import com.group7.backend.entity.MeetingType;
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
 * Fluent builder for {@code POST /api/mentorships/{id}/meetings}.
 *
 * <p>Returns the raw {@link JsonNode} response so scenarios can inspect
 * {@code warnings[]} (used by the availability-conflict scenario).
 */
public final class MeetingBuilder {

    private final E2EClient client;
    private Long mentorshipId;
    private String title = "E2E meeting";
    private String description;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private MeetingType type = MeetingType.ONLINE;
    private String meetingLink = "https://meet.example.com/e2e";
    private boolean recurring = false;

    public MeetingBuilder(E2EClient client) {
        this.client = client;
    }

    public MeetingBuilder in(Long mentorshipId) {
        this.mentorshipId = mentorshipId;
        return this;
    }

    public MeetingBuilder title(String title) {
        this.title = title;
        return this;
    }

    public MeetingBuilder description(String description) {
        this.description = description;
        return this;
    }

    public MeetingBuilder startsAt(OffsetDateTime startTime) {
        this.startTime = startTime;
        return this;
    }

    public MeetingBuilder endsAt(OffsetDateTime endTime) {
        this.endTime = endTime;
        return this;
    }

    public MeetingBuilder type(MeetingType type) {
        this.type = type;
        return this;
    }

    public MeetingBuilder link(String meetingLink) {
        this.meetingLink = meetingLink;
        return this;
    }

    /** Schedules the meeting as the given user (the mentor in production). */
    public JsonNode schedule(UserHandle byUser) throws Exception {
        if (mentorshipId == null || startTime == null || endTime == null) {
            throw new IllegalStateException("in(), startsAt(), endsAt() are required");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        if (description != null) body.put("description", description);
        body.put("startTime", startTime.toString());
        body.put("endTime", endTime.toString());
        body.put("meetingType", type.name());
        if (type == MeetingType.ONLINE) body.put("meetingLink", meetingLink);
        body.put("recurring", recurring);

        MvcResult result = client.mockMvc().perform(post("/api/mentorships/" + mentorshipId + "/meetings")
                        .header("Authorization", "Bearer " + byUser.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.objectMapper().writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return client.objectMapper().readTree(result.getResponse().getContentAsString());
    }
}
