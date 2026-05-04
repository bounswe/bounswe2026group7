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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the controlled-vocabulary URI feature added in #320.
 *
 * <ul>
 *   <li>Legacy label-only payload still round-trips unchanged.</li>
 *   <li>Labels + URIs payload persists both halves and reads back symmetric.</li>
 *   <li>Length-mismatched parallel arrays are rejected with 400.</li>
 *   <li>Malformed URIs (wrong scheme, https variant, bad host) are rejected.</li>
 *   <li>Per-row validation forbids interest URIs from the wrong scheme.</li>
 *   <li>JSON-LD response emits {@code @id} for tagged entries and plain
 *       strings for legacy entries; the existing TODO is gone.</li>
 *   <li>The {@code /api/taxonomies/fields} endpoint returns ISCED-F URIs
 *       sourced from the bundled catalogue (no external HTTP).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileTaxonomyIntegrationTest {

    private static final MediaType LD_JSON = MediaType.parseMediaType("application/ld+json");
    private static final String ESCO_ML =
            "http://data.europa.eu/esco/skill/ccd0a1d9-afda-43d9-b901-96344886e14d";
    private static final String ESCO_JAVA =
            "http://data.europa.eu/esco/skill/abcdef12-3456-7890-abcd-ef1234567890";
    private static final String ISCED_F_CS = "http://data.europa.eu/esco/isced-f/0613";
    private static final String WIKIDATA_CHESS = "http://www.wikidata.org/entity/Q11633";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;

    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(User.class), anyString());
    }

    @Test
    void mentorProfileLegacyPayloadStillRoundTripsWithNullUriSiblings() throws Exception {
        String token = registerAndLogin("Ali", "Veli", "tax_legacy@test.com", true);

        Map<String, Object> patch = Map.of(
                "expertise", "Backend Development",
                "field", "Computer Science",
                "interests", List.of("AI", "Systems")
        );
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk());

        JsonNode body = readMe(token);
        assertThat(body.get("expertise").asText()).isEqualTo("Backend Development");
        assertThat(body.get("expertiseUri").isNull()).isTrue();
        assertThat(body.get("interests").isArray()).isTrue();
        assertThat(textValues(body.get("interests"))).containsExactly("AI", "Systems");
        // The URI sibling is always present in the response, with all-null entries
        // for legacy data; old clients ignore the field.
        assertThat(body.get("interestUris").isArray()).isTrue();
        assertThat(body.get("interestUris")).allMatch(JsonNode::isNull);
    }

    @Test
    void mentorProfileWithLabelsAndUrisRoundTripsBothHalves() throws Exception {
        String token = registerAndLogin("Bora", "Saridemir", "tax_full@test.com", true);

        Map<String, Object> patch = Map.of(
                "expertise", "Machine learning",
                "expertiseUri", ESCO_ML,
                "field", "Computer science",
                "fieldUri", ISCED_F_CS,
                "interests", List.of("chess", "Java"),
                "interestUris", Arrays.asList(WIKIDATA_CHESS, ESCO_JAVA),
                "preferredMenteeSkills", List.of("Java"),
                "preferredMenteeSkillUris", List.of(ESCO_JAVA)
        );
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk());

        JsonNode body = readMe(token);
        assertThat(body.get("expertiseUri").asText()).isEqualTo(ESCO_ML);
        assertThat(body.get("fieldUri").asText()).isEqualTo(ISCED_F_CS);
        assertThat(textValues(body.get("interestUris")))
                .containsExactly(WIKIDATA_CHESS, ESCO_JAVA);
        assertThat(textValues(body.get("preferredMenteeSkillUris")))
                .containsExactly(ESCO_JAVA);
    }

    @Test
    void mentorProfileWithMismatchedParallelLengthsReturns400() throws Exception {
        String token = registerAndLogin("Mismatch", "Test", "tax_mismatch@test.com", true);

        Map<String, Object> patch = Map.of(
                "interests", List.of("AI", "Systems"),
                "interestUris", List.of(WIKIDATA_CHESS) // length 1 vs labels length 2
        );
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mentorProfileWithMalformedUriReturns400() throws Exception {
        String token = registerAndLogin("Bad", "Uri", "tax_baduri@test.com", true);

        Map<String, Object> patch = Map.of(
                "expertise", "ML",
                "expertiseUri", "not-a-uri"
        );
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mentorExpertiseRejectsHttpsVariant() throws Exception {
        String token = registerAndLogin("Https", "Test", "tax_https@test.com", true);

        Map<String, Object> patch = Map.of(
                "expertise", "ML",
                "expertiseUri", ESCO_ML.replace("http://", "https://")
        );
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mentorInterestsAcceptEscoOrWikidataButRejectIscedF() throws Exception {
        String token = registerAndLogin("Mix", "Schemes", "tax_mix@test.com", true);

        // Wikidata in interests slot is allowed (hobbies)
        Map<String, Object> wikidataPatch = Map.of(
                "interests", List.of("chess"),
                "interestUris", List.of(WIKIDATA_CHESS)
        );
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wikidataPatch)))
                .andExpect(status().isOk());

        // ISCED-F URI in interests slot is not allowed
        Map<String, Object> iscedPatch = Map.of(
                "interests", List.of("computer science"),
                "interestUris", List.of(ISCED_F_CS)
        );
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(iscedPatch)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mentorJsonLdEmitsAtIdForTaggedEntriesAndPlainStringForUntagged() throws Exception {
        String token = registerAndLogin("Ld", "Mentor", "tax_ld@test.com", true);

        Map<String, Object> patch = Map.of(
                "expertise", "Machine learning",
                "expertiseUri", ESCO_ML,
                "interests", List.of("chess", "free-text-hobby"),
                "interestUris", Arrays.asList(WIKIDATA_CHESS, null)
        );
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk());

        MvcResult ld = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(LD_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(ld.getResponse().getContentAsString());

        JsonNode knowsAbout = body.get("knowsAbout");
        assertThat(knowsAbout).isNotNull();
        assertThat(knowsAbout.isArray()).isTrue();

        // First entry: expertise with URI → JSON-LD node with @id
        JsonNode first = knowsAbout.get(0);
        assertThat(first.isObject()).as("tagged entry is a JSON-LD node").isTrue();
        assertThat(first.get("@id").asText()).isEqualTo(ESCO_ML);
        assertThat(first.get("name").asText()).isEqualTo("Machine learning");

        // Second entry: chess with Wikidata URI
        JsonNode second = knowsAbout.get(1);
        assertThat(second.isObject()).isTrue();
        assertThat(second.get("@id").asText()).isEqualTo(WIKIDATA_CHESS);

        // Third entry: free-text-hobby has no URI → plain string
        JsonNode third = knowsAbout.get(2);
        assertThat(third.isTextual()).as("untagged entry is a plain string").isTrue();
        assertThat(third.asText()).isEqualTo("free-text-hobby");
    }

    @Test
    void taxonomyFieldsEndpointReturnsIscedFUrisForBundledCatalogue() throws Exception {
        String token = registerAndLogin("Tax", "User", "tax_endpoint@test.com", false);

        MvcResult result = mockMvc.perform(get("/api/taxonomies/fields")
                        .param("q", "Computer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isPositive();
        for (JsonNode hit : body) {
            assertThat(hit.get("identifierUri").asText())
                    .matches("http://data\\.europa\\.eu/esco/isced-f/[0-9]{4}");
        }
    }

    @Test
    void taxonomyEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/taxonomies/fields").param("q", "x"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status)
                            .as("unauthenticated requests must be rejected")
                            .isIn(401, 403);
                });
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private String registerAndLogin(String firstName, String lastName,
                                    String email, boolean isMentor) throws Exception {
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

        String verificationToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verificationToken))
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

    private JsonNode readMe(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> textValues(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(n -> n.isNull() ? null : n.asText())
                .toList();
    }
}
