package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.CreateReportRequest;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.request.UpdateReportStatusRequest;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.ProblemType;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.entity.User;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;

/**
 * End-to-end coverage of the reporting + admin-moderation surface
 * (#135) against a real Postgres + the live REST surface. Exercises
 * the full lifecycle: A submits a report against B, admin C sees it
 * in the queue, transitions through UNDER_REVIEW → RESOLVED, A reads
 * back the resolved status from /me, and a fresh duplicate against
 * the same target is allowed (the partial unique index releases the
 * slot when the prior report resolves).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM reports");
        notificationRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void fullLifecycle_submitTriageResolveResubmit() throws Exception {
        // Reporter A (mentee), target B (mentor), admin C.
        String tokenA = registerAndLogin("rpt_a@test.com", false);
        String tokenB = registerAndLogin("rpt_b@test.com", true);
        Long idA = userRepository.findByEmail("rpt_a@test.com").orElseThrow().getId();
        Long idB = userRepository.findByEmail("rpt_b@test.com").orElseThrow().getId();
        Admin admin = seedAdmin("rpt_admin@test.com");
        String tokenC = loginAs("rpt_admin@test.com");

        // 1. A submits a USER report against B.
        Long reportId = submitReport(tokenA, ReportTargetType.USER, idB);

        // 2. Notification fan-out: admin C has a REPORT_RECEIVED row.
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            long notifs = notificationRepository.findAll().stream()
                    .filter(n -> n.getRecipient().getId().equals(admin.getId()))
                    .filter(n -> n.getType() == NotificationType.REPORT_RECEIVED)
                    .count();
            assertThat(notifs).isEqualTo(1L);
        });

        // 3. Admin sees the report in the queue with status=OPEN.
        mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(reportId.intValue()))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));

        // 4. Admin views detail → target_summary contains B's name.
        mockMvc.perform(get("/api/admin/reports/" + reportId)
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetSummary").value(org.hamcrest.Matchers.containsString("Tester")));

        // 5. Transition OPEN → UNDER_REVIEW → RESOLVED.
        transition(tokenC, reportId, ReportStatus.UNDER_REVIEW);
        transition(tokenC, reportId, ReportStatus.RESOLVED);

        // 6. A reads back the resolved status from /me.
        mockMvc.perform(get("/api/reports/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.content[0].reviewedById").value(admin.getId().intValue()));

        // 7. Now that the prior report is RESOLVED, A can submit a fresh
        // one against the same target — partial unique index releases
        // the slot.
        Long secondId = submitReport(tokenA, ReportTargetType.USER, idB);
        assertThat(secondId).isNotEqualTo(reportId);
    }

    @Test
    void duplicateActiveReport_returns409() throws Exception {
        String tokenA = registerAndLogin("dup_a@test.com", false);
        String tokenB = registerAndLogin("dup_b@test.com", true);
        Long idB = userRepository.findByEmail("dup_b@test.com").orElseThrow().getId();
        seedAdmin("dup_admin@test.com");

        submitReport(tokenA, ReportTargetType.USER, idB);

        // Second report against the same target while first is still OPEN.
        CreateReportRequest body = new CreateReportRequest(
                ReportTargetType.USER, idB, ProblemType.HARASSMENT, "duplicate");
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void selfReport_returns400() throws Exception {
        String tokenA = registerAndLogin("self_a@test.com", false);
        Long idA = userRepository.findByEmail("self_a@test.com").orElseThrow().getId();
        seedAdmin("self_admin@test.com");

        CreateReportRequest body = new CreateReportRequest(
                ReportTargetType.USER, idA, ProblemType.OTHER, "myself");
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdmin_cannotAccessAdminQueue_403() throws Exception {
        String tokenA = registerAndLogin("forbid_a@test.com", false);
        seedAdmin("forbid_admin@test.com");

        mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTransition_returns400_andTerminalRejectsFurtherTransitions() throws Exception {
        String tokenA = registerAndLogin("trans_a@test.com", false);
        String tokenB = registerAndLogin("trans_b@test.com", true);
        Long idB = userRepository.findByEmail("trans_b@test.com").orElseThrow().getId();
        seedAdmin("trans_admin@test.com");
        String tokenC = loginAs("trans_admin@test.com");

        Long reportId = submitReport(tokenA, ReportTargetType.USER, idB);
        transition(tokenC, reportId, ReportStatus.RESOLVED);

        // Resolved → anything is rejected.
        UpdateReportStatusRequest reopenBody = new UpdateReportStatusRequest(ReportStatus.OPEN);
        mockMvc.perform(patch("/api/admin/reports/" + reportId)
                        .header("Authorization", "Bearer " + tokenC)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reopenBody)))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Long submitReport(String token, ReportTargetType type, Long targetId) throws Exception {
        CreateReportRequest body = new CreateReportRequest(
                type, targetId, ProblemType.HARASSMENT, "Sample description");
        MvcResult result = mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private void transition(String token, Long reportId, ReportStatus newStatus) throws Exception {
        UpdateReportStatusRequest body = new UpdateReportStatusRequest(newStatus);
        mockMvc.perform(patch("/api/admin/reports/" + reportId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private Admin seedAdmin(String email) {
        Admin a = new Admin();
        a.setFirstName("Admin");
        a.setLastName("Reviewer");
        a.setEmail(email);
        a.setPasswordHash(passwordEncoder.encode("Password1"));
        a.setIsEmailVerified(true);
        a.setCreatedAt(OffsetDateTime.now());
        // Admin extends User via JOINED inheritance; UserRepository.save
        // handles the parent + child row insert in one transaction.
        return userRepository.save(a);
    }

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Report");
        req.setLastName("Tester");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(isMentor);
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(user.getId()).get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());
        return loginAs(email);
    }

    private String loginAs(String email) throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Password1");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }
}
