package com.group7.backend.service;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private MenteeRepository menteeRepository;

    @Mock
    private MentorRepository mentorRepository;

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
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
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
}
