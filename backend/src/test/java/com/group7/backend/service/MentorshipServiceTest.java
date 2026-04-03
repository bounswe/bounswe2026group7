package com.group7.backend.service;

import com.group7.backend.dto.request.AcceptRequestRequest;
import com.group7.backend.dto.request.SharedGoalRequest;
import com.group7.backend.dto.response.MentorshipResponse;
import com.group7.backend.entity.*;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipServiceTest {

    @Mock
    private MentorshipRepository mentorshipRepository;

    @Mock
    private MentorshipRequestRepository mentorshipRequestRepository;

    @InjectMocks
    private MentorshipService mentorshipService;

    private Mentor mentor;
    private Mentee mentee;
    private MentorshipRequest request;
    private AcceptRequestRequest acceptDto;

    @BeforeEach
    void setUp() {
        mentor = new Mentor();
        mentor.setId(1L);
        mentor.setFirstName("Ahmet");
        mentor.setMaxMenteeCapacity(3);
        mentor.setCurrentMenteeCount(1);

        mentee = new Mentee();
        mentee.setId(2L);
        mentee.setFirstName("Elif");

        request = new MentorshipRequest();
        request.setId(10L);
        request.setMentor(mentor);
        request.setMentee(mentee);
        request.setStatus(MentorshipRequestStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());

        acceptDto = new AcceptRequestRequest();
        acceptDto.setDuration(3);
    }

    // ── Accept request ──────────────────────────────────────────────────────

    @Test
    void acceptRequestCreatesActiveMentorship() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> {
            Mentorship m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        MentorshipResponse response = mentorshipService.acceptRequest(1L, 10L, acceptDto);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getMentorId()).isEqualTo(1L);
        assertThat(response.getMenteeId()).isEqualTo(2L);
        assertThat(response.getDuration()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void acceptRequestSetsRequestStatusToAccepted() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> {
            Mentorship m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        mentorshipService.acceptRequest(1L, 10L, acceptDto);

        assertThat(request.getStatus()).isEqualTo(MentorshipRequestStatus.ACCEPTED);
    }

    @Test
    void acceptRequestSetsMenteeActiveMentorId() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> {
            Mentorship m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        mentorshipService.acceptRequest(1L, 10L, acceptDto);

        assertThat(mentee.getActiveMentorId()).isEqualTo(1L);
    }

    @Test
    void acceptRequestIncrementsMentorMenteeCount() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> {
            Mentorship m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        mentorshipService.acceptRequest(1L, 10L, acceptDto);

        assertThat(mentor.getCurrentMenteeCount()).isEqualTo(2);
    }

    @Test
    void acceptRequestCancelsOtherPendingRequests() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> {
            Mentorship m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        mentorshipService.acceptRequest(1L, 10L, acceptDto);

        verify(mentorshipRequestRepository).cancelOtherPendingRequests(2L, 10L);
    }

    @Test
    void acceptRequestNotFound() {
        when(mentorshipRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipService.acceptRequest(1L, 99L, acceptDto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void acceptRequestWrongMentor() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> mentorshipService.acceptRequest(999L, 10L, acceptDto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void acceptRequestNotPending() {
        request.setStatus(MentorshipRequestStatus.REJECTED);
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> mentorshipService.acceptRequest(1L, 10L, acceptDto))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("no longer pending");
    }

    @Test
    void acceptRequestMenteeHasActiveMentor() {
        mentee.setActiveMentorId(50L);
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> mentorshipService.acceptRequest(1L, 10L, acceptDto))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("active mentor");
    }

    @Test
    void acceptRequestMentorAtCapacity() {
        mentor.setCurrentMenteeCount(3);
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> mentorshipService.acceptRequest(1L, 10L, acceptDto))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void acceptRequestInvalidDuration() {
        acceptDto.setDuration(2);
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> mentorshipService.acceptRequest(1L, 10L, acceptDto))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("1, 3, or 6");
    }

    // ── Reject request ──────────────────────────────────────────────────────

    @Test
    void rejectRequestSuccess() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        mentorshipService.rejectRequest(1L, 10L);

        assertThat(request.getStatus()).isEqualTo(MentorshipRequestStatus.REJECTED);
    }

    @Test
    void rejectRequestNotFound() {
        when(mentorshipRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipService.rejectRequest(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectRequestNotPending() {
        request.setStatus(MentorshipRequestStatus.ACCEPTED);
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> mentorshipService.rejectRequest(1L, 10L))
                .isInstanceOf(MentorshipRequestException.class);
    }

    // ── List active mentorships ─────────────────────────────────────────────

    @Test
    void getActiveMentorshipsSuccess() {
        Mentorship mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setStartDate(LocalDateTime.now());
        mentorship.setEndDate(LocalDateTime.now().plusMonths(3));
        mentorship.setDuration(3);

        when(mentorshipRepository.findByUserIdAndStatus(1L, MentorshipStatus.ACTIVE))
                .thenReturn(List.of(mentorship));

        List<MentorshipResponse> result = mentorshipService.getActiveMentorships(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMentorFirstName()).isEqualTo("Ahmet");
    }

    // ── Shared goal ─────────────────────────────────────────────────────────

    @Test
    void setSharedGoalSuccess() {
        Mentorship mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setStartDate(LocalDateTime.now());
        mentorship.setEndDate(LocalDateTime.now().plusMonths(3));
        mentorship.setDuration(3);

        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(mentorshipRepository.save(any())).thenReturn(mentorship);

        SharedGoalRequest goalDto = new SharedGoalRequest();
        goalDto.setSharedGoal("Build a portfolio project");

        MentorshipResponse response = mentorshipService.setSharedGoal(1L, 100L, goalDto);

        assertThat(response.getSharedGoal()).isEqualTo("Build a portfolio project");
    }

    @Test
    void setSharedGoalWrongUser() {
        Mentorship mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);

        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));

        SharedGoalRequest goalDto = new SharedGoalRequest();
        goalDto.setSharedGoal("test");

        assertThatThrownBy(() -> mentorshipService.setSharedGoal(999L, 100L, goalDto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setSharedGoalNotActive() {
        Mentorship mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.COMPLETED);

        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));

        SharedGoalRequest goalDto = new SharedGoalRequest();
        goalDto.setSharedGoal("test");

        assertThatThrownBy(() -> mentorshipService.setSharedGoal(1L, 100L, goalDto))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("not active");
    }
}
