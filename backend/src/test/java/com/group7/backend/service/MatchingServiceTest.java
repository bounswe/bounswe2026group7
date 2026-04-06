package com.group7.backend.service;

import com.group7.backend.dto.response.MenteeCandidateResponse;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private MenteeRepository menteeRepository;

    @Mock
    private MentorRepository mentorRepository;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @InjectMocks
    private MatchingService matchingService;

    private Mentee mentee;
    private Mentor mentor;

    @BeforeEach
    void setUp() {
        mentee = new Mentee();
        mentee.setMajor("Computer Science");
        mentee.setGoals("career machine learning");
        mentee.setCareerInterest("backend engineering");
        mentee.setInterests(List.of("AI", "Databases"));
        mentee.setSkills(List.of("Java", "Python"));

        mentor = new Mentor();
        mentor.setMaxMenteeCapacity(3);
        mentor.setCurrentMenteeCount(1);
        mentor.setField("Computer Science");
        mentor.setExpertise("backend engineering Java");
        mentor.setPreferredMenteeMajor("Computer Science");
        mentor.setPreferredMenteeSkills(List.of("Java", "Kotlin"));
        mentor.setInterests(List.of("AI", "Systems"));
        mentor.setMentoringGoals("Help with career and machine learning projects");
    }

    // ── Score calculation ────────────────────────────────────────────────────

    @Test
    void scoreOverlappingInterests() {
        // Mentee has AI and Databases; mentor has AI and Systems → 1 overlap = +3
        int score = matchingService.calculateScore(mentor, mentee);
        assertThat(score).isGreaterThanOrEqualTo(3);
    }

    @Test
    void scoreSkillMatch() {
        // Java is in preferredMenteeSkills → +3
        Mentor m = new Mentor();
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        m.setPreferredMenteeSkills(List.of("Java"));

        Mentee me = new Mentee();
        me.setSkills(List.of("Java"));

        int score = matchingService.calculateScore(m, me);
        assertThat(score).isEqualTo(3);
    }

    @Test
    void scoreMajorMatchesPreferredMenteeMajor() {
        // Computer Science == Computer Science → +5
        Mentor m = new Mentor();
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        m.setPreferredMenteeMajor("Computer Science");

        Mentee me = new Mentee();
        me.setMajor("Computer Science");

        int score = matchingService.calculateScore(m, me);
        assertThat(score).isEqualTo(5);
    }

    @Test
    void scoreMajorMatchesField() {
        // Mentee major == mentor field → +3
        Mentor m = new Mentor();
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        m.setField("Computer Science");

        Mentee me = new Mentee();
        me.setMajor("Computer Science");

        int score = matchingService.calculateScore(m, me);
        assertThat(score).isEqualTo(3);
    }

    @Test
    void scoreGoalsKeywordOverlap() {
        // "machine" and "learning" are in mentor mentoringGoals → +2 each
        Mentor m = new Mentor();
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        m.setMentoringGoals("machine learning and career guidance");

        Mentee me = new Mentee();
        me.setGoals("machine learning");

        int score = matchingService.calculateScore(m, me);
        assertThat(score).isEqualTo(4); // "machine" +2, "learning" +2
    }

    @Test
    void scoreNullFieldsDoNotCrash() {
        Mentor m = new Mentor();
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        // all fields null

        Mentee me = new Mentee();
        // all fields null

        int score = matchingService.calculateScore(m, me);
        assertThat(score).isEqualTo(0);
    }

    // ── Capacity filter ──────────────────────────────────────────────────────

    @Test
    void fullCapacityMentorExcluded() {
        mentor.setCurrentMenteeCount(3); // full
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findAll()).thenReturn(List.of(mentor));

        List<MentorMatchResponse> result = matchingService.getTopMentors(1L, null);

        assertThat(result).isEmpty();
    }

    // ── Active mentor check ──────────────────────────────────────────────────

    @Test
    void activeMentorBlocksRequest() {
        mentee.setActiveMentorId(99L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));

        assertThatThrownBy(() -> matchingService.getTopMentors(1L, null))
                .isInstanceOf(com.group7.backend.exception.MatchingNotAllowedException.class)
                .hasMessageContaining("active mentor");
    }

    // ── Mentee not found ─────────────────────────────────────────────────────

    @Test
    void menteeNotFoundThrows() {
        when(menteeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.getTopMentors(99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Top 5 limit ──────────────────────────────────────────────────────────

    @Test
    void returnsAtMostFiveMentors() {
        List<Mentor> sixMentors = java.util.stream.IntStream.range(0, 6).mapToObj(i -> {
            Mentor m = new Mentor();
            m.setMaxMenteeCapacity(3);
            m.setCurrentMenteeCount(0);
            return m;
        }).toList();

        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findAll()).thenReturn(sixMentors);

        List<MentorMatchResponse> result = matchingService.getTopMentors(1L, null);

        assertThat(result).hasSize(5);
    }

    // ── Keyword filter ───────────────────────────────────────────────────────

    @Test
    void keywordFilterMatchesExpertise() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findAll()).thenReturn(List.of(mentor));

        List<MentorMatchResponse> result = matchingService.getTopMentors(1L, "Java");

        assertThat(result).hasSize(1);
        verify(notificationEventPublisher).publishMatchFound(1L, result.get(0).getFirstName());
    }

    @Test
    void keywordFilterNoMatchReturnsEmpty() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findAll()).thenReturn(List.of(mentor));

        List<MentorMatchResponse> result = matchingService.getTopMentors(1L, "rust");

        assertThat(result).isEmpty();
    }

    @Test
    void nullKeywordReturnsAll() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findAll()).thenReturn(List.of(mentor));

        List<MentorMatchResponse> result = matchingService.getTopMentors(1L, null);

        assertThat(result).hasSize(1);
    }

    // ── Ordering ─────────────────────────────────────────────────────────────

    @Test
    void resultsOrderedByScoreDescending() {
        Mentor lowScore = new Mentor();
        lowScore.setMaxMenteeCapacity(3);
        lowScore.setCurrentMenteeCount(0);
        // no matching fields → score 0

        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findAll()).thenReturn(List.of(lowScore, mentor));

        List<MentorMatchResponse> result = matchingService.getTopMentors(1L, null);

        assertThat(result.get(0).getMatchScore()).isGreaterThanOrEqualTo(result.get(1).getMatchScore());
    }

    // ── Candidate mentees: preference matching ──────────────────────────────

    @Test
    void candidateMenteesMatchesByInterest() {
        assertThat(matchingService.matchesMentorPreferences(mentor, mentee)).isTrue();
    }

    @Test
    void candidateMenteesMatchesBySkill() {
        Mentor m = new Mentor();
        m.setPreferredMenteeSkills(List.of("Python"));

        Mentee me = new Mentee();
        me.setSkills(List.of("Python"));

        assertThat(matchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    @Test
    void candidateMenteesMatchesByPreferredMajor() {
        Mentor m = new Mentor();
        m.setPreferredMenteeMajor("Computer Science");

        Mentee me = new Mentee();
        me.setMajor("Computer Science");

        assertThat(matchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    @Test
    void candidateMenteesMatchesByField() {
        Mentor m = new Mentor();
        m.setField("Computer Science");

        Mentee me = new Mentee();
        me.setMajor("Computer Science");

        assertThat(matchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    @Test
    void candidateMenteesNoOverlapReturnsfalse() {
        Mentor m = new Mentor();
        m.setField("Music");
        m.setPreferredMenteeMajor("Music");
        m.setPreferredMenteeSkills(List.of("Piano"));
        m.setInterests(List.of("Jazz"));

        Mentee me = new Mentee();
        me.setMajor("Computer Science");
        me.setSkills(List.of("Java"));
        me.setInterests(List.of("AI"));

        assertThat(matchingService.matchesMentorPreferences(m, me)).isFalse();
    }

    @Test
    void candidateMenteesNullFieldsDoNotCrash() {
        Mentor m = new Mentor();
        Mentee me = new Mentee();

        assertThat(matchingService.matchesMentorPreferences(m, me)).isFalse();
    }

    // ── Candidate mentees: active mentor exclusion ──────────────────────────

    @Test
    void candidateMenteesExcludesActivelyMentoredMentees() {
        Mentee activeMentee = new Mentee();
        activeMentee.setActiveMentorId(99L);
        activeMentee.setInterests(List.of("AI"));

        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee, activeMentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null);

        assertThat(result).hasSize(1);
    }

    // ── Candidate mentees: capacity check ───────────────────────────────────

    @Test
    void candidateMenteesFullCapacityBlocksRequest() {
        mentor.setCurrentMenteeCount(3); // full
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));

        assertThatThrownBy(() -> matchingService.getCandidateMentees(1L, null))
                .isInstanceOf(com.group7.backend.exception.MatchingNotAllowedException.class)
                .hasMessageContaining("capacity");
    }

    // ── Candidate mentees: mentor not found ─────────────────────────────────

    @Test
    void candidateMenteesMentorNotFoundThrows() {
        when(mentorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.getCandidateMentees(99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Candidate mentees: keyword filter ───────────────────────────────────

    @Test
    void candidateMenteesKeywordFilterMatchesGoals() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, "machine");

        assertThat(result).hasSize(1);
    }

    @Test
    void candidateMenteesKeywordFilterNoMatchReturnsEmpty() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, "rust");

        assertThat(result).isEmpty();
    }

    @Test
    void candidateMenteesNullKeywordReturnsAll() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null);

        assertThat(result).hasSize(1);
    }

    // ── Candidate mentees: case insensitivity ───────────────────────────────

    @Test
    void candidateMenteesMatchesByInterestCaseInsensitive() {
        Mentor m = new Mentor();
        m.setInterests(List.of("ai"));

        Mentee me = new Mentee();
        me.setInterests(List.of("AI"));

        assertThat(matchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    @Test
    void candidateMenteesMatchesBySkillCaseInsensitive() {
        Mentor m = new Mentor();
        m.setPreferredMenteeSkills(List.of("JAVA"));

        Mentee me = new Mentee();
        me.setSkills(List.of("java"));

        assertThat(matchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    @Test
    void candidateMenteesMatchesByMajorCaseInsensitive() {
        Mentor m = new Mentor();
        m.setPreferredMenteeMajor("computer science");

        Mentee me = new Mentee();
        me.setMajor("Computer Science");

        assertThat(matchingService.matchesMentorPreferences(m, me)).isTrue();
    }

    // ── Candidate mentees: keyword on different fields ──────────────────────

    @Test
    void candidateMenteesKeywordFilterMatchesMajor() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, "Computer");

        assertThat(result).hasSize(1);
    }

    @Test
    void candidateMenteesKeywordFilterMatchesSkill() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, "Java");

        assertThat(result).hasSize(1);
    }

    @Test
    void candidateMenteesKeywordFilterMatchesInterest() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, "AI");

        assertThat(result).hasSize(1);
    }

    @Test
    void candidateMenteesKeywordFilterMatchesCareerInterest() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, "backend");

        assertThat(result).hasSize(1);
    }

    @Test
    void candidateMenteesKeywordFilterIsCaseInsensitive() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, "MACHINE");

        assertThat(result).hasSize(1);
    }

    @Test
    void candidateMenteesEmptyKeywordReturnsAll() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, "");

        assertThat(result).hasSize(1);
    }

    // ── Candidate mentees: multiple mentees filtering ───────────────────────

    @Test
    void candidateMenteesFiltersOutNonMatchingMentees() {
        Mentee nonMatching = new Mentee();
        nonMatching.setMajor("Music");
        nonMatching.setSkills(List.of("Piano"));
        nonMatching.setInterests(List.of("Jazz"));

        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee, nonMatching));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMajor()).isEqualTo("Computer Science");
    }

    @Test
    void candidateMenteesReturnsMultipleMatchingMentees() {
        Mentee secondMatch = new Mentee();
        secondMatch.setInterests(List.of("AI"));
        secondMatch.setSkills(List.of("Kotlin"));

        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee, secondMatch));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void candidateMenteesExcludesAllActivelyMentoredMentees() {
        Mentee active1 = new Mentee();
        active1.setActiveMentorId(10L);
        active1.setInterests(List.of("AI"));

        Mentee active2 = new Mentee();
        active2.setActiveMentorId(20L);
        active2.setInterests(List.of("Systems"));

        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee, active1, active2));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null);

        assertThat(result).hasSize(1);
    }

    // ── Candidate mentees: capacity edge cases ──────────────────────────────

    @Test
    void candidateMenteesExactCapacityBlocksRequest() {
        mentor.setMaxMenteeCapacity(2);
        mentor.setCurrentMenteeCount(2);
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));

        assertThatThrownBy(() -> matchingService.getCandidateMentees(1L, null))
                .isInstanceOf(com.group7.backend.exception.MatchingNotAllowedException.class);
    }

    @Test
    void candidateMenteesOneSlotLeftAllowed() {
        mentor.setMaxMenteeCapacity(3);
        mentor.setCurrentMenteeCount(2);
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null);

        assertThat(result).hasSize(1);
    }

    // ── Candidate mentees: DTO mapping ──────────────────────────────────────

    @Test
    void candidateMenteesResponseContainsCorrectFields() {
        mentee.setFirstName("Elif");
        mentee.setBackgroundInfo("3rd year CS student");
        mentee.setMeetingFreqPref("Weekly");

        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(mentee));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, null);

        assertThat(result).hasSize(1);
        MenteeCandidateResponse dto = result.get(0);
        assertThat(dto.getFirstName()).isEqualTo("Elif");
        assertThat(dto.getGoals()).isEqualTo("career machine learning");
        assertThat(dto.getMajor()).isEqualTo("Computer Science");
        assertThat(dto.getInterests()).containsExactly("AI", "Databases");
        assertThat(dto.getSkills()).containsExactly("Java", "Python");
        assertThat(dto.getCareerInterest()).isEqualTo("backend engineering");
        assertThat(dto.getBackgroundInfo()).isEqualTo("3rd year CS student");
        assertThat(dto.getMeetingFreqPref()).isEqualTo("Weekly");
    }

    @Test
    void candidateMenteesKeywordAndPreferenceFilterCombined() {
        Mentee matchingWithKeyword = new Mentee();
        matchingWithKeyword.setInterests(List.of("AI"));
        matchingWithKeyword.setGoals("machine learning research");

        Mentee matchingWithoutKeyword = new Mentee();
        matchingWithoutKeyword.setInterests(List.of("AI"));
        matchingWithoutKeyword.setGoals("web development");

        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(menteeRepository.findAll()).thenReturn(List.of(matchingWithKeyword, matchingWithoutKeyword));

        List<MenteeCandidateResponse> result = matchingService.getCandidateMentees(1L, "machine");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGoals()).isEqualTo("machine learning research");
    }
}
