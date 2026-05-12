package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JsonLdContentNegotiationTest {

    private static final String LD_JSON = "application/ld+json";
    private static final String LD_JSON_WITH_CHARSET = "application/ld+json;charset=UTF-8";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private AvailabilitySlotRepository availabilitySlotRepository;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        availabilitySlotRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void plainJsonRequest_returnsPlainJsonResponseUnchanged() throws Exception {
        String token = registerAndLogin("Esra", "Yilmaz", "ld_mentor1@test.com", true);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@context")).isNull();
        assertThat(body.get("@type")).isNull();
        assertThat(body.get("firstName").asText()).isEqualTo("Esra");
        assertThat(body.get("role").asText()).isEqualTo("MENTOR");
    }

    @Test
    void ldJsonRequest_returnsPersonJsonLdForMentor() throws Exception {
        String token = registerAndLogin("Esra", "Yilmaz", "ld_mentor2@test.com", true);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(LD_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(LD_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@context").asText()).isEqualTo("https://schema.org");
        assertThat(body.get("@type").asText()).isEqualTo("Person");
        String id = body.get("@id").asText();
        assertThat(id).as("@id must be an absolute IRI").startsWith("http");
        assertThat(id).contains("/api/users/");
        assertThat(body.get("givenName").asText()).isEqualTo("Esra");
        assertThat(body.get("familyName").asText()).isEqualTo("Yilmaz");
        assertThat(body.get("email").asText()).isEqualTo("ld_mentor2@test.com");
        // mentor without filled-in profile has no bio/expertise/etc — those fields should be omitted
        assertThat(body.get("description")).isNull();
        // legacy fields should not appear
        assertThat(body.get("firstName")).isNull();
        assertThat(body.get("role")).isNull();
    }

    @Test
    void ldJsonRequest_fullyPopulatedMentor_emitsAllFields() throws Exception {
        String token = registerAndLogin("Sami", "Cakmak", "ld_mentor_full@test.com", true);

        Map<String, Object> patch = Map.of(
                "bio", "10+ years backend engineer.",
                "field", "Computer Science",
                "expertise", "Distributed Systems",
                "affiliation", "Bogazici University",
                "interests", List.of("Distributed Systems", "Databases", "Performance Engineering")
        );
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(LD_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("description").asText()).isEqualTo("10+ years backend engineer.");
        assertThat(body.get("jobTitle").asText()).isEqualTo("Computer Science");
        assertThat(body.get("affiliation").asText()).isEqualTo("Bogazici University");

        JsonNode knowsAbout = body.get("knowsAbout");
        assertThat(knowsAbout).isNotNull();
        assertThat(knowsAbout.isArray()).isTrue();
        // expertise leads, then interests; "Distributed Systems" appears once due to dedup
        List<String> values = new ArrayList<>();
        knowsAbout.forEach(n -> values.add(n.asText()));
        assertThat(values).containsExactly("Distributed Systems", "Databases", "Performance Engineering");
    }

    @Test
    void ldJsonRequest_returnsPersonJsonLdForMentee() throws Exception {
        String token = registerAndLogin("Ali", "Demir", "ld_mentee1@test.com", false);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(LD_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@type").asText()).isEqualTo("Person");
        assertThat(body.get("givenName").asText()).isEqualTo("Ali");
        assertThat(body.get("familyName").asText()).isEqualTo("Demir");
        // memberOf was incorrect for mentee.major; verify it does NOT appear
        assertThat(body.get("memberOf")).isNull();
    }

    @Test
    void ldJsonRequest_wrapsAvailabilityListAsGraph() throws Exception {
        String mentorToken = registerAndLogin("Cem", "Kaya", "ld_mentor3@test.com", true);
        Long mentorId = userRepository.findByEmail("ld_mentor3@test.com").orElseThrow().getId();

        // add two availability slots so we can verify graph wrapping
        mockMvc.perform(post("/api/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00",
                                        "endTime", "12:00", "recurring", true))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "TUESDAY", "startTime", "13:00",
                                        "endTime", "17:00", "recurring", true))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/availability/" + mentorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken)
                        .accept(LD_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(LD_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@context").asText()).isEqualTo("https://schema.org");
        JsonNode graph = body.get("@graph");
        assertThat(graph).isNotNull();
        assertThat(graph.isArray()).isTrue();
        assertThat(graph).hasSize(2);

        JsonNode firstSlot = graph.get(0);
        assertThat(firstSlot.get("@type").asText()).isEqualTo("Schedule");
        assertThat(firstSlot.get("@context")).as("inner items must not duplicate @context").isNull();
        assertThat(firstSlot.get("byDay").asText()).startsWith("https://schema.org/");
        assertThat(firstSlot.get("startTime").asText()).startsWith("09:00");
        assertThat(firstSlot.get("repeatFrequency").asText()).isEqualTo("P1W");
    }

    @Test
    void ldJsonRequest_emptyAvailabilityList_returnsEmptyGraph() throws Exception {
        String mentorToken = registerAndLogin("Empty", "Slots", "ld_mentor_empty@test.com", true);
        Long mentorId = userRepository.findByEmail("ld_mentor_empty@test.com").orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/api/availability/" + mentorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken)
                        .accept(LD_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(LD_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@context").asText()).isEqualTo("https://schema.org");
        JsonNode graph = body.get("@graph");
        assertThat(graph).isNotNull();
        assertThat(graph.isArray()).isTrue();
        assertThat(graph).isEmpty();
    }

    @Test
    void ldJsonRequest_pageResponse_wrapsAsOrderedCollectionPage() throws Exception {
        // Before #490 this test pinned the Page<T> downgrade-to-JSON
        // behaviour; OrderedCollectionPageJsonLdAdvice now models pagination
        // as an AS 2.0 OrderedCollectionPage and preserves the negotiated
        // Content-Type, so the assertion flips: Content-Type stays
        // application/ld+json and the body is the envelope with
        // orderedItems, totalItems, and hypermedia paging links.
        registerAndLogin("Page", "Mentor1", "ld_page1@test.com", true);
        registerAndLogin("Page", "Mentor2", "ld_page2@test.com", true);

        String menteeToken = registerAndLogin("Pager", "Reader", "ld_pager@test.com", false);

        MvcResult result = mockMvc.perform(get("/api/users/mentors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken)
                        .param("page", "0")
                        .param("size", "10")
                        .accept(LD_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(LD_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@context")).isNotNull();
        assertThat(body.get("@type").asText()).isEqualTo("OrderedCollectionPage");
        assertThat(body.get("totalItems").asLong()).isEqualTo(2L);
        assertThat(body.get("orderedItems")).isNotNull();
        assertThat(body.get("orderedItems").isArray()).isTrue();
        assertThat(body.get("orderedItems").size()).isEqualTo(2);
        // first / last always present; prev / next absent on a single-page result.
        assertThat(body.get("first")).isNotNull();
        assertThat(body.get("last")).isNotNull();
        assertThat(body.get("prev")).isNull();
        assertThat(body.get("next")).isNull();
    }

    @Test
    void ldJsonRequest_unmappedSingleBody_downgradesContentType() throws Exception {
        // /api/auth/login returns AuthResponse, which has no JSON-LD mapping.
        // The advice should downgrade Content-Type to plain JSON rather than
        // emit a body labeled as ld+json without a @context.
        registerAndLogin("Down", "Grade", "ld_downgrade@test.com", true);

        LoginRequest login = new LoginRequest();
        login.setEmail("ld_downgrade@test.com");
        login.setPassword("Password1");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(LD_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@context")).isNull();
        assertThat(body.get("sessionToken")).isNotNull();
        assertThat(body.get("role").asText()).isEqualTo("MENTOR");
    }

    @Test
    void ldJsonRequestWithCharset_returnsJsonLd() throws Exception {
        String token = registerAndLogin("Char", "Set", "ld_charset@test.com", true);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(LD_JSON_WITH_CHARSET))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(LD_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@context").asText()).isEqualTo("https://schema.org");
        assertThat(body.get("@type").asText()).isEqualTo("Person");
    }

    @Test
    void wildcardAcceptHeader_returnsPlainJson() throws Exception {
        String token = registerAndLogin("Wild", "Card", "ld_wildcard@test.com", true);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@context")).isNull();
        assertThat(body.get("firstName").asText()).isEqualTo("Wild");
    }

    // ── Helpers ─────────────────────────────────────────────

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

        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
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
