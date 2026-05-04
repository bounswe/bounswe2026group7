package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.AvailabilityOverrideRequest;
import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.AvailabilityOverrideKind;
import com.group7.backend.entity.User;
import com.group7.backend.repository.AvailabilityOverrideRepository;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the availability override CRUD endpoints and the
 * iCalendar export (#250) against real Postgres. Each scenario is
 * self-contained — {@code @BeforeEach cleanDb()} wipes the relevant tables,
 * then the scenario seeds users and overrides via the public API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvailabilityIcalIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private AvailabilitySlotRepository slotRepository;
    @Autowired private AvailabilityOverrideRepository overrideRepository;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        overrideRepository.deleteAll();
        slotRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    // ── ICS export ─────────────────────────────────────────────────────────

    @Test
    void icalExportIsAnonymousAndParsesCleanly() throws Exception {
        // Mentor with one weekly slot + one AVAILABLE override + one
        // UNAVAILABLE override. The export must include all three as VEVENTs
        // and parse cleanly via ical4j on the consumer side.
        String mentorToken = registerAndLogin("ical_anon_mentor@test.com", true);
        Long mentorId = userRepository.findByEmail("ical_anon_mentor@test.com")
                .orElseThrow().getId();

        addWeeklySlot(mentorToken, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        addOverride(mentorToken, AvailabilityOverrideKind.AVAILABLE,
                "2026-06-15T09:00:00Z", "2026-06-15T12:00:00Z");
        addOverride(mentorToken, AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-20T00:00:00Z", "2026-06-25T00:00:00Z");

        // No Authorization header — proves the permitAll carve-out works.
        MvcResult result = mockMvc.perform(get("/api/availability/" + mentorId + "/ical"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"mentor_" + mentorId + "_availability.ics\""))
                .andReturn();

        Calendar parsed = new CalendarBuilder()
                .build(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        List<VEvent> events = parsed.getComponents("VEVENT");
        assertThat(events).hasSize(3);
    }

    @Test
    void icalExportReturns404ForUnknownMentor() throws Exception {
        mockMvc.perform(get("/api/availability/99999999/ical"))
                .andExpect(status().isNotFound());
    }

    @Test
    void icalExportForEmptyMentorIsValidVCalendarWithNoEvents() throws Exception {
        registerAndLogin("ical_empty_mentor@test.com", true);
        Long mentorId = userRepository.findByEmail("ical_empty_mentor@test.com")
                .orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/api/availability/" + mentorId + "/ical"))
                .andExpect(status().isOk())
                .andReturn();

        Calendar parsed = new CalendarBuilder()
                .build(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        assertThat(parsed.<VEvent>getComponents("VEVENT")).isEmpty();
        // VTIMEZONE is always emitted so consumers without an external TZ
        // database render correctly.
        assertThat(parsed.getComponents("VTIMEZONE")).hasSize(1);
    }

    // ── override CRUD ──────────────────────────────────────────────────────

    @Test
    void overrideCrudRoundTrip_addListDelete() throws Exception {
        String mentorToken = registerAndLogin("ical_crud_mentor@test.com", true);
        Long mentorId = userRepository.findByEmail("ical_crud_mentor@test.com")
                .orElseThrow().getId();

        // Add one override.
        AvailabilityOverrideRequest req = override(
                AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-10T00:00:00Z", "2026-06-20T00:00:00Z");
        MvcResult addResult = mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("UNAVAILABLE"))
                .andReturn();
        Long overrideId = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .get("id").asLong();

        // List returns it (any authenticated user can read).
        mockMvc.perform(get("/api/availability/" + mentorId + "/overrides")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(overrideId));

        // Delete it.
        mockMvc.perform(delete("/api/availability/overrides/" + overrideId)
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isNoContent());

        // List is empty.
        mockMvc.perform(get("/api/availability/" + mentorId + "/overrides")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void addOverrideReturns409OnSameKindOverlap() throws Exception {
        String mentorToken = registerAndLogin("ical_overlap_mentor@test.com", true);

        addOverride(mentorToken, AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-10T00:00:00Z", "2026-06-20T00:00:00Z");

        AvailabilityOverrideRequest overlapping = override(
                AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-15T00:00:00Z", "2026-06-25T00:00:00Z");
        mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapping)))
                .andExpect(status().isConflict());
    }

    @Test
    void addOverrideAllowsCrossKindOverlap() throws Exception {
        // The whole point of the layered model: an UNAVAILABLE block can sit
        // on top of an AVAILABLE block (or the recurring weekly schedule).
        String mentorToken = registerAndLogin("ical_cross_mentor@test.com", true);

        addOverride(mentorToken, AvailabilityOverrideKind.AVAILABLE,
                "2026-06-15T09:00:00Z", "2026-06-15T17:00:00Z");

        // UNAVAILABLE 12-13 carves a hole out of the AVAILABLE 09-17.
        AvailabilityOverrideRequest carve = override(
                AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-15T12:00:00Z", "2026-06-15T13:00:00Z");
        mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(carve)))
                .andExpect(status().isCreated());
    }

    @Test
    void menteeAddingOverrideReturns403() throws Exception {
        String menteeToken = registerAndLogin("ical_mentee@test.com", false);

        AvailabilityOverrideRequest req = override(
                AvailabilityOverrideKind.AVAILABLE,
                "2026-06-15T09:00:00Z", "2026-06-15T10:00:00Z");
        mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mentorCannotDeleteAnotherMentorsOverride() throws Exception {
        String aToken = registerAndLogin("ical_delete_a@test.com", true);
        String bToken = registerAndLogin("ical_delete_b@test.com", true);

        // A creates an override.
        MvcResult addResult = mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(override(
                                AvailabilityOverrideKind.AVAILABLE,
                                "2026-06-15T09:00:00Z", "2026-06-15T10:00:00Z"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long overrideId = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .get("id").asLong();

        // B tries to delete it → 403 (not 404 — would leak existence).
        mockMvc.perform(delete("/api/availability/overrides/" + overrideId)
                        .header("Authorization", "Bearer " + bToken))
                .andExpect(status().isForbidden());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private AvailabilityOverrideRequest override(AvailabilityOverrideKind kind,
                                                 String startIso, String endIso) {
        AvailabilityOverrideRequest r = new AvailabilityOverrideRequest();
        r.setKind(kind);
        r.setStartAt(OffsetDateTime.parse(startIso));
        r.setEndAt(OffsetDateTime.parse(endIso));
        return r;
    }

    private void addOverride(String token, AvailabilityOverrideKind kind,
                             String startIso, String endIso) throws Exception {
        mockMvc.perform(post("/api/availability/overrides")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(override(kind, startIso, endIso))))
                .andExpect(status().isCreated());
    }

    private void addWeeklySlot(String token, DayOfWeek day,
                               LocalTime start, LocalTime end) throws Exception {
        AvailabilitySlotRequest req = new AvailabilitySlotRequest();
        req.setDayOfWeek(day);
        req.setStartTime(start);
        req.setEndTime(end);
        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName(isMentor ? "Mira" : "Eli");
        req.setLastName(isMentor ? "Mentor" : "Mentee");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(isMentor);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(user.getId()).get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Password1");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }
}
