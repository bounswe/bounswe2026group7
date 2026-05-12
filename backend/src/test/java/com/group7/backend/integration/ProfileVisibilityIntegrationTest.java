package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.MenteeProfileRequest;
import com.group7.backend.dto.request.MentorProfileRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #570 — server-side profile visibility + 1.1.2.5 mentee surname/photo
 * masking. Eleven scenarios pinning the redaction matrix and list-level
 * exclusion at every reachable endpoint (single profile fetch, /mentors,
 * /mentees, /search, /matching).
 *
 * <p>Backward-compatibility callouts:
 * <ul>
 *   <li>{@link #migratedRowsDefaultToVisible_existingMentorReturned200} — V54's
 *       {@code NOT NULL DEFAULT TRUE} keeps pre-#570 mentors publicly visible.</li>
 *   <li>{@link #mentorPatch_setProfileVisibilityFalse_thenSelfStillVisible_othersBlocked}
 *       — owner self-view is unaffected; only third-party viewers are gated.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileVisibilityIntegrationTest {

    private static final String ADMIN_EMAIL = "visibility-admin@test.local";
    private static final String ADMIN_PASSWORD = "AdminVisibilityPwd1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(User.class), anyString());
    }

    // ── 1. selfView — private mentee can see their own /me ───────────────────

    @Test
    void selfView_privateProfile_allowed() throws Exception {
        String menteeToken = registerAndLogin("Ali", "Yilmaz", "self-private@test.com", false);
        // Toggle privacy via PATCH.
        MenteeProfileRequest req = new MenteeProfileRequest();
        req.setProfileVisibility(false);
        mockMvc.perform(patch("/api/users/me/mentee")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Self can still view /me.
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVisibility").value(false));
    }

    // ── 2. selfView by id — private mentee can see /users/{ownId} ────────────

    @Test
    void selfViewById_privateProfile_allowed() throws Exception {
        String menteeToken = registerAndLogin("Ali", "Yilmaz", "self-by-id@test.com", false);
        Long id = getUserId(menteeToken);
        setMenteePrivate(menteeToken, false);

        mockMvc.perform(get("/api/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    // ── 3. mentee → private mentor returns 403 ───────────────────────────────

    @Test
    void otherMentee_viewsPrivateMentor_returns403() throws Exception {
        String mentorToken = registerAndLogin("Mira", "Demir", "priv-mentor@test.com", true);
        Long mentorId = getUserId(mentorToken);
        setMentorPrivate(mentorToken);
        String menteeToken = registerAndLogin("Ali", "Yilmaz", "viewer-mentee@test.com", false);

        mockMvc.perform(get("/api/users/" + mentorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }

    // ── 4. other mentor → private mentor returns 403 ─────────────────────────

    @Test
    void otherMentor_viewsPrivateMentor_returns403() throws Exception {
        String privateMentorToken = registerAndLogin("Mira", "Demir", "priv-mentor2@test.com", true);
        Long privateMentorId = getUserId(privateMentorToken);
        setMentorPrivate(privateMentorToken);
        String otherMentorToken = registerAndLogin("Cem", "Kara", "other-mentor@test.com", true);

        mockMvc.perform(get("/api/users/" + privateMentorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherMentorToken))
                .andExpect(status().isForbidden());
    }

    // ── 5. mentor → visible mentee → lastName + photo masked ─────────────────

    @Test
    void mentor_viewsMentee_masksLastNameAndPhoto() throws Exception {
        String mentorToken = registerAndLogin("Mira", "Demir", "mask-mentor@test.com", true);
        String menteeToken = registerAndLogin("Ali", "Yilmaz", "mask-mentee@test.com", false);
        Long menteeId = getUserId(menteeToken);

        mockMvc.perform(get("/api/users/" + menteeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.lastName").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.profilePhoto").value(Matchers.nullValue()));
    }

    // ── 6. mentee → visible mentor → unmasked ───────────────────────────────

    @Test
    void mentee_viewsMentor_unmasked() throws Exception {
        String mentorToken = registerAndLogin("Mira", "Demir", "visible-mentor@test.com", true);
        Long mentorId = getUserId(mentorToken);
        String menteeToken = registerAndLogin("Ali", "Yilmaz", "viewer-mentee2@test.com", false);

        mockMvc.perform(get("/api/users/" + mentorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Mira"))
                .andExpect(jsonPath("$.lastName").value("Demir"));
    }

    // ── 7. private mentor absent from /mentors, /search, /matching ──────────

    @Test
    void privateMentor_notInListEndpoints() throws Exception {
        String privateMentorToken = registerAndLogin("Mira", "Demir", "list-priv-mentor@test.com", true);
        Long privateMentorId = getUserId(privateMentorToken);
        setMentorPrivate(privateMentorToken);
        String publicMentorToken = registerAndLogin("Cem", "Kara", "list-pub-mentor@test.com", true);
        Long publicMentorId = getUserId(publicMentorToken);
        String menteeToken = registerAndLogin("Ali", "Yilmaz", "list-viewer-mentee@test.com", false);

        // /api/users/mentors
        MvcResult mentorsList = mockMvc.perform(get("/api/users/mentors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseDoesNotContainId(mentorsList, privateMentorId);
        assertResponseContainsId(mentorsList, publicMentorId);

        // /api/users/search?role=MENTOR
        MvcResult search = mockMvc.perform(get("/api/users/search?role=MENTOR")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseDoesNotContainId(search, privateMentorId);

        // /api/matching/mentors
        MvcResult matching = mockMvc.perform(get("/api/matching/mentors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseDoesNotContainId(matching, privateMentorId);
    }

    // ── 8. private mentee absent from /mentees, /search, /matching ──────────

    @Test
    void privateMentee_notInListEndpoints() throws Exception {
        String privateMenteeToken = registerAndLogin("Ali", "Yilmaz", "list-priv-mentee@test.com", false);
        Long privateMenteeId = getUserId(privateMenteeToken);
        setMenteePrivate(privateMenteeToken, false);

        String publicMenteeToken = registerAndLogin("Ela", "Aydin", "list-pub-mentee@test.com", false);
        Long publicMenteeId = getUserId(publicMenteeToken);
        String mentorToken = registerAndLogin("Mira", "Demir", "list-viewer-mentor@test.com", true);

        // /api/users/mentees
        MvcResult list = mockMvc.perform(get("/api/users/mentees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseDoesNotContainId(list, privateMenteeId);
        assertResponseContainsId(list, publicMenteeId);

        // /api/users/search?role=MENTEE
        MvcResult search = mockMvc.perform(get("/api/users/search?role=MENTEE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseDoesNotContainId(search, privateMenteeId);

        // /api/matching/mentees (mentor needs capacity which the default 3 satisfies)
        MvcResult matching = mockMvc.perform(get("/api/matching/mentees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseDoesNotContainId(matching, privateMenteeId);
    }

    // ── 9. admin sees all profiles including private ─────────────────────────

    @Test
    void admin_seesAllProfilesIncludingPrivate() throws Exception {
        seedAdmin();
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");

        String privateMentorToken = registerAndLogin("Mira", "Demir", "admin-priv-m@test.com", true);
        Long privateMentorId = getUserId(privateMentorToken);
        setMentorPrivate(privateMentorToken);

        String privateMenteeToken = registerAndLogin("Ali", "Yilmaz", "admin-priv-me@test.com", false);
        Long privateMenteeId = getUserId(privateMenteeToken);
        setMenteePrivate(privateMenteeToken, false);

        // Single-profile fetch — admin gets 200 + no masking.
        mockMvc.perform(get("/api/users/" + privateMentorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Demir"));

        mockMvc.perform(get("/api/users/" + privateMenteeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Yilmaz"));

        // /api/users list — admin bypasses the visibility filter.
        MvcResult all = mockMvc.perform(get("/api/users?size=100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseContainsId(all, privateMentorId);
        assertResponseContainsId(all, privateMenteeId);
    }

    // ── 10. migrated rows default to visible ─────────────────────────────────

    @Test
    void migratedRowsDefaultToVisible_existingMentorReturned200() throws Exception {
        // Mentor.profileVisibility defaults to true at JPA level, mirroring V54
        // (NOT NULL DEFAULT TRUE). Verify the mentor entity field defaults to true
        // — captures any future regression where the default flips.
        String mentorToken = registerAndLogin("Default", "Visible", "default-visible@test.com", true);
        Long mentorId = getUserId(mentorToken);

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVisibility").value(true));

        String otherToken = registerAndLogin("Other", "Mentee", "other-default@test.com", false);
        mockMvc.perform(get("/api/users/" + mentorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk());
    }

    // ── 11. mentor patches to false → self still 200, others 403 ─────────────

    @Test
    void mentorPatch_setProfileVisibilityFalse_thenSelfStillVisible_othersBlocked() throws Exception {
        String mentorToken = registerAndLogin("Mira", "Demir", "patch-mentor@test.com", true);
        Long mentorId = getUserId(mentorToken);
        setMentorPrivate(mentorToken);

        // Owner sees themselves with profileVisibility=false.
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVisibility").value(false));
        mockMvc.perform(get("/api/users/" + mentorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk());

        // Third-party mentee → 403.
        String menteeToken = registerAndLogin("Ali", "Yilmaz", "patch-viewer-mentee@test.com", false);
        mockMvc.perform(get("/api/users/" + mentorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Long getUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private void setMentorPrivate(String token) throws Exception {
        MentorProfileRequest req = new MentorProfileRequest();
        req.setProfileVisibility(false);
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private void setMenteePrivate(String token, boolean visibility) throws Exception {
        MenteeProfileRequest req = new MenteeProfileRequest();
        req.setProfileVisibility(visibility);
        mockMvc.perform(patch("/api/users/me/mentee")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private void assertResponseContainsId(MvcResult result, Long expectedId) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = body.has("content") ? body.get("content") : body;
        boolean found = false;
        for (JsonNode entry : content) {
            if (entry.has("id") && entry.get("id").asLong() == expectedId) {
                found = true;
                break;
            }
        }
        assertThat(found)
                .as("expected id %d in response %s", expectedId, content.toString())
                .isTrue();
    }

    private void assertResponseDoesNotContainId(MvcResult result, Long forbiddenId) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = body.has("content") ? body.get("content") : body;
        for (JsonNode entry : content) {
            if (entry.has("id")) {
                assertThat(entry.get("id").asLong())
                        .as("response unexpectedly contained id %d", forbiddenId)
                        .isNotEqualTo(forbiddenId);
            }
        }
    }

    private Admin seedAdmin() {
        Admin admin = new Admin();
        admin.setFirstName("Visibility");
        admin.setLastName("Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setIsEmailVerified(true);
        return userRepository.save(admin);
    }

    private String login(String email, String password, String expectedRole) throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(expectedRole))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    private String registerAndLogin(String firstName, String lastName, String email, boolean isMentor) throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setFirstName(firstName);
        reg.setLastName(lastName);
        reg.setEmail(email);
        reg.setPassword("Password1");
        reg.setIsMentor(isMentor);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        String token = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
                .andExpect(status().isOk());

        return login(email, "Password1", isMentor ? "MENTOR" : "MENTEE");
    }

    // Silence unused-import warnings for Mentor / Mentee referenced in javadoc only.
    @SuppressWarnings("unused")
    private static final Class<?>[] DOC_REFS = { Mentor.class, Mentee.class };
}
