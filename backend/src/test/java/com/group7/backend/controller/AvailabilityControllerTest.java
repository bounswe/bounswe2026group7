package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.AvailabilityOverrideRequest;
import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.request.BulkAvailabilityRequest;
import com.group7.backend.dto.response.AvailabilityOverrideResponse;
import com.group7.backend.dto.response.AvailabilitySlotResponse;
import com.group7.backend.entity.AvailabilityOverrideKind;
import com.group7.backend.exception.OverlappingSlotException;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.AvailabilityOverrideService;
import com.group7.backend.service.AvailabilityService;
import com.group7.backend.service.CalendarExportService;
import com.group7.backend.service.JwtService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
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
    private AvailabilityOverrideService overrideService;

    @MockitoBean
    private CalendarExportService calendarExportService;

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
    @DisplayName("Any authenticated user — including unrelated mentees — can read any mentor's availability (issue #274)")
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
    @DisplayName("A mentor can read another mentor's availability (issue #274 — open policy)")
    void getSlotsReturns200ForUnrelatedMentor() throws Exception {
        mockMentorJwt("other-mentor-token", 3L);
        when(availabilityService.getSlots(2L)).thenReturn(List.of(sampleSlot()));

        mockMvc.perform(get("/api/availability/2")
                        .header("Authorization", "Bearer other-mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"));
    }

    @Test
    @DisplayName("Unauthenticated requests are rejected (issue #274 — denied case)")
    void getSlotsRejectsUnauthenticatedRequests() throws Exception {
        // Spring Security's default entry point returns 403 (not 401) when no
        // authentication is present and no custom AuthenticationEntryPoint is
        // configured. The assertion locks in "rejected without a Bearer token",
        // which is the policy contract; the exact code is a Spring detail.
        mockMvc.perform(get("/api/availability/2"))
                .andExpect(status().isForbidden());
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

    // ── /overrides endpoints (issue #250) ───────────────────────────────────

    private AvailabilityOverrideResponse sampleOverride() {
        AvailabilityOverrideResponse r = new AvailabilityOverrideResponse();
        r.setId(7L);
        r.setKind(AvailabilityOverrideKind.UNAVAILABLE);
        r.setStartAt(OffsetDateTime.parse("2026-06-10T09:00:00Z"));
        r.setEndAt(OffsetDateTime.parse("2026-06-20T17:00:00Z"));
        return r;
    }

    @Test
    void listOverridesReturns200WithPagedBody() throws Exception {
        mockMenteeJwt("mentee-token", 1L);
        Page<AvailabilityOverrideResponse> page = new PageImpl<>(List.of(sampleOverride()));
        when(overrideService.list(eq(2L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/availability/2/overrides")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].kind").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.content[0].startAt").value("2026-06-10T09:00:00Z"));
    }

    @Test
    void addOverrideReturns201ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        when(overrideService.add(eq(2L), any(AvailabilityOverrideRequest.class)))
                .thenReturn(sampleOverride());

        AvailabilityOverrideRequest req = new AvailabilityOverrideRequest();
        req.setKind(AvailabilityOverrideKind.UNAVAILABLE);
        req.setStartAt(OffsetDateTime.parse("2026-06-10T09:00:00Z"));
        req.setEndAt(OffsetDateTime.parse("2026-06-20T17:00:00Z"));

        mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void addOverrideReturns403ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 1L);
        AvailabilityOverrideRequest req = new AvailabilityOverrideRequest();
        req.setKind(AvailabilityOverrideKind.AVAILABLE);
        req.setStartAt(OffsetDateTime.parse("2026-06-10T09:00:00Z"));
        req.setEndAt(OffsetDateTime.parse("2026-06-10T10:00:00Z"));

        mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("400 when endAt is not after startAt — Bean Validation @AssertTrue fires")
    void addOverrideReturns400WhenEndNotAfterStart() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        AvailabilityOverrideRequest req = new AvailabilityOverrideRequest();
        req.setKind(AvailabilityOverrideKind.AVAILABLE);
        req.setStartAt(OffsetDateTime.parse("2026-06-10T10:00:00Z"));
        req.setEndAt(OffsetDateTime.parse("2026-06-10T10:00:00Z")); // equal → not after

        mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addOverrideReturns409WhenSameKindOverlap() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        when(overrideService.add(eq(2L), any(AvailabilityOverrideRequest.class)))
                .thenThrow(new OverlappingSlotException("Override overlaps existing UNAVAILABLE"));

        AvailabilityOverrideRequest req = new AvailabilityOverrideRequest();
        req.setKind(AvailabilityOverrideKind.UNAVAILABLE);
        req.setStartAt(OffsetDateTime.parse("2026-06-10T09:00:00Z"));
        req.setEndAt(OffsetDateTime.parse("2026-06-20T17:00:00Z"));

        mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void removeOverrideReturns204ForOwner() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        doNothing().when(overrideService).remove(2L, 7L);

        mockMvc.perform(delete("/api/availability/overrides/7")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeOverrideReturns403ForOtherMentor() throws Exception {
        mockMentorJwt("mentor-token", 2L);
        doThrow(new ProfileNotVisibleException("You can only delete your own overrides"))
                .when(overrideService).remove(2L, 7L);

        mockMvc.perform(delete("/api/availability/overrides/7")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isForbidden());
    }

    // ── /ical endpoint (issue #250) ─────────────────────────────────────────

    @Test
    @DisplayName("GET /ical is anonymous — calendar apps cannot send Authorization headers on subscription URLs")
    void exportIcalReturns200ForAnonymousCaller() throws Exception {
        byte[] ics = ("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\n"
                + "END:VCALENDAR\r\n").getBytes();
        when(calendarExportService.exportMentorAvailability(2L)).thenReturn(ics);

        mockMvc.perform(get("/api/availability/2/ical"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"mentor_2_availability.ics\""))
                .andExpect(content().bytes(ics));
    }

    @Test
    void exportIcalReturns404WhenMentorMissing() throws Exception {
        when(calendarExportService.exportMentorAvailability(99L))
                .thenThrow(new ResourceNotFoundException("Mentor not found"));

        mockMvc.perform(get("/api/availability/99/ical"))
                .andExpect(status().isNotFound());
    }
}
