package com.group7.backend.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for the four taxonomy URI validators. Each accepts the canonical
 * linked-data URI for its scheme, accepts null (URI is optional), and rejects
 * mismatched schemes, https variants (linked-data is canonical http://), and
 * malformed shapes.
 */
class UriValidatorTest {

    private static final String VALID_ESCO =
            "http://data.europa.eu/esco/skill/ccd0a1d9-afda-43d9-b901-96344886e14d";
    private static final String VALID_ISCED_F =
            "http://data.europa.eu/esco/isced-f/0613";
    private static final String VALID_WIKIDATA =
            "http://www.wikidata.org/entity/Q11633";

    private final EscoUriValidator esco = new EscoUriValidator();
    private final IscedFUriValidator iscedF = new IscedFUriValidator();
    private final WikidataUriValidator wikidata = new WikidataUriValidator();
    private final EscoOrWikidataUriValidator escoOrWikidata = new EscoOrWikidataUriValidator();

    @Test
    void escoValidatorAcceptsCanonicalAndNull() {
        assertThat(esco.isValid(VALID_ESCO, null)).isTrue();
        assertThat(esco.isValid(null, null)).isTrue();
    }

    @Test
    void escoValidatorRejectsHttpsAndOtherSchemes() {
        assertThat(esco.isValid(VALID_ESCO.replace("http://", "https://"), null))
                .as("https variants are not canonical linked-data identifiers")
                .isFalse();
        assertThat(esco.isValid(VALID_ISCED_F, null)).isFalse();
        assertThat(esco.isValid(VALID_WIKIDATA, null)).isFalse();
        assertThat(esco.isValid("not-a-uri", null)).isFalse();
        assertThat(esco.isValid("http://data.europa.eu/esco/skill/", null))
                .as("must have a non-empty UUID part")
                .isFalse();
    }

    @Test
    void iscedFValidatorRequiresFourDigits() {
        assertThat(iscedF.isValid(VALID_ISCED_F, null)).isTrue();
        assertThat(iscedF.isValid(null, null)).isTrue();
        assertThat(iscedF.isValid("http://data.europa.eu/esco/isced-f/061", null))
                .as("3-digit narrow-level codes are rejected; only 4-digit detailed level")
                .isFalse();
        assertThat(iscedF.isValid("http://data.europa.eu/esco/isced-f/06133", null))
                .as("5-digit values are rejected")
                .isFalse();
        assertThat(iscedF.isValid(VALID_ESCO, null)).isFalse();
    }

    @Test
    void wikidataValidatorAcceptsAnyQId() {
        assertThat(wikidata.isValid(VALID_WIKIDATA, null)).isTrue();
        assertThat(wikidata.isValid("http://www.wikidata.org/entity/Q1", null)).isTrue();
        assertThat(wikidata.isValid("http://www.wikidata.org/entity/Q1234567890", null))
                .isTrue();
        assertThat(wikidata.isValid(null, null)).isTrue();
    }

    @Test
    void wikidataValidatorRejectsHttpsAndPropertyForms() {
        assertThat(wikidata.isValid(VALID_WIKIDATA.replace("http://", "https://"), null))
                .isFalse();
        assertThat(wikidata.isValid("http://www.wikidata.org/entity/P31", null))
                .as("Wikidata properties (P-IDs) are not entity items")
                .isFalse();
        assertThat(wikidata.isValid("http://www.wikidata.org/wiki/Q11633", null))
                .as("/wiki/ paths are HTML pages, not entity URIs")
                .isFalse();
    }

    @Test
    void escoOrWikidataAcceptsBoth() {
        assertThat(escoOrWikidata.isValid(VALID_ESCO, null)).isTrue();
        assertThat(escoOrWikidata.isValid(VALID_WIKIDATA, null)).isTrue();
        assertThat(escoOrWikidata.isValid(null, null)).isTrue();
    }

    @Test
    void escoOrWikidataRejectsIscedF() {
        assertThat(escoOrWikidata.isValid(VALID_ISCED_F, null))
                .as("interest collections are skill-or-hobby, not field-of-study")
                .isFalse();
        assertThat(escoOrWikidata.isValid("not-a-uri", null)).isFalse();
    }
}
