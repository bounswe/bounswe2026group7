package com.group7.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link SearchNormaliser} — the shared filter-input
 * sanitiser between {@code MatchingService} and {@code UserService.searchUsers}
 * (#262). Each branch is correctness-required (see class javadoc); this test
 * pins them.
 */
class SearchNormaliserTest {

    // ── keyword(...) ──────────────────────────────────────────────────────────

    @Test
    void keyword_nullReturnsNull() {
        assertThat(SearchNormaliser.keyword(null)).isNull();
    }

    @Test
    void keyword_emptyReturnsNull() {
        assertThat(SearchNormaliser.keyword("")).isNull();
    }

    @Test
    void keyword_whitespaceReturnsNull() {
        assertThat(SearchNormaliser.keyword("   ")).isNull();
    }

    @Test
    void keyword_tooShortReturnsNull() {
        assertThat(SearchNormaliser.keyword("ab")).isNull();
    }

    @Test
    void keyword_threeCharsReturnsLowercasedWildcard() {
        assertThat(SearchNormaliser.keyword("Foo")).isEqualTo("%foo%");
    }

    @Test
    void keyword_escapeOrderIsBarFirst() {
        // The escape character | must be doubled before % and _ are escaped,
        // otherwise |% would itself become ||% and break the ESCAPE clause.
        assertThat(SearchNormaliser.keyword("a|b")).isEqualTo("%a||b%");
    }

    @Test
    void keyword_percentIsEscaped() {
        assertThat(SearchNormaliser.keyword("100%")).isEqualTo("%100|%%");
    }

    @Test
    void keyword_underscoreIsEscaped() {
        assertThat(SearchNormaliser.keyword("foo_bar")).isEqualTo("%foo|_bar%");
    }

    // ── list(...) ────────────────────────────────────────────────────────────

    @Test
    void list_nullReturnsNull() {
        assertThat(SearchNormaliser.list(null)).isNull();
    }

    @Test
    void list_emptyReturnsNull() {
        assertThat(SearchNormaliser.list(List.of())).isNull();
    }

    @Test
    void list_allNullsReturnNull() {
        // After filtering out null entries the survivor list is empty — must
        // coalesce to null so the JPQL :list IS NULL gate fires.
        assertThat(SearchNormaliser.list(Arrays.asList(null, null))).isNull();
    }

    @Test
    void list_lowercasesSurvivors() {
        assertThat(SearchNormaliser.list(List.of("Java", "PYTHON")))
                .containsExactly("java", "python");
    }

    @Test
    void list_filtersNullsAndLowercases() {
        assertThat(SearchNormaliser.list(Arrays.asList("Foo", null, "BAR")))
                .containsExactly("foo", "bar");
    }

    // ── scalar(...) ──────────────────────────────────────────────────────────

    @Test
    void scalar_nullReturnsNull() {
        assertThat(SearchNormaliser.scalar(null)).isNull();
    }

    @Test
    void scalar_blankReturnsNull() {
        assertThat(SearchNormaliser.scalar("   ")).isNull();
    }

    @Test
    void scalar_lowercasesAndTrims() {
        assertThat(SearchNormaliser.scalar("  Computer Science  "))
                .isEqualTo("computer science");
    }

    // ── Locale independence ──────────────────────────────────────────────────
    // String.toLowerCase() (no args) uses Locale.getDefault(), which on a
    // Turkish-locale JVM lowercases "I" to "ı" (dotless). Postgres LOWER()
    // uses the database collation (typically en_US.UTF-8), and the two sides
    // would silently disagree on any keyword containing "I". All three
    // normalisers must use Locale.ROOT for predictable ASCII folding.

    @Test
    void keyword_isLocaleIndependent_underTurkishDefault() {
        Locale prev = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));
            // Without Locale.ROOT this would be "%bıg%" (dotless ı) under
            // Turkish locale, which never matches a column lowercased by
            // Postgres in en_US collation.
            assertThat(SearchNormaliser.keyword("BIG")).isEqualTo("%big%");
        } finally {
            Locale.setDefault(prev);
        }
    }

    @Test
    void list_isLocaleIndependent_underTurkishDefault() {
        Locale prev = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));
            assertThat(SearchNormaliser.list(List.of("AI", "INFRA")))
                    .containsExactly("ai", "infra");
        } finally {
            Locale.setDefault(prev);
        }
    }

    @Test
    void scalar_isLocaleIndependent_underTurkishDefault() {
        Locale prev = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));
            assertThat(SearchNormaliser.scalar("INDUSTRIAL ENGINEERING"))
                    .isEqualTo("industrial engineering");
        } finally {
            Locale.setDefault(prev);
        }
    }
}
