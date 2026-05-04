package com.group7.backend.controller;

import com.group7.backend.dto.response.TaxonomyHit;
import com.group7.backend.service.TaxonomyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/taxonomies")
@Tag(name = "Taxonomies",
        description = "Autocomplete lookups for ESCO skills, ISCED-F fields, and Wikidata hobbies")
public class TaxonomyController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final TaxonomyService taxonomyService;

    public TaxonomyController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/skills")
    @Operation(summary = "Search ESCO skills",
            description = "Returns canonical ESCO skill URIs whose label matches the query. "
                    + "URIs are linked-data identifiers (http://data.europa.eu/esco/skill/...).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matches returned"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "503", description = "Upstream taxonomy provider unreachable")
    })
    public ResponseEntity<List<TaxonomyHit>> searchSkills(
            @Parameter(description = "Free-text query") @RequestParam("q") String query,
            @Parameter(description = "Preferred language (BCP-47 tag); falls back to Accept-Language")
            @RequestParam(value = "lang", required = false) String lang,
            @Parameter(description = "Maximum results (1-50)") @RequestParam(value = "limit",
                    defaultValue = "" + DEFAULT_LIMIT) int limit,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                taxonomyService.searchSkills(query, resolveLang(lang, request), clampLimit(limit)));
    }

    @GetMapping("/fields")
    @Operation(summary = "Search ISCED-F fields",
            description = "Returns ESCO-published ISCED-F URIs whose label or 4-digit code matches "
                    + "the query. URIs are linked-data identifiers "
                    + "(http://data.europa.eu/esco/isced-f/<code>). Backed by a bundled catalogue, "
                    + "so this endpoint never makes external calls.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matches returned"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<List<TaxonomyHit>> searchFields(
            @RequestParam("q") String query,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                taxonomyService.searchFields(query, resolveLang(lang, request), clampLimit(limit)));
    }

    @GetMapping("/hobbies")
    @Operation(summary = "Search Wikidata hobbies",
            description = "Returns canonical Wikidata entity URIs whose label matches the query. "
                    + "URIs are linked-data identifiers (http://www.wikidata.org/entity/Q<id>).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matches returned"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "503", description = "Upstream taxonomy provider unreachable")
    })
    public ResponseEntity<List<TaxonomyHit>> searchHobbies(
            @RequestParam("q") String query,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                taxonomyService.searchHobbies(query, resolveLang(lang, request), clampLimit(limit)));
    }

    private static String resolveLang(String lang, HttpServletRequest request) {
        if (lang != null && !lang.isBlank()) {
            return lang.toLowerCase(Locale.ROOT);
        }
        Locale locale = request.getLocale();
        return locale != null ? locale.getLanguage() : "en";
    }

    private static int clampLimit(int requested) {
        if (requested < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
