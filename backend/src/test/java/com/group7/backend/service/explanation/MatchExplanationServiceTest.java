package com.group7.backend.service.explanation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.Mentee;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchExplanationServiceTest {

    private static MentorRecommendationProperties props(boolean enabled, int dailyCap) {
        return new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, 0, 0, 0, 0, 0, 0),
                new MentorRecommendationProperties.Signals(false, false, false, false, false, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                new MentorRecommendationProperties.Explanation(
                        enabled, "gpt-4o-mini", 3000,
                        new MentorRecommendationProperties.ExplanationCache(64, 30),
                        dailyCap));
    }

    private static Mentee mentee() {
        Mentee me = new Mentee();
        me.setId(7L);
        me.setGoals("learn react");
        me.setCareerInterest("frontend");
        me.setMajor("Computer Science");
        return me;
    }

    private static MentorMatchResponse mentor(long id, String firstName, String... factors) {
        MentorMatchResponse r = new MentorMatchResponse();
        r.setId(id);
        r.setFirstName(firstName);
        r.setMatchScore(70);
        r.setFactors(new ArrayList<>(List.of(factors)));
        return r;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(Supplier<T> s) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenAnswer(inv -> s.get());
        return p;
    }

    private static ChatResponse chatResponseFor(String json) {
        AssistantMessage msg = new AssistantMessage(json);
        Generation gen = new Generation(msg);
        return new ChatResponse(List.of(gen));
    }

    // ── Feature-flag / config branches ───────────────────────────────────

    @Test
    void disabled_doesNothing() {
        var svc = new MatchExplanationService(
                provider(() -> mock(ChatModel.class)),
                new ObjectMapper(),
                props(false, 10000),
                new SimpleMeterRegistry());
        var page = List.of(mentor(1L, "A", "x"));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
    }

    @Test
    void nullPageOrMenteeNoop() {
        var svc = new MatchExplanationService(
                provider(() -> mock(ChatModel.class)),
                new ObjectMapper(),
                props(true, 10000),
                new SimpleMeterRegistry());
        svc.attach(null, mentee());
        svc.attach(List.of(), mentee());
        svc.attach(List.of(mentor(1L, "A")), null);
    }

    @Test
    void emptyExplanationRecord_safeAndDisabled() {
        var noExplanationProps = new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0, 0, 0, 0, 0, 0, 0),
                new MentorRecommendationProperties.Signals(false, false, false, false, false, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                null);
        var svc = new MatchExplanationService(
                provider(() -> mock(ChatModel.class)),
                new ObjectMapper(),
                noExplanationProps,
                new SimpleMeterRegistry());
        var page = new ArrayList<>(List.of(mentor(1L, "A")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
    }

    // ── Happy path + caching ────────────────────────────────────────────

    @Test
    void successPath_attachesProseAndCachesForNextCall() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponseFor(
                "{\"explanations\":[{\"id\":1,\"explanation\":\"Bob's expertise matches your goal.\"}]}"));

        var meters = new SimpleMeterRegistry();
        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(), props(true, 10000), meters);

        var page1 = new ArrayList<>(List.of(mentor(1L, "Bob", "shared-skill:react")));
        svc.attach(page1, mentee());
        assertThat(page1.get(0).getExplanation()).isEqualTo("Bob's expertise matches your goal.");
        assertThat(meters.counter("explanation.cache.misses").count()).isEqualTo(1.0);
        assertThat(meters.counter("openai.chat.calls", "outcome", "success").count()).isEqualTo(1.0);

        // Second call same factor set → cache hit, no further LLM call.
        var page2 = new ArrayList<>(List.of(mentor(1L, "Bob", "shared-skill:react")));
        svc.attach(page2, mentee());
        assertThat(page2.get(0).getExplanation()).isEqualTo("Bob's expertise matches your goal.");
        assertThat(meters.counter("explanation.cache.hits").count()).isEqualTo(1.0);
        verify(chat, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void differentFactorsForSameMentor_busts_cache() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(
                chatResponseFor("{\"explanations\":[{\"id\":1,\"explanation\":\"first.\"}]}"),
                chatResponseFor("{\"explanations\":[{\"id\":1,\"explanation\":\"second.\"}]}"));

        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(),
                props(true, 10000), new SimpleMeterRegistry());

        var page1 = new ArrayList<>(List.of(mentor(1L, "Bob", "shared-skill:react")));
        var page2 = new ArrayList<>(List.of(mentor(1L, "Bob", "shared-skill:typescript")));  // different factors
        svc.attach(page1, mentee());
        svc.attach(page2, mentee());
        assertThat(page1.get(0).getExplanation()).isEqualTo("first.");
        assertThat(page2.get(0).getExplanation()).isEqualTo("second.");
        verify(chat, times(2)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    // ── Graceful degradation ────────────────────────────────────────────

    @Test
    void noChatModelBean_leavesProseNull() {
        var svc = new MatchExplanationService(
                provider(() -> null), new ObjectMapper(),
                props(true, 10000), new SimpleMeterRegistry());
        var page = new ArrayList<>(List.of(mentor(1L, "A", "x")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
    }

    @Test
    void chatThrows_leavesProseNullAndBumpsFailureCounter() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new RuntimeException("OpenAI 503"));
        var meters = new SimpleMeterRegistry();
        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(), props(true, 10000), meters);

        var page = new ArrayList<>(List.of(mentor(1L, "A", "x")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
        assertThat(meters.counter("openai.chat.calls", "outcome", "failure").count()).isEqualTo(1.0);
        verify(chat, atLeastOnce()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void malformedJsonResponse_leavesProseNullWithoutThrow() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponseFor("not even close to JSON"));
        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(),
                props(true, 10000), new SimpleMeterRegistry());

        var page = new ArrayList<>(List.of(mentor(1L, "A", "x")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
    }

    @Test
    void missingExplanationForOneMentor_leavesThatProseNullOnly() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponseFor(
                "{\"explanations\":[{\"id\":1,\"explanation\":\"good fit\"}]}"));
        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(),
                props(true, 10000), new SimpleMeterRegistry());

        var page = new ArrayList<>(List.of(mentor(1L, "A", "x"), mentor(2L, "B", "y")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isEqualTo("good fit");
        assertThat(page.get(1).getExplanation()).isNull();
    }

    @Test
    void dailyCapExceeded_skipsLlmCallAndIncrementsCapCounter() {
        ChatModel chat = mock(ChatModel.class);
        var meters = new SimpleMeterRegistry();
        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(),
                props(true, /*dailyCap*/ 0), meters);

        var page = new ArrayList<>(List.of(mentor(1L, "A", "x")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
        assertThat(meters.counter("openai.chat.calls", "outcome", "cap-exceeded").count()).isEqualTo(1.0);
        verify(chat, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    // ── Validation (post-call) ──────────────────────────────────────────

    @Test
    void prose_validation_rejectsTagsAndOverLongOutput() {
        assertThat(MatchExplanationService.validateProse(null)).isNull();
        assertThat(MatchExplanationService.validateProse("   ")).isNull();
        assertThat(MatchExplanationService.validateProse("normal one-liner.")).isEqualTo("normal one-liner.");
        assertThat(MatchExplanationService.validateProse("<script>alert(1)</script>")).isNull();
        assertThat(MatchExplanationService.validateProse("contains <b>bold</b> tag")).isNull();
        assertThat(MatchExplanationService.validateProse("x".repeat(241))).isNull();
        assertThat(MatchExplanationService.validateProse("x".repeat(240))).hasSize(240);
    }

    // ── sanitiseFreeText (prompt-injection defence) ─────────────────────

    @Test
    void sanitiseFreeText_nullReturnsEmpty() {
        assertThat(MatchExplanationService.sanitiseFreeText(null, 40)).isEmpty();
    }

    @Test
    void sanitiseFreeText_stripsControlCharactersToSpaces() {
        // NUL, newline, tab — all replaced with single spaces; runs collapsed.
        assertThat(MatchExplanationService.sanitiseFreeText("hello world", 40))
                .isEqualTo("hello world");
        assertThat(MatchExplanationService.sanitiseFreeText("foo\n\nbar", 40))
                .isEqualTo("foo bar");
        assertThat(MatchExplanationService.sanitiseFreeText("a\tb\tc", 40))
                .isEqualTo("a b c");
    }

    @Test
    void sanitiseFreeText_truncatesAtWordBoundaryWhenSpaceAvailable() {
        // "react developer mentor" capped at 20 chars: "react developer ment…"
        // After hard cut at index 20 the last space is at 15 (> maxChars/2 = 10),
        // so back off to that space → "react developer".
        assertThat(MatchExplanationService.sanitiseFreeText("react developer mentor", 20))
                .isEqualTo("react developer");
    }

    @Test
    void sanitiseFreeText_hardCutsWhenNoSpaceInBack_half() {
        // No spaces at all → fall through to hard cut at maxChars.
        assertThat(MatchExplanationService.sanitiseFreeText("a".repeat(500), 40))
                .hasSize(40);
    }

    @Test
    void sanitiseFreeText_hardCutsWhenLastSpaceIsTooEarly() {
        // 30 chars long, only space at index 4. Cap = 20, last space at 4 is
        // not > maxChars/2 (10), so the algorithm falls through to the
        // hard cut at the cap.
        String input = "abcd " + "x".repeat(25);
        String out = MatchExplanationService.sanitiseFreeText(input, 20);
        assertThat(out).hasSize(20);
    }

    @Test
    void sanitiseFreeText_underCapIsUnchanged() {
        assertThat(MatchExplanationService.sanitiseFreeText("short", 40)).isEqualTo("short");
    }

    @Test
    void sanitiseFreeText_collapsesMultipleSpaces() {
        assertThat(MatchExplanationService.sanitiseFreeText("a   b   c", 40))
                .isEqualTo("a b c");
    }

    @Test
    void signalsHash_isStableAndOrderIndependent() {
        String a = MatchExplanationService.signalsHash(List.of("alpha", "beta", "gamma"));
        String b = MatchExplanationService.signalsHash(List.of("gamma", "alpha", "beta"));
        assertThat(a).isEqualTo(b);
        assertThat(MatchExplanationService.signalsHash(List.of())).isEqualTo("none");
        assertThat(MatchExplanationService.signalsHash(null)).isEqualTo("none");
        // Different content → different hash.
        assertThat(a).isNotEqualTo(MatchExplanationService.signalsHash(List.of("alpha", "beta")));
    }

    // ── invokeChat / parseExplanationJson edge cases (coverage gate) ─────

    @Test
    void chatReturnsNullResponse_proseNullAndCountsAsFailure() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(null);
        var meters = new SimpleMeterRegistry();
        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(),
                props(true, 10000), meters);

        var page = new ArrayList<>(List.of(mentor(1L, "A", "x")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
        assertThat(meters.counter("openai.chat.calls", "outcome", "failure").count()).isEqualTo(1.0);
    }

    @Test
    void chatReturnsBlankContent_proseNull() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponseFor(""));
        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(),
                props(true, 10000), new SimpleMeterRegistry());

        var page = new ArrayList<>(List.of(mentor(1L, "A", "x")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
    }

    @Test
    void jsonWithoutExplanationsArray_yieldsNoProse() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponseFor("{\"unexpected\":\"shape\"}"));
        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(),
                props(true, 10000), new SimpleMeterRegistry());

        var page = new ArrayList<>(List.of(mentor(1L, "A", "x")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
    }

    @Test
    void shutdown_terminatesExecutorCleanly() {
        var svc = new MatchExplanationService(
                provider(() -> mock(ChatModel.class)),
                new ObjectMapper(),
                props(true, 10000),
                new SimpleMeterRegistry());
        // No throw means the @PreDestroy happy path completed; covers the
        // executor shutdown + awaitTermination(true) lines.
        svc.shutdown();
    }

    @Test
    void constructor_acceptsNullPropsForDefensiveBoot() {
        // A boot-time null props (e.g. binding failure that left the record
        // null) shouldn't crash construction. The service ends up disabled
        // — every call lands on the early-return.
        var svc = new MatchExplanationService(
                provider(() -> mock(ChatModel.class)),
                new ObjectMapper(),
                null,
                new SimpleMeterRegistry());
        var page = new ArrayList<>(List.of(mentor(1L, "A", "x")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isNull();
    }

    @Test
    void jsonWithMalformedEntries_skipsBadOnesButKeepsValid() {
        // Covers the parseExplanationJson branches: non-numeric id continues,
        // non-textual explanation continues, blank prose skipped.
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponseFor("{\"explanations\":["
                        + "{\"id\":\"not-a-long\",\"explanation\":\"a\"},"
                        + "{\"id\":1,\"explanation\":42},"
                        + "{\"id\":1,\"explanation\":\"  \"},"
                        + "{\"id\":1,\"explanation\":\"valid prose\"}"
                        + "]}"));
        var svc = new MatchExplanationService(
                provider(() -> chat), new ObjectMapper(),
                props(true, 10000), new SimpleMeterRegistry());

        var page = new ArrayList<>(List.of(mentor(1L, "A", "x")));
        svc.attach(page, mentee());
        assertThat(page.get(0).getExplanation()).isEqualTo("valid prose");
    }
}
