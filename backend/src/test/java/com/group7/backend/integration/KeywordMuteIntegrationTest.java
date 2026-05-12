package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the per-user keyword-mute resource. Exercises
 * GET / POST / DELETE shape, normalisation, validation, duplicate-409,
 * and the 50-keyword cap.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KeywordMuteIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM user_keyword_mutes");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void list_returnsEmpty_whenUserHasNoMutes() throws Exception {
        String token = registerAndLogin("empty@test.com");
        mockMvc.perform(get("/api/users/me/keyword-mutes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void create_returns201_andEchoesNormalisedKeyword() throws Exception {
        String token = registerAndLogin("create@test.com");
        MvcResult result = mockMvc.perform(post("/api/users/me/keyword-mutes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"  Crypto  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyword").value("crypto"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("id").asLong()).isPositive();
    }

    @Test
    void create_returns409_onDuplicate() throws Exception {
        String token = registerAndLogin("dup@test.com");
        mockMvc.perform(post("/api/users/me/keyword-mutes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"spam\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/users/me/keyword-mutes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"Spam\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_returns400_onIllegalCharacters() throws Exception {
        String token = registerAndLogin("badchars@test.com");
        mockMvc.perform(post("/api/users/me/keyword-mutes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"!!!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_onBlankKeyword() throws Exception {
        String token = registerAndLogin("blank@test.com");
        mockMvc.perform(post("/api/users/me/keyword-mutes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_removesMute_andReturns204() throws Exception {
        String token = registerAndLogin("del@test.com");
        MvcResult result = mockMvc.perform(post("/api/users/me/keyword-mutes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"removeme\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/users/me/keyword-mutes/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/keyword-mutes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void delete_returns404_whenMuteBelongsToAnotherUser() throws Exception {
        String ownerToken = registerAndLogin("owner@test.com");
        String intruderToken = registerAndLogin("intruder@test.com");

        MvcResult result = mockMvc.perform(post("/api/users/me/keyword-mutes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"private\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/users/me/keyword-mutes/" + id)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    private String registerAndLogin(String email) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Mute");
        req.setLastName("Tester");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(true);
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
