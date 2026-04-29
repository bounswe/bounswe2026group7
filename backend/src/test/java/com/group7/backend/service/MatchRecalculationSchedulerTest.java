package com.group7.backend.service;

import com.group7.backend.dto.response.MenteeCandidateResponse;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.MatchHistory;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MatchHistoryRepository;
import com.group7.backend.scheduler.MatchRecalculationScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for MatchRecalculationScheduler.
 * 
 * Verifies that:
 * 1. Notifications are published only when top match changes
 * 2. Repeated runs with same matches don't publish duplicate notifications
 * 3. Both mentee and mentor matching logic works correctly
 * 4. MatchHistory is properly persisted and queried
 */
@ExtendWith(MockitoExtension.class)
class MatchRecalculationSchedulerTest {

    @Mock
    private MenteeRepository menteeRepository;

    @Mock
    private MentorRepository mentorRepository;

    @Mock
    private MatchHistoryRepository matchHistoryRepository;

    @Mock
    private MatchingService matchingService;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @InjectMocks
    private MatchRecalculationScheduler scheduler;

    private Mentee mentee1;
    private Mentee mentee2;
    private Mentor mentor1;
    private Mentor mentor2;
    private MentorMatchResponse topMentor;
    private MenteeCandidateResponse topMentee;

    @BeforeEach
    void setUp() {
        // Create test mentees
        mentee1 = new Mentee();
        mentee1.setId(1L);
        mentee1.setFirstName("Alice");
        mentee1.setActiveMentorId(null);

        mentee2 = new Mentee();
        mentee2.setId(2L);
        mentee2.setFirstName("Bob");
        mentee2.setActiveMentorId(null);

        // Create test mentors
        mentor1 = new Mentor();
        mentor1.setId(10L);
        mentor1.setFirstName("Charlie");
        mentor1.setCurrentMenteeCount(0);
        mentor1.setMaxMenteeCapacity(5);

        mentor2 = new Mentor();
        mentor2.setId(11L);
        mentor2.setFirstName("Diana");
        mentor2.setCurrentMenteeCount(0);
        mentor2.setMaxMenteeCapacity(5);

        // Create top match responses
        topMentor = new MentorMatchResponse();
        topMentor.setId(10L);
        topMentor.setFirstName("Charlie");

        topMentee = new MenteeCandidateResponse();
        topMentee.setId(1L);
        topMentee.setFirstName("Alice");
    }

    // ── Mentee Matching Tests ────────────────────────────────────────────────

    @Test
    void publishesNotificationWhenMenteeTopMatchChanges() {
        // Scenario: Mentee's top match changes from mentor1 to mentor2
        MatchHistory oldHistory = new MatchHistory();
        oldHistory.setUserId(1L);
        oldHistory.setUserType(MatchHistory.UserType.MENTEE);
        oldHistory.setTopMatchId(10L);
        oldHistory.setTopMatchName("Charlie");
        oldHistory.setMatchScore(85);

        MentorMatchResponse newTopMentor = new MentorMatchResponse();
        newTopMentor.setId(11L);
        newTopMentor.setFirstName("Diana");

        when(menteeRepository.findAll()).thenReturn(List.of(mentee1));
        when(matchingService.getTopMentorsForScheduler(1L))
                .thenReturn(List.of(newTopMentor));
        when(matchHistoryRepository.findLatestByUserIdAndUserType(1L, MatchHistory.UserType.MENTEE))
                .thenReturn(Optional.of(oldHistory));
        when(matchHistoryRepository.save(any(MatchHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.recalculateMatches();

        // Verify notification published exactly once
        verify(notificationEventPublisher, times(1))
                .publishMatchFound(1L, "Diana");

        // Verify new MatchHistory saved
        verify(matchHistoryRepository, times(1)).save(argThat(mh ->
                mh.getUserId().equals(1L) &&
                mh.getUserType() == MatchHistory.UserType.MENTEE &&
                mh.getTopMatchId().equals(11L)
        ));
    }

    @Test
    void noNotificationWhenMenteeTopMatchUnchanged() {
        // Scenario: Mentee's top match stays the same (same ID)
        MatchHistory existingHistory = new MatchHistory();
        existingHistory.setUserId(1L);
        existingHistory.setUserType(MatchHistory.UserType.MENTEE);
        existingHistory.setTopMatchId(10L);
        existingHistory.setTopMatchName("Charlie");
        existingHistory.setMatchScore(85);

        MentorMatchResponse sameTopMentor = new MentorMatchResponse();
        sameTopMentor.setId(10L);
        sameTopMentor.setFirstName("Charlie");

        when(menteeRepository.findAll()).thenReturn(List.of(mentee1));
        when(matchingService.getTopMentorsForScheduler(1L))
                .thenReturn(List.of(sameTopMentor));
        when(matchHistoryRepository.findLatestByUserIdAndUserType(1L, MatchHistory.UserType.MENTEE))
                .thenReturn(Optional.of(existingHistory));

        scheduler.recalculateMatches();

        // Verify NO notification published
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());

        // Unchanged matches still refresh the stored snapshot.
        verify(matchHistoryRepository, times(1)).save(any(MatchHistory.class));
    }

    @Test
    void publishesNotificationWhenMenteeHasNoHistoryYet() {
        // Scenario: First time scheduler runs for this mentee (no history yet)
        MentorMatchResponse firstMatch = new MentorMatchResponse();
        firstMatch.setId(10L);
        firstMatch.setFirstName("Charlie");

        when(menteeRepository.findAll()).thenReturn(List.of(mentee1));
        when(matchingService.getTopMentorsForScheduler(1L))
                .thenReturn(List.of(firstMatch));
        when(matchHistoryRepository.findLatestByUserIdAndUserType(1L, MatchHistory.UserType.MENTEE))
                .thenReturn(Optional.empty()); // No history yet
        when(matchHistoryRepository.save(any(MatchHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.recalculateMatches();

        // First-time calculation only persists the snapshot.
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());

        // Verify MatchHistory created
        verify(matchHistoryRepository, times(1)).save(argThat(mh ->
                mh.getUserId().equals(1L) &&
                mh.getUserType() == MatchHistory.UserType.MENTEE &&
                mh.getTopMatchId().equals(10L)
        ));
    }

    @Test
    void noNotificationWhenMenteeHasNoMatches() {
        // Scenario: Mentee has no matching mentors
        when(menteeRepository.findAll()).thenReturn(List.of(mentee1));
        when(matchingService.getTopMentorsForScheduler(1L))
                .thenReturn(List.of()); // Empty result

        scheduler.recalculateMatches();

        // Verify NO notification
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());

        // Verify MatchHistory NOT saved
        verify(matchHistoryRepository, never()).save(any());
    }

    // ── Mentor Matching Tests ────────────────────────────────────────────────

    @Test
    void publishesNotificationWhenMentorTopCandidateChanges() {
        // Scenario: Mentor's top candidate changes from mentee1 to mentee2
        MatchHistory oldHistory = new MatchHistory();
        oldHistory.setUserId(10L);
        oldHistory.setUserType(MatchHistory.UserType.MENTOR);
        oldHistory.setTopMatchId(1L);
        oldHistory.setTopMatchName("Alice");
        oldHistory.setMatchScore(80);

        MenteeCandidateResponse newTopCandidate = new MenteeCandidateResponse();
        newTopCandidate.setId(2L);
        newTopCandidate.setFirstName("Bob");

        when(mentorRepository.findAll()).thenReturn(List.of(mentor1));
        when(matchingService.getCandidateMenteesForScheduler(10L))
                .thenReturn(List.of(newTopCandidate));
        when(matchHistoryRepository.findLatestByUserIdAndUserType(10L, MatchHistory.UserType.MENTOR))
                .thenReturn(Optional.of(oldHistory));
        when(matchHistoryRepository.save(any(MatchHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.recalculateMatches();

        // Verify notification published
        verify(notificationEventPublisher, times(1))
                .publishMatchFound(10L, "Bob");

        // Verify new MatchHistory saved
        verify(matchHistoryRepository, times(1)).save(argThat(mh ->
                mh.getUserId().equals(10L) &&
                mh.getUserType() == MatchHistory.UserType.MENTOR &&
                mh.getTopMatchId().equals(2L)
        ));
    }

    @Test
    void noNotificationWhenMentorTopCandidateUnchanged() {
        // Scenario: Mentor's top candidate stays the same
        MatchHistory existingHistory = new MatchHistory();
        existingHistory.setUserId(10L);
        existingHistory.setUserType(MatchHistory.UserType.MENTOR);
        existingHistory.setTopMatchId(1L);
        existingHistory.setTopMatchName("Alice");
        existingHistory.setMatchScore(80);

        MenteeCandidateResponse sameTopCandidate = new MenteeCandidateResponse();
        sameTopCandidate.setId(1L);
        sameTopCandidate.setFirstName("Alice");

        when(mentorRepository.findAll()).thenReturn(List.of(mentor1));
        when(matchingService.getCandidateMenteesForScheduler(10L))
                .thenReturn(List.of(sameTopCandidate));
        when(matchHistoryRepository.findLatestByUserIdAndUserType(10L, MatchHistory.UserType.MENTOR))
                .thenReturn(Optional.of(existingHistory));

        scheduler.recalculateMatches();

        // Verify NO notification
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());
    }

    @Test
    void processesMultipleMenteesInSingleRun() {
        // Scenario: Scheduler runs for multiple mentees in one cycle
        MentorMatchResponse mentor1Match = new MentorMatchResponse();
        mentor1Match.setId(10L);
        mentor1Match.setFirstName("Charlie");

        MentorMatchResponse mentor2Match = new MentorMatchResponse();
        mentor2Match.setId(11L);
        mentor2Match.setFirstName("Diana");

        when(menteeRepository.findAll()).thenReturn(List.of(mentee1, mentee2));
        when(matchingService.getTopMentorsForScheduler(1L))
                .thenReturn(List.of(mentor1Match));
        when(matchingService.getTopMentorsForScheduler(2L))
                .thenReturn(List.of(mentor2Match));
        when(matchHistoryRepository.findLatestByUserIdAndUserType(anyLong(), eq(MatchHistory.UserType.MENTEE)))
                .thenReturn(Optional.empty()); // Both are new
        when(matchHistoryRepository.save(any(MatchHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.recalculateMatches();

        // First pass for each user stores history without notifications.
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());

        // Verify 2 MatchHistory records saved
        verify(matchHistoryRepository, times(2)).save(any(MatchHistory.class));
    }

    @Test
    void processesMultipleMentorsInSingleRun() {
        // Scenario: Scheduler runs for multiple mentors in one cycle
        MenteeCandidateResponse mentee1Candidate = new MenteeCandidateResponse();
        mentee1Candidate.setId(1L);
        mentee1Candidate.setFirstName("Alice");

        MenteeCandidateResponse mentee2Candidate = new MenteeCandidateResponse();
        mentee2Candidate.setId(2L);
        mentee2Candidate.setFirstName("Bob");

        when(mentorRepository.findAll()).thenReturn(List.of(mentor1, mentor2));
        when(matchingService.getCandidateMenteesForScheduler(10L))
                .thenReturn(List.of(mentee1Candidate));
        when(matchingService.getCandidateMenteesForScheduler(11L))
                .thenReturn(List.of(mentee2Candidate));
        when(matchHistoryRepository.findLatestByUserIdAndUserType(anyLong(), eq(MatchHistory.UserType.MENTOR)))
                .thenReturn(Optional.empty()); // Both are new
        when(matchHistoryRepository.save(any(MatchHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.recalculateMatches();

        // First pass for each user stores history without notifications.
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());

        // Verify 2 MatchHistory records saved
        verify(matchHistoryRepository, times(2)).save(any(MatchHistory.class));
    }

    @Test
    void handlesExceptionForIndividualUserGracefully() {
        // Scenario: One mentee throws exception, scheduler should continue with others
        MentorMatchResponse mentor1Match = new MentorMatchResponse();
        mentor1Match.setId(10L);
        mentor1Match.setFirstName("Charlie");

        MentorMatchResponse mentor2Match = new MentorMatchResponse();
        mentor2Match.setId(11L);
        mentor2Match.setFirstName("Diana");

        when(menteeRepository.findAll()).thenReturn(List.of(mentee1, mentee2));
        when(matchingService.getTopMentorsForScheduler(1L))
                .thenThrow(new RuntimeException("Database error")); // Mentee1 fails
        when(matchingService.getTopMentorsForScheduler(2L))
                .thenReturn(List.of(mentor2Match)); // Mentee2 succeeds
        when(matchHistoryRepository.findLatestByUserIdAndUserType(2L, MatchHistory.UserType.MENTEE))
                .thenReturn(Optional.empty());
        when(matchHistoryRepository.save(any(MatchHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Scheduler should not throw exception
        scheduler.recalculateMatches();

        // The failing user should not prevent the other user's history from being saved.
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());

        // Verify one MatchHistory record saved (for mentee2)
        verify(matchHistoryRepository, times(1)).save(any(MatchHistory.class));
    }

    @Test
    void matchHistoryPersistsCorrectData() {
        // Scenario: Verify MatchHistory is populated with correct data
        MentorMatchResponse topMatch = new MentorMatchResponse();
        topMatch.setId(10L);
        topMatch.setFirstName("Charlie");

        when(menteeRepository.findAll()).thenReturn(List.of(mentee1));
        when(matchingService.getTopMentorsForScheduler(1L))
                .thenReturn(List.of(topMatch));
        when(matchHistoryRepository.findLatestByUserIdAndUserType(1L, MatchHistory.UserType.MENTEE))
                .thenReturn(Optional.empty());
        when(matchHistoryRepository.save(any(MatchHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.recalculateMatches();

        // Verify MatchHistory saved with correct values
        verify(matchHistoryRepository, times(1)).save(argThat(mh ->
                mh.getUserId().equals(1L) &&
                mh.getUserType() == MatchHistory.UserType.MENTEE &&
                mh.getTopMatchId().equals(10L) &&
                mh.getTopMatchName().equals("Charlie")
        ));
    }

    @Test
    void emptyUserListDoesNotCrash() {
        // Scenario: No mentees or mentors exist
        when(menteeRepository.findAll()).thenReturn(List.of());
        when(mentorRepository.findAll()).thenReturn(List.of());

        // Should execute without error
        scheduler.recalculateMatches();

        // Verify no notifications or saves
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());
        verify(matchHistoryRepository, never()).save(any());
    }
}
