package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.request.BulkAvailabilityRequest;
import com.group7.backend.dto.response.MenteeAvailabilitySlotResponse;
import com.group7.backend.exception.OverlappingSlotException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MenteeAvailabilityService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MenteeAvailabilityController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MenteeAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MenteeAvailabilityService menteeAvailabilityService;

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

    private MenteeAvailabilitySlotResponse sampleSlot() {
        MenteeAvailabilitySlotResponse r = new MenteeAvailabilitySlotResponse();
        r.setId(1L);
        r.setDayOfWeek("MONDAY");
        r.setStartTime(LocalTime.of(9, 0));
        r.setEndTime(LocalTime.of(12, 0));
        r.setRecurring(true);
        return r;
    }

    @Test
    void getOwnSlotsReturns200ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 1L);
        when(menteeAvailabilityService.getSlots(1L)).thenReturn(List.of(sampleSlot()));

        mockMvc.perform(get("/api/mentee-availability")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"));
    }

    @Test
    void getOwnSlotsReturns403ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 2L);

        mockMvc.perform(get("/api/mentee-availability")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkUpdateReturns200() throws Exception {
        mockMenteeJwt("mentee-token", 1L);
        when(menteeAvailabilityService.bulkUpdate(eq(1L), any())).thenReturn(List.of(sampleSlot()));

        AvailabilitySlotRequest slot = new AvailabilitySlotRequest();
        slot.setDayOfWeek(DayOfWeek.MONDAY);
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(12, 0));
        BulkAvailabilityRequest body = new BulkAvailabilityRequest();
        body.setSlots(List.of(slot));

        mockMvc.perform(put("/api/mentee-availability")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void addSlotReturns409ForOverlap() throws Exception {
        mockMenteeJwt("mentee-token", 1L);
        when(menteeAvailabilityService.addSlot(eq(1L), any()))
                .thenThrow(new OverlappingSlotException("overlap"));

        AvailabilitySlotRequest body = new AvailabilitySlotRequest();
        body.setDayOfWeek(DayOfWeek.MONDAY);
        body.setStartTime(LocalTime.of(9, 0));
        body.setEndTime(LocalTime.of(12, 0));

        mockMvc.perform(post("/api/mentee-availability")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void removeSlotReturns404WhenMissing() throws Exception {
        mockMenteeJwt("mentee-token", 1L);
        doThrow(new ResourceNotFoundException("Mentee availability slot not found"))
                .when(menteeAvailabilityService).removeSlot(1L, 11L);

        mockMvc.perform(delete("/api/mentee-availability/11")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isNotFound());
    }
}
