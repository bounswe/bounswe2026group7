package com.group7.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.group7.backend.dto.response.TaxonomyHit;
import com.group7.backend.exception.TaxonomyUpstreamException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Looks up controlled-vocabulary identifiers used to tag profile fields.
 *
 * <p>Three sources are fronted behind a single Caffeine cache (1-hour TTL):
 * ESCO for professional skills, Wikidata for personal hobbies, and a static
 * ISCED-F catalogue bundled as a classpath resource for academic fields.
 * Upstream HTTP calls have a 3-second timeout; on timeout or 5xx the service
 * throws {@link TaxonomyUpstreamException} which the controller maps to 503.
 */
@Service
public class TaxonomyService {

    private final RestClient escoClient;
    private final RestClient wikidataClient;
    private final List<IscedFEntry> iscedFCatalogue;
    private final Cache<String, List<TaxonomyHit>> cache;

    public TaxonomyService(@Qualifier("escoRestClient") RestClient escoClient,
                           @Qualifier("wikidataRestClient") RestClient wikidataClient,
                           ObjectMapper objectMapper) {
        this.escoClient = escoClient;
        this.wikidataClient = wikidataClient;
        this.iscedFCatalogue = loadIscedFCatalogue(objectMapper);
        this.cache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats()
                .build();
    }

    public List<TaxonomyHit> searchSkills(String query, String lang, int limit) {
        String cacheKey = "skills|" + query + "|" + lang + "|" + limit;
        return cache.get(cacheKey, k -> fetchEscoSkills(query, lang, limit));
    }

    public List<TaxonomyHit> searchFields(String query, String lang, int limit) {
        String cacheKey = "fields|" + query + "|" + limit;
        return cache.get(cacheKey, k -> filterIscedFCatalogue(query, limit));
    }

    public List<TaxonomyHit> searchHobbies(String query, String lang, int limit) {
        String cacheKey = "hobbies|" + query + "|" + lang + "|" + limit;
        return cache.get(cacheKey, k -> fetchWikidataHobbies(query, lang, limit));
    }

    private List<TaxonomyHit> fetchEscoSkills(String query, String lang, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String uri = UriComponentsBuilder.fromPath("/search")
                .queryParam("text", query)
                .queryParam("language", lang)
                .queryParam("type", "skill")
                .queryParam("limit", limit)
                .toUriString();
        try {
            EscoSearchResponse response = escoClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(EscoSearchResponse.class);
            if (response == null || response.embedded() == null
                    || response.embedded().results() == null) {
                return List.of();
            }
            List<TaxonomyHit> hits = new ArrayList<>(response.embedded().results().size());
            for (EscoResult result : response.embedded().results()) {
                if (result.title() == null || result.uri() == null) {
                    continue;
                }
                hits.add(new TaxonomyHit(result.title(), result.uri()));
            }
            return hits;
        } catch (RestClientException e) {
            throw new TaxonomyUpstreamException("ESCO lookup failed", e);
        }
    }

    private List<TaxonomyHit> fetchWikidataHobbies(String query, String lang, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String uri = UriComponentsBuilder.fromPath("/w/api.php")
                .queryParam("action", "wbsearchentities")
                .queryParam("search", query)
                .queryParam("language", lang)
                .queryParam("format", "json")
                .queryParam("limit", limit)
                .queryParam("type", "item")
                .toUriString();
        try {
            WikidataSearchResponse response = wikidataClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(WikidataSearchResponse.class);
            if (response == null || response.search() == null) {
                return List.of();
            }
            List<TaxonomyHit> hits = new ArrayList<>(response.search().size());
            for (WikidataHit hit : response.search()) {
                if (hit.label() == null || hit.concepturi() == null) {
                    continue;
                }
                hits.add(new TaxonomyHit(hit.label(), hit.concepturi()));
            }
            return hits;
        } catch (RestClientException e) {
            throw new TaxonomyUpstreamException("Wikidata lookup failed", e);
        }
    }

    private List<TaxonomyHit> filterIscedFCatalogue(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<TaxonomyHit> hits = new ArrayList<>();
        for (IscedFEntry entry : iscedFCatalogue) {
            if (entry.label().toLowerCase(Locale.ROOT).contains(needle)
                    || entry.code().contains(needle)) {
                hits.add(new TaxonomyHit(entry.label(), entry.uri()));
                if (hits.size() >= limit) {
                    break;
                }
            }
        }
        return hits;
    }

    private static List<IscedFEntry> loadIscedFCatalogue(ObjectMapper objectMapper) {
        try (InputStream stream = new ClassPathResource("taxonomy/isced-f-2013.json").getInputStream()) {
            return objectMapper.readValue(stream, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ISCED-F catalogue from classpath", e);
        }
    }

    /**
     * Caffeine cache stats (hit/miss counts, average load penalty, etc.).
     * Exposed for tests and future actuator-style observability without
     * leaking the cache itself.
     */
    public CacheStats cacheStats() {
        return cache.stats();
    }

    private record IscedFEntry(String code, String label) {
        String uri() {
            return "http://data.europa.eu/esco/isced-f/" + code;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EscoSearchResponse(@JsonProperty("_embedded") EscoEmbedded embedded) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EscoEmbedded(List<EscoResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EscoResult(String title, String uri) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WikidataSearchResponse(List<WikidataHit> search) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WikidataHit(String id, String label, String concepturi) {}
}
