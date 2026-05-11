package com.group7.backend.service;

import com.group7.backend.dto.response.MenteeCandidateResponse;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.config.SemanticSimilarityProperties;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.service.explanation.MatchExplanationService;
import com.group7.backend.service.ranking.MentorScoringPipeline;
import com.group7.backend.service.ranking.MmrReranker;
import com.group7.backend.service.ranking.RuleBasedMentorRanker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-level orchestration tests for {@link MatchingService}. The legacy
 * scoring algorithm assertions moved into
 * {@code RuleBasedMentorRankerTest} after the ranker extraction (#262); the
 * service uses a real {@link RuleBasedMentorRanker} instance here so the
 * orchestration assertions still observe end-to-end scoring outputs without
 * needing to mock the ranker.
 *
 * <p>Repository mocks reflect the post-#262 shape: the matching path uses
 * {@code findRankingCandidates} (List, no count) on both
 * {@code MentorRepository} and {@code MenteeRepository}, plus
 * {@code findByMentorIdIn} for the batch availability fetch. The
 * {@code searchByFilters} (Page) variants are exercised through
 * {@code UserSearchControllerTest} / {@code UserSearchIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock private MenteeRepository menteeRepository;
    @Mock private MentorRepository mentorRepository;
    @Mock private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock private MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;

    private MatchingService matchingService;

    private Mentee mentee;
    private Mentor mentor;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        // Real RuleBasedMentorRanker — pure function over already-loaded entities.
        // The pipeline wraps it with a no-op MMR (mmr.enabled=false) so scoring
        // stays byte-identical to the pre-decomposition behaviour these tests
        // were originally written against.
        var recProps = new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(false),
                new MentorRecommendationProperties.Weights(0, 0, 0, 0, 0, 0, 0),
                new MentorRecommendationProperties.Signals(false, false, false, false, false, false),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                new MentorRecommendationProperties.Mmr(false, 0.7),
                null);
        var simProps = new SemanticSimilarityProperties(
                "text-embedding-3-small",
                new SemanticSimilarityProperties.Cache(64, 1),
                true);
        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> noEmbeddingModel =
                org.mockito.Mockito.mock(ObjectProvider.class);
        lenient().when(noEmbeddingModel.getIfAvailable()).thenReturn(null);
        var sim = new SemanticSimilarityService(noEmbeddingModel, simProps, new SimpleMeterRegistry());

        var noCentroidStats = org.mockito.Mockito.mock(
                com.group7.backend.service.embedding.MentorPopulationStats.class);
        lenient().when(noCentroidStats.centroid()).thenReturn(java.util.Optional.empty());

        var pipeline = new MentorScoringPipeline(
                new RuleBasedMentorRanker(),
                recProps,
                sim,
                noCentroidStats);

        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.ai.chat.model.ChatModel> noChatModel =
                org.mockito.Mockito.mock(ObjectProvider.class);
        lenient().when(noChatModel.getIfAvailable()).thenReturn(null);
        var explanationService = new MatchExplanationService(
                noChatModel, new ObjectMapper(), recProps, new SimpleMeterRegistry());

        // Stub PlatformTransactionManager so TransactionTemplate.execute(...)
        // runs the callback inline — these are pure unit tests with no real
        // JPA session, so the transaction boundary doesn't matter. The
        // SimpleTransactionStatus stub lets commit() pass cleanly.
        var txManager = org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        lenient().when(txManager.getTransaction(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());

        matchingService = new MatchingService(
                menteeRepository, mentorRepository,
                availabilitySlotRepository, menteeAvailabilitySlotRepository,
                pipeline,
                explanationService,
                txManager,
                /*rankingWindow*/ 200);

        pageable = PageRequest.of(0, 20);

        mentee = new Mentee();
        mentee.setId(1L);
        mentee.setFirstName("Eli");
        mentee.setMajor("Computer Science");
        mentee.setGoals("career machine learning");
        mentee.setCareerInterest("backend engineering");
        mentee.setInterests(List.of("AI", "Databases"));
        mentee.setSkills(List.of("Java", "Python"));

        mentor = new Mentor();
        mentor.setId(2L);
        mentor.setFirstName("Mira");
        mentor.setMaxMenteeCapacity(3);
        mentor.setCurrentMenteeCount(1);
        mentor.setField("Computer Science");
        mentor.setExpertise("backend engineering Java");
        mentor.setPreferredMenteeMajor("Computer Science");
        mentor.setPreferredMenteeSkills(List.of("Java", "Kotlin"));
        mentor.setInterests(List.of("AI", "Systems"));
        mentor.setMentoringGoals("Help with career and machine learning projects");

        // Default: no availability data on either side — score reflects
        // profile-only contributions, mirroring the pre-refactor lenient
        // mocks. Tests that exercise availability override these.
        lenient().when(availabilitySlotRepository.findByMentorIdIn(anyCollection()))
                .thenReturn(List.of());
        lenient().when(menteeAvailabilitySlotRepository.findByMenteeId(anyLong()))
                .thenReturn(List.of());
    }

    // ── Active mentor / mentee not found ──────────────────────────────────

    @Test
    void activeMentorBlocksRequest() {
        mentee.setActiveMentorId(99L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));

        assertThatThrownBy(() -> matchingService.getTopMentors(1L, null, pageable))
                .isInstanceOf(com.group7.backend.exception.MatchingNotAllowedException.class)
                .hasMessageContaining("active mentor");
    }

    @Test
    void menteeNotFoundThrows() {
        when(menteeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.getTopMentors(99L, null, pageable))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Capacity filter (now SQL-side) ────────────────────────────────────

    @Test
    void searchByFilters_invokedWithRequireCapacityTrue() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        matchingService.getTopMentors(1L, null, pageable);

        // Verify the SQL filter pushes capacity, not in-memory.
        verify(mentorRepository).findRankingCandidates(
                any(), any(), any(), any(), eq(true), any(), any(Pageable.class));
    }

    // ── Pagination ────────────────────────────────────────────────────────

    @Test
    void paginationReturnsRequestedPageSize() {
        List<Mentor> sixMentors = java.util.stream.IntStream.range(0, 6).mapToObj(i -> {
            Mentor m = new Mentor();
            m.setId((long) (10 + i));
            m.setMaxMenteeCapacity(3);
            m.setCurrentMenteeCount(0);
            return m;
        }).toList();
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(sixMentors);

        Page<MentorMatchResponse> result = matchingService.getTopMentors(1L, null, PageRequest.of(0, 3));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void paginationReturnsSecondPage() {
        List<Mentor> sixMentors = java.util.stream.IntStream.range(0, 6).mapToObj(i -> {
            Mentor m = new Mentor();
            m.setId((long) (10 + i));
            m.setMaxMenteeCapacity(3);
            m.setCurrentMenteeCount(0);
            return m;
        }).toList();
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(sixMentors);

        Page<MentorMatchResponse> result = matchingService.getTopMentors(1L, null, PageRequest.of(1, 4));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getNumber()).isEqualTo(1);
    }

    @Test
    void paginationBeyondTotalReturnsEmptyPage() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        Page<MentorMatchResponse> result = matchingService.getTopMentors(1L, null, PageRequest.of(10, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void paginationWithIntegerOverflowOffset_returnsEmptyPage() {
        // page=Integer.MAX_VALUE with size>1 makes Pageable.getOffset() exceed
        // Integer.MAX_VALUE; a naive `(int) offset` cast wraps to a negative
        // start and crashes List.subList with IndexOutOfBoundsException.
        // slicePage compares the offset in long-space first to keep the cast
        // safe. Reachable through the public matching/search endpoints
        // because clampPageable does not bound the page number.
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        Page<MentorMatchResponse> result = matchingService.getTopMentors(
                1L, null, PageRequest.of(Integer.MAX_VALUE, 2));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── Keyword filter (now SQL-side) ─────────────────────────────────────

    @Test
    void keywordFilter_passesNormalisedKeywordToRepo() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        matchingService.getTopMentors(1L, "Java", pageable);

        // Service normaliseKeyword: trim + lowercase + escape + wrap %...%.
        verify(mentorRepository).findRankingCandidates(
                eq("%java%"), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void shortKeyword_skipsKeywordFilter() {
        // q="ab" length 2 < 3 — service treats as null, no filter applied.
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        matchingService.getTopMentors(1L, "ab", pageable);

        verify(mentorRepository).findRankingCandidates(
                eq(null), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void blankKeyword_skipsKeywordFilter() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        matchingService.getTopMentors(1L, "   ", pageable);

        verify(mentorRepository).findRankingCandidates(
                eq(null), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void nullKeyword_skipsKeywordFilter() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        matchingService.getTopMentors(1L, null, pageable);

        verify(mentorRepository).findRankingCandidates(
                eq(null), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void wildcardEscape_keywordContainingPercent_isLiteralised() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        matchingService.getTopMentors(1L, "abc%def", pageable);

        // Escape order: pipe first, then % and _. Result: "%abc|%def%".
        verify(mentorRepository).findRankingCandidates(
                eq("%abc|%def%"), any(), any(), any(), anyBoolean(), any(), any());
    }

    // ── Ordering ──────────────────────────────────────────────────────────

    @Test
    void resultsOrderedByScoreDescending() {
        Mentor lowScore = new Mentor();
        lowScore.setId(7L);
        lowScore.setMaxMenteeCapacity(3);
        lowScore.setCurrentMenteeCount(0);
        // No matching fields → score 0.

        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(lowScore, mentor));

        Page<MentorMatchResponse> result = matchingService.getTopMentors(1L, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getMatchScore())
                .isGreaterThanOrEqualTo(result.getContent().get(1).getMatchScore());
    }

    @Test
    void availabilityCanBreakTieBetweenMentors() {
        Mentor mentorWithoutOverlap = new Mentor();
        mentorWithoutOverlap.setId(9L);
        mentorWithoutOverlap.setMaxMenteeCapacity(3);
        mentorWithoutOverlap.setCurrentMenteeCount(0);
        mentorWithoutOverlap.setField(mentor.getField());
        mentorWithoutOverlap.setPreferredMenteeMajor(mentor.getPreferredMenteeMajor());
        mentorWithoutOverlap.setPreferredMenteeSkills(mentor.getPreferredMenteeSkills());
        mentorWithoutOverlap.setInterests(mentor.getInterests());
        mentorWithoutOverlap.setMentoringGoals(mentor.getMentoringGoals());
        mentorWithoutOverlap.setExpertise(mentor.getExpertise());

        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentorWithoutOverlap, mentor));

        // Mentor 2L has overlapping slot; mentor 9L does not.
        AvailabilitySlot mentor2Slot = new AvailabilitySlot();
        mentor2Slot.setDayOfWeek(DayOfWeek.MONDAY);
        mentor2Slot.setStartTime(LocalTime.parse("10:00"));
        mentor2Slot.setEndTime(LocalTime.parse("12:00"));
        mentor2Slot.setMentor(mentor);
        AvailabilitySlot mentor9Slot = new AvailabilitySlot();
        mentor9Slot.setDayOfWeek(DayOfWeek.MONDAY);
        mentor9Slot.setStartTime(LocalTime.parse("14:00"));
        mentor9Slot.setEndTime(LocalTime.parse("16:00"));
        mentor9Slot.setMentor(mentorWithoutOverlap);
        when(availabilitySlotRepository.findByMentorIdIn(anyCollection()))
                .thenReturn(List.of(mentor2Slot, mentor9Slot));

        MenteeAvailabilitySlot menteeSlot = new MenteeAvailabilitySlot();
        menteeSlot.setDayOfWeek(DayOfWeek.MONDAY);
        menteeSlot.setStartTime(LocalTime.parse("10:30"));
        menteeSlot.setEndTime(LocalTime.parse("11:30"));
        when(menteeAvailabilitySlotRepository.findByMenteeId(1L))
                .thenReturn(List.of(menteeSlot));

        Page<MentorMatchResponse> result = matchingService.getTopMentors(1L, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(2L);
        assertThat(result.getContent().get(0).getMatchScore())
                .isGreaterThan(result.getContent().get(1).getMatchScore());
    }

    // ── N+1 elimination — slot batch fetched once ─────────────────────────

    @Test
    void rankMentors_batchFetchesSlotsExactlyOnce() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        matchingService.getTopMentors(1L, null, pageable);

        verify(availabilitySlotRepository, times(1)).findByMentorIdIn(anyCollection());
        verify(availabilitySlotRepository, never()).findByMentorId(anyLong());
        verify(menteeAvailabilitySlotRepository, times(1)).findByMenteeId(1L);
    }

    @Test
    void rankMentors_emptyResult_skipsSlotFetches() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of());

        matchingService.getTopMentors(1L, null, pageable);

        verify(availabilitySlotRepository, never()).findByMentorIdIn(anyCollection());
        verify(menteeAvailabilitySlotRepository, never()).findByMenteeId(anyLong());
    }

    // ── Read paths are side-effect-free (#273 regression) ─────────────────
    // Removing the publishMatchFound dependency from MatchingService is a
    // structural change: the service no longer holds a NotificationEventPublisher
    // field, so it cannot publish anything regardless of input. The tests below
    // confirm the public methods still return the expected output without
    // throwing; the runtime "no notification row appears" assertion lives in
    // MatchNotificationIntegrationTest, where the full Spring stack confirms
    // browse never persists a Notification row. The seven previous notification-
    // firing tests moved to MatchNotificationProcessorTest.

    @Test
    void getTopMentors_returnsRankedListWithoutSideEffects() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        Page<MentorMatchResponse> result = matchingService.getTopMentors(1L, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getTopMentorsList_returnsRankedListWithoutSideEffects() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        List<MentorMatchResponse> result = matchingService.getTopMentorsList(1L, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void getCandidateMentees_returnsCandidateListWithoutSideEffects() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        stubMenteeSearch(List.of(mentee));

        Page<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    // ── Candidate mentees: full capacity / mentor not found ───────────────

    @Test
    void candidateMenteesFullCapacityBlocksRequest() {
        mentor.setCurrentMenteeCount(3); // full
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));

        assertThatThrownBy(() -> matchingService.getCandidateMentees(1L, null, pageable))
                .isInstanceOf(com.group7.backend.exception.MatchingNotAllowedException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void candidateMenteesExactCapacityBlocksRequest() {
        mentor.setMaxMenteeCapacity(2);
        mentor.setCurrentMenteeCount(2);
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));

        assertThatThrownBy(() -> matchingService.getCandidateMentees(1L, null, pageable))
                .isInstanceOf(com.group7.backend.exception.MatchingNotAllowedException.class);
    }

    @Test
    void candidateMenteesMentorNotFoundThrows() {
        when(mentorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.getCandidateMentees(99L, null, pageable))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Candidate mentees: SQL filters pushed correctly ───────────────────

    @Test
    void candidateMentees_invokesMenteeSearchWithRequireUnattachedTrue() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        stubMenteeSearch(List.of(mentee));

        matchingService.getCandidateMentees(1L, null, pageable);

        // requireUnattached=true (the SQL filter that replaced the old in-memory
        // m -> m.getActiveMentorId() == null check). requesterMentorId is null
        // because the matching path doesn't slot-filter candidate mentees —
        // overlap is part of scoring, not filtering, and the mentee path here
        // doesn't score.
        verify(menteeRepository).findRankingCandidates(
                any(), any(), any(), any(), eq(true),
                org.mockito.ArgumentMatchers.isNull(), any(Pageable.class));
    }

    @Test
    void candidateMentees_keywordIsNormalisedAndPushed() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        stubMenteeSearch(List.of(mentee));

        matchingService.getCandidateMentees(1L, "machine", pageable);

        verify(menteeRepository).findRankingCandidates(
                eq("%machine%"), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void candidateMentees_doesNotInvokeRanker() {
        // The mentor-side path scores via the ranker; the mentee-side path
        // is filter-only. Verifying via no-batch-fetch on mentor slots since
        // that's the only code path that would fire if the ranker were called.
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        stubMenteeSearch(List.of(mentee));

        matchingService.getCandidateMentees(1L, null, pageable);

        verify(availabilitySlotRepository, never()).findByMentorIdIn(anyCollection());
    }

    // ── Candidate mentees: pagination ─────────────────────────────────────

    @Test
    void candidateMenteesPagination_returnsRequestedSlice() {
        // Mentees need at least one preference overlap with the mentor fixture
        // (interests=[AI, Systems]) to survive the in-memory
        // matchesMentorPreferences filter.
        List<Mentee> manyMentees = java.util.stream.IntStream.range(0, 6).mapToObj(i -> {
            Mentee me = new Mentee();
            me.setId((long) (100 + i));
            me.setInterests(List.of("AI"));
            return me;
        }).toList();
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        stubMenteeSearch(manyMentees);

        Page<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null, PageRequest.of(0, 3));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(6);
    }

    // ── Candidate mentees: DTO mapping ────────────────────────────────────

    @Test
    void candidateMenteesResponseContainsCorrectFields() {
        mentee.setFirstName("Elif");
        mentee.setBackgroundInfo("3rd year CS student");
        mentee.setMeetingFreqPref("Weekly");
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        stubMenteeSearch(List.of(mentee));

        Page<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        MenteeCandidateResponse dto = result.getContent().get(0);
        assertThat(dto.getFirstName()).isEqualTo("Elif");
        assertThat(dto.getGoals()).isEqualTo("career machine learning");
        assertThat(dto.getMajor()).isEqualTo("Computer Science");
        assertThat(dto.getInterests()).containsExactly("AI", "Databases");
        assertThat(dto.getSkills()).containsExactly("Java", "Python");
        assertThat(dto.getCareerInterest()).isEqualTo("backend engineering");
        assertThat(dto.getBackgroundInfo()).isEqualTo("3rd year CS student");
        assertThat(dto.getMeetingFreqPref()).isEqualTo("Weekly");
    }

    // ── Unpaginated getTopMentorsList ─────────────────────────────────────

    @Test
    void getTopMentorsList_returnsAllRanked_capPreserved() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of(mentor));

        List<MentorMatchResponse> result = matchingService.getTopMentorsList(1L, null);

        assertThat(result).hasSize(1);
        // Same 200-cap as the paginated path (verified via PageRequest.of(0, 200)).
        verify(mentorRepository).findRankingCandidates(
                any(), any(), any(), any(), anyBoolean(), any(), eq(PageRequest.of(0, 200)));
    }

    @Test
    void getTopMentorsList_returnsEmptyListWhenNoCandidates() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        stubMentorSearch(List.of());

        List<MentorMatchResponse> result = matchingService.getTopMentorsList(1L, null);

        assertThat(result).isEmpty();
    }

    // ── matchesMentorPreferences edge cases ───────────────────────────────
    // Pin the OR-of-categories filter behaviour and its null-guard branches.
    // The mentor-side path always pre-filters via this in-memory check; getting
    // it wrong silently empties candidate-mentee results.

    @Test
    void matchesMentorPreferences_matchesViaInterestOverlap() {
        Mentor m = new Mentor();
        m.setInterests(List.of("AI"));
        Mentee me = new Mentee();
        me.setInterests(List.of("AI"));
        assertThat(MatchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    @Test
    void matchesMentorPreferences_matchesViaSkill() {
        Mentor m = new Mentor();
        m.setPreferredMenteeSkills(List.of("Java"));
        Mentee me = new Mentee();
        me.setSkills(List.of("Java"));
        assertThat(MatchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    @Test
    void matchesMentorPreferences_matchesViaPreferredMajor() {
        Mentor m = new Mentor();
        m.setPreferredMenteeMajor("Computer Science");
        Mentee me = new Mentee();
        me.setMajor("Computer Science");
        assertThat(MatchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    @Test
    void matchesMentorPreferences_matchesViaField() {
        Mentor m = new Mentor();
        m.setField("Computer Science");
        Mentee me = new Mentee();
        me.setMajor("Computer Science");
        assertThat(MatchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    @Test
    void matchesMentorPreferences_returnsFalseWhenNothingOverlaps() {
        Mentor m = new Mentor();
        m.setInterests(List.of("AI"));
        m.setPreferredMenteeSkills(List.of("Java"));
        m.setPreferredMenteeMajor("CS");
        Mentee me = new Mentee();
        me.setInterests(List.of("Music"));
        me.setSkills(List.of("Piano"));
        me.setMajor("Music Theory");
        assertThat(MatchingService.matchesMentorPreferences(m, me)).isFalse();
    }

    @Test
    void matchesMentorPreferences_handlesAllNullFields() {
        // Bare entities — no interests, skills, or major on either side.
        // Must not throw NPE; returns false (no preference category fires).
        assertThat(MatchingService.matchesMentorPreferences(new Mentor(), new Mentee())).isFalse();
    }

    @Test
    void matchesMentorPreferences_isCaseInsensitive() {
        Mentor m = new Mentor();
        m.setInterests(List.of("AI"));
        Mentee me = new Mentee();
        me.setInterests(List.of("ai"));
        assertThat(MatchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    // ── Pure rank methods: precondition guards (#273) ─────────────────────
    // The package-private rankMentorsFor / findCandidateMenteesFor were
    // introduced so MatchNotificationProcessor can skip the redundant
    // findById + eligibility check that the public path already does. The
    // precondition guards turn caller-side bugs into loud IllegalStateExceptions
    // rather than letting the methods silently rank for an ineligible user.

    @Test
    void rankMentorsFor_throwsIllegalStateException_whenMenteeIsNull() {
        assertThatThrownBy(() -> matchingService.rankMentorsFor(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void rankMentorsFor_throwsIllegalStateException_whenMenteeHasActiveMentor() {
        Mentee ineligible = new Mentee();
        ineligible.setId(42L);
        ineligible.setActiveMentorId(99L);

        assertThatThrownBy(() -> matchingService.rankMentorsFor(ineligible, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active mentor");
    }

    @Test
    void rankMentorsFor_returnsRankedListForEligibleMentee() {
        // Sanity: with a valid mentee the method delegates to the same SQL +
        // ranker pipeline that getTopMentors uses.
        stubMentorSearch(List.of(mentor));

        List<MentorMatchResponse> result = matchingService.rankMentorsFor(mentee, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(mentor.getId());
    }

    @Test
    void findCandidateMenteesFor_throwsIllegalStateException_whenMentorIsNull() {
        assertThatThrownBy(() -> matchingService.findCandidateMenteesFor(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void findCandidateMenteesFor_throwsIllegalStateException_whenMentorAtFullCapacity() {
        Mentor ineligible = new Mentor();
        ineligible.setId(42L);
        ineligible.setMaxMenteeCapacity(2);
        ineligible.setCurrentMenteeCount(2);  // at cap

        assertThatThrownBy(() -> matchingService.findCandidateMenteesFor(ineligible, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("full capacity");
    }

    @Test
    void findCandidateMenteesFor_returnsCandidatesForEligibleMentor() {
        stubMenteeSearch(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.findCandidateMenteesFor(mentor, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(mentee.getId());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    // The matching path uses findRankingCandidates (List, no count) — see
    // MatchingService.rankMentorsForId / getCandidateMentees. Tests of the
    // SQL-side filter shape still use searchByFilters by name to capture the
    // semantic intent (verify(... searchByFilters(...))) — those verifications
    // were rewritten to target findRankingCandidates after the S1 refactor.

    private void stubMentorSearch(List<Mentor> mentors) {
        when(mentorRepository.findRankingCandidates(
                any(), any(), any(), any(), anyBoolean(), any(), any(Pageable.class)))
                .thenReturn(mentors);
    }

    private void stubMenteeSearch(List<Mentee> mentees) {
        when(menteeRepository.findRankingCandidates(
                any(), any(), any(), any(), anyBoolean(), any(), any(Pageable.class)))
                .thenReturn(mentees);
    }
}
