package com.group7.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.response.TaxonomyHit;
import com.group7.backend.exception.TaxonomyUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * TaxonomyService is the only place HTTP calls to ESCO and Wikidata happen.
 * These tests pin the request shape, response parsing, cache behaviour, and
 * 503 mapping with stubbed clients — running them does not touch the network.
 */
class TaxonomyServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MockRestServiceServer escoServer;
    private MockRestServiceServer wikidataServer;
    private TaxonomyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder escoBuilder = RestClient.builder().baseUrl("http://esco.test");
        RestClient.Builder wikiBuilder = RestClient.builder().baseUrl("http://wikidata.test");
        escoServer = MockRestServiceServer.bindTo(escoBuilder).build();
        wikidataServer = MockRestServiceServer.bindTo(wikiBuilder).build();
        service = new TaxonomyService(escoBuilder.build(), wikiBuilder.build(), OBJECT_MAPPER);
    }

    @Test
    void searchSkillsParsesEscoEmbeddedResults() {
        escoServer.expect(requestTo(containsString("/search?text=Machine")))
                .andExpect(requestTo(containsString("language=en")))
                .andExpect(requestTo(containsString("type=skill")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"_embedded\": {\"results\": [" +
                                "{\"title\": \"Machine learning\"," +
                                "\"uri\": \"http://data.europa.eu/esco/skill/abc-123\"}" +
                                "]}}",
                        MediaType.APPLICATION_JSON));

        List<TaxonomyHit> hits = service.searchSkills("Machine", "en", 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).label()).isEqualTo("Machine learning");
        assertThat(hits.get(0).identifierUri())
                .isEqualTo("http://data.europa.eu/esco/skill/abc-123");
    }

    @Test
    void searchSkillsReturnsEmptyOnBlankQueryWithoutHittingUpstream() {
        // No server expectation set: any HTTP call would fail the test.
        assertThat(service.searchSkills("", "en", 10)).isEmpty();
        assertThat(service.searchSkills(null, "en", 10)).isEmpty();
    }

    @Test
    void searchSkillsCachesByQueryAndLang() {
        escoServer.expect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"_embedded\": {\"results\": []}}",
                        MediaType.APPLICATION_JSON));

        service.searchSkills("Java", "en", 10);
        // Second call with identical args must hit the cache, not the upstream.
        // MockRestServiceServer.verify() at end-of-test would fail if a second
        // request arrived, since only one expectation was queued.
        service.searchSkills("Java", "en", 10);

        escoServer.verify();
        assertThat(service.cacheStats().hitCount()).isPositive();
    }

    @Test
    void searchSkillsMapsUpstreamFailureToTaxonomyUpstreamException() {
        escoServer.expect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.searchSkills("Anything", "en", 10))
                .isInstanceOf(TaxonomyUpstreamException.class)
                .hasMessageContaining("ESCO");
    }

    @Test
    void searchHobbiesParsesWikidataConceptUri() {
        wikidataServer.expect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"search\": [" +
                                "{\"id\": \"Q11633\"," +
                                "\"label\": \"chess\"," +
                                "\"concepturi\": \"http://www.wikidata.org/entity/Q11633\"}" +
                                "]}",
                        MediaType.APPLICATION_JSON));

        List<TaxonomyHit> hits = service.searchHobbies("chess", "en", 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).label()).isEqualTo("chess");
        assertThat(hits.get(0).identifierUri())
                .isEqualTo("http://www.wikidata.org/entity/Q11633");
    }

    @Test
    void searchHobbiesMapsUpstreamFailureToTaxonomyUpstreamException() {
        wikidataServer.expect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.searchHobbies("anything", "en", 10))
                .isInstanceOf(TaxonomyUpstreamException.class)
                .hasMessageContaining("Wikidata");
    }

    @Test
    void searchFieldsMatchesIscedFLabelSubstring() {
        // No HTTP — fields use the bundled catalogue.
        List<TaxonomyHit> hits = service.searchFields("Computer", "en", 10);

        assertThat(hits).isNotEmpty();
        assertThat(hits)
                .allMatch(h -> h.identifierUri().startsWith("http://data.europa.eu/esco/isced-f/"))
                .allMatch(h -> h.identifierUri().matches(".+/[0-9]{4}$"));
        assertThat(hits)
                .extracting(TaxonomyHit::label)
                .anyMatch(l -> l.contains("Computer"));
    }

    @Test
    void searchFieldsMatchesIscedFFourDigitCode() {
        // 0613 is "Software and applications development and analysis"
        List<TaxonomyHit> hits = service.searchFields("0613", "en", 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).identifierUri())
                .isEqualTo("http://data.europa.eu/esco/isced-f/0613");
    }

    @Test
    void searchFieldsRespectsLimit() {
        List<TaxonomyHit> hits = service.searchFields("e", "en", 3);

        assertThat(hits).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void searchFieldsReturnsEmptyOnBlankQuery() {
        assertThat(service.searchFields("", "en", 10)).isEmpty();
        assertThat(service.searchFields(null, "en", 10)).isEmpty();
    }
}
