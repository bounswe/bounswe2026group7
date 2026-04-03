package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.request.BulkAvailabilityRequest;
import com.group7.backend.dto.response.AvailabilitySlotResponse;
import com.group7.backend.exception.OverlappingSlotException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.AvailabilityService;
import com.group7.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AvailabilityController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AvailabilityService availabilityService;

    @MockitoBean
    private JwtService jwtService;

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentee@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private void mockMentorJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentor@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTOR");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private AvailabilitySlotResponse sampleSlot() {
        AvailabilitySlotResponse r = new AvailabilitySlotResponse();
        r.setId(1L);
        r.setDayOfWeek("MONDAY");
        r.setStartTime(LocalTime.of(9, 0));
        r.setEndTime(LocalTime.of(12, 0));
        r.setRecurring(true);
        return r;
    }

    // ── GET /{mentorId} ─────────────────────────────────────────────────────

    @Test
    void getSlotsReturns200ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 1L);
        when(availabilityService.getSlots(2L)).thenReturn(List.of(sampleSlot()));

        mockMvc.perform(get("/api/availability/2")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"));
    }

    @Test
    void getSlotsReturns200ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        when(availabilityService.getSlots(2L)).thenReturn(List.of(sampleSlot()));

        mockMvc.perform(get("/api/availability/2")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk());
    }

    @Test
    void getSlotsReturns404ForUnknownMentor() throws Exception {
        mockMenteeJwt("mentee-token", 1L);
        when(availabilityService.getSlots(99L)).thenThrow(new ResourceNotFoundException("Mentor not found"));

        mockMvc.perform(get("/api/availability/99")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isNotFound());
    }

    // ── PUT ──────────────────────────────────────────────────────────────────

    @Test
    void bulkUpdateReturns200ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        when(availabilityService.bulkUpdate(eq(2L), any())).thenReturn(List.of(sampleSlot()));

        AvailabilitySlotRequest slot = new AvailabilitySlotRequest();
        slot.setDayOfWeek(DayOfWeek.MONDAY);
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(12, 0));
        BulkAvailabilityRequest body = new BulkAvailabilityRequest();
        body.setSlots(List.of(slot));

        mockMvc.perform(put("/api/availability")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"));
    }

    @Test
    void bulkUpdateReturns403ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 1L);

        BulkAvailabilityRequest body = new BulkAvailabilityRequest();
        body.setSlots(List.of());

        mockMvc.perform(put("/api/availability")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkUpdateReturns409ForOverlap() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        when(availabilityService.bulkUpdate(eq(2L), any()))
                .thenThrow(new OverlappingSlotException("Slots overlap on MONDAY"));

        BulkAvailabilityRequest body = new BulkAvailabilityRequest();
        body.setSlots(List.of());

        mockMvc.perform(put("/api/availability")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void addSlotReturns400ForEndBeforeStart() throws Exception {
        mockMentorJwt("mentor-token", 2L);

        AvailabilitySlotRequest body = new AvailabilitySlotRequest();
        body.setDayOfWeek(DayOfWeek.MONDAY);
        body.setStartTime(LocalTime.of(14, 0));
        body.setEndTime(LocalTime.of(9, 0));

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── POST ─────────────────────────────────────────────────────────────────

    @Test
    void addSlotReturns201ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        when(availabilityService.addSlot(eq(2L), any())).thenReturn(sampleSlot());

        AvailabilitySlotRequest body = new AvailabilitySlotRequest();
        body.setDayOfWeek(DayOfWeek.MONDAY);
        body.setStartTime(LocalTime.of(9, 0));
        body.setEndTime(LocalTime.of(12, 0));

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void addSlotReturns403ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 1L);

        AvailabilitySlotRequest body = new AvailabilitySlotRequest();
        body.setDayOfWeek(DayOfWeek.MONDAY);
        body.setStartTime(LocalTime.of(9, 0));
        body.setEndTime(LocalTime.of(12, 0));

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addSlotReturns400WhenDayMissing() throws Exception {
        mockMentorJwt("mentor-token", 2L);

        AvailabilitySlotRequest body = new AvailabilitySlotRequest();
        body.setStartTime(LocalTime.of(9, 0));
        body.setEndTime(LocalTime.of(12, 0));

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /{slotId} ────────────────────────────────────────────────────

    @Test
    void removeSlotReturns204ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        doNothing().when(availabilityService).removeSlot(2L, 10L);

        mockMvc.perform(delete("/api/availability/10")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeSlotReturns403ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 1L);

        mockMvc.perform(delete("/api/availability/10")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeSlotReturns404WhenNotFound() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        doThrow(new ResourceNotFoundException("Availability slot not found"))
                .when(availabilityService).removeSlot(2L, 99L);

        mockMvc.perform(delete("/api/availability/99")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isNotFound());
    }
}
