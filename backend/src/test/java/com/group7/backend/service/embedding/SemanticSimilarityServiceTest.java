package com.group7.backend.service.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.SemanticSimilarityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Coverage for the OpenAI embedding client. Uses {@link MockRestServiceServer}
 * bound to a {@link RestClient.Builder} (the verified pattern from
 * {@code TaxonomyServiceTest} in this codebase) so we never hit a real
 * OpenAI endpoint and the test stays a sub-second unit test.
 *
 * <p>Covers the documented contract:
 * <ol>
 *   <li>happy path returns the vector from the response payload;</li>
 *   <li>{@code Authorization: Bearer …} header is sent;</li>
 *   <li>request body carries {@code model} + {@code input}, JSON-encoded;</li>
 *   <li>blank / null input short-circuits and never calls OpenAI;</li>
 *   <li>blank API key short-circuits (env unset case);</li>
 *   <li>4xx response fails-open to {@code float[0]};</li>
 *   <li>5xx response fails-open to {@code float[0]};</li>
 *   <li>second call for the same text hits the cache (one HTTP call total);</li>
 *   <li>different texts each hit OpenAI;</li>
 *   <li>cache key is stable across whitespace differences in the input;</li>
 *   <li>cosineSimilarity over identical / orthogonal / different-length /
 *       all-zero vectors returns the expected values.</li>
 * </ol>
 */
class SemanticSimilarityServiceTest {

    private static final String API_KEY = "sk-test-key";
    private static final String MODEL = "text-embedding-3-small";

    private MockRestServiceServer server;
    private SemanticSimilarityService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://openai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SemanticSimilarityService(builder.build(), props(API_KEY));
        service.initCache();
    }

    @Test
    void happyPath_returnsEmbeddingFromResponseBody() throws Exception {
        server.expect(requestTo("http://openai.test/v1/embeddings"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.input").value("hello world"))
                .andRespond(withSuccess(jsonPayload(new float[]{0.1f, 0.2f, 0.3f}),
                        MediaType.APPLICATION_JSON));

        float[] vec = service.embed("hello world");
        assertThat(vec).hasSize(3).containsExactly(0.1f, 0.2f, 0.3f);
        server.verify();
    }

    @Test
    void blankInput_returnsEmpty_andSkipsHttpCall() {
        assertThat(service.embed(null)).isEmpty();
        assertThat(service.embed("")).isEmpty();
        assertThat(service.embed("   \t\n")).isEmpty();
        // server.verify() with no expectations = no calls were made
        server.verify();
    }

    @Test
    void blankApiKey_returnsEmpty_andSkipsHttpCall() {
        // Re-construct with blank api key
        RestClient.Builder b = RestClient.builder().baseUrl("http://openai.test");
        MockRestServiceServer s = MockRestServiceServer.bindTo(b).build();
        SemanticSimilarityService svc = new SemanticSimilarityService(b.build(), props(""));
        svc.initCache();

        assertThat(svc.embed("some text")).isEmpty();
        s.verify();
    }

    @Test
    void openai4xx_failsOpen_toEmptyVector() {
        server.expect(requestTo("http://openai.test/v1/embeddings"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));

        assertThat(service.embed("hi")).isEmpty();
        server.verify();
    }

    @Test
    void openai5xx_failsOpen_toEmptyVector() {
        server.expect(requestTo("http://openai.test/v1/embeddings"))
                .andRespond(withServerError());

        assertThat(service.embed("hi")).isEmpty();
        server.verify();
    }

    @Test
    void emptyDataArray_failsOpen() throws Exception {
        String payload = new ObjectMapper().writeValueAsString(Map.of("data", java.util.List.of()));
        server.expect(requestTo("http://openai.test/v1/embeddings"))
                .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

        assertThat(service.embed("hi")).isEmpty();
        server.verify();
    }

    @Test
    void secondCallForSameText_hitsCache_oneHttpCallTotal() throws Exception {
        server.expect(requestTo("http://openai.test/v1/embeddings"))
                .andRespond(withSuccess(jsonPayload(new float[]{0.5f}),
                        MediaType.APPLICATION_JSON));

        float[] first = service.embed("same text");
        float[] second = service.embed("same text");

        assertThat(first).containsExactly(0.5f);
        assertThat(second).containsExactly(0.5f);
        // Only one expectation registered → cache hit on the second call.
        server.verify();
    }

    @Test
    void cacheKey_isStableAcrossWhitespace() throws Exception {
        // We only set up ONE expectation; if cache keys differ between
        // "hello" and "  hello  ", the second call will fail mock-verify
        // with "no further expectations".
        server.expect(requestTo("http://openai.test/v1/embeddings"))
                .andRespond(withSuccess(jsonPayload(new float[]{0.42f}),
                        MediaType.APPLICATION_JSON));

        float[] first = service.embed("hello");
        float[] second = service.embed("  hello  ");

        assertThat(first).containsExactly(0.42f);
        assertThat(second).containsExactly(0.42f);
        server.verify();
    }

    @Test
    void differentTexts_eachHitOpenAi() throws Exception {
        server.expect(requestTo("http://openai.test/v1/embeddings"))
                .andRespond(withSuccess(jsonPayload(new float[]{0.1f}),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://openai.test/v1/embeddings"))
                .andRespond(withSuccess(jsonPayload(new float[]{0.2f}),
                        MediaType.APPLICATION_JSON));

        assertThat(service.embed("first")).containsExactly(0.1f);
        assertThat(service.embed("second")).containsExactly(0.2f);
        server.verify();
    }

    // ── cosineSimilarity helper ────────────────────────────────────────────

    @Test
    void cosine_identicalVectors_returnsOne() {
        float[] v = {0.6f, 0.8f};
        assertThat(SemanticSimilarityService.cosineSimilarity(v, v))
                .isCloseTo(1.0, offset(1e-6));
    }

    @Test
    void cosine_orthogonalVectors_returnsZero() {
        assertThat(SemanticSimilarityService.cosineSimilarity(
                new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isEqualTo(0.0);
    }

    @Test
    void cosine_emptyVector_returnsZero() {
        assertThat(SemanticSimilarityService.cosineSimilarity(
                new float[0], new float[]{1f})).isEqualTo(0.0);
        assertThat(SemanticSimilarityService.cosineSimilarity(
                new float[]{1f}, new float[0])).isEqualTo(0.0);
    }

    @Test
    void cosine_nullVector_returnsZero() {
        assertThat(SemanticSimilarityService.cosineSimilarity(null, new float[]{1f}))
                .isEqualTo(0.0);
        assertThat(SemanticSimilarityService.cosineSimilarity(new float[]{1f}, null))
                .isEqualTo(0.0);
    }

    @Test
    void cosine_differentLengths_returnsZero() {
        assertThat(SemanticSimilarityService.cosineSimilarity(
                new float[]{1f, 0f}, new float[]{1f, 0f, 0f})).isEqualTo(0.0);
    }

    @Test
    void cosine_zeroMagnitudeVector_returnsZero() {
        assertThat(SemanticSimilarityService.cosineSimilarity(
                new float[]{0f, 0f}, new float[]{1f, 0f})).isEqualTo(0.0);
    }

    @Test
    void cosine_partialSimilarity_returnsCorrectValue() {
        // (3,4)·(4,3) = 24; |(3,4)| = 5; |(4,3)| = 5; cosine = 24/25 = 0.96
        assertThat(SemanticSimilarityService.cosineSimilarity(
                new float[]{3f, 4f}, new float[]{4f, 3f}))
                .isCloseTo(0.96, offset(1e-6));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static String jsonPayload(float[] vector) throws Exception {
        return new ObjectMapper().writeValueAsString(
                Map.of("data", java.util.List.of(Map.of("embedding", vector))));
    }

    private static SemanticSimilarityProperties props(String apiKey) {
        return new SemanticSimilarityProperties(
                new SemanticSimilarityProperties.Openai(apiKey, "http://openai.test", MODEL),
                new SemanticSimilarityProperties.Cache(10_000, 24L),
                new SemanticSimilarityProperties.Request(5_000L));
    }
}
