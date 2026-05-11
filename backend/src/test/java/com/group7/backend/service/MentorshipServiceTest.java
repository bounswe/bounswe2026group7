package com.group7.backend.service;

import com.group7.backend.dto.request.AcceptRequestRequest;
import com.group7.backend.dto.request.CancelMentorshipRequest;
import com.group7.backend.dto.request.EndMentorshipRequest;
import com.group7.backend.dto.request.ExtendMentorshipRequest;
import com.group7.backend.dto.request.SharedGoalRequest;
import com.group7.backend.dto.response.MentorshipAuditLogResponse;
import com.group7.backend.dto.response.MentorshipResponse;
import com.group7.backend.entity.*;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorshipAuditLogRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipServiceTest {

    @Mock
    private MentorshipRepository mentorshipRepository;

    @Mock
    private MentorshipRequestRepository mentorshipRequestRepository;

    @Mock
    private MentorshipAuditLogRepository mentorshipAuditLogRepository;

    @Mock
    private MentorshipCleanupService mentorshipCleanupService;

    @Mock
    private MentorshipCooldownPolicy mentorshipCooldownPolicy;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @Mock
    private BanService banService;

    @Spy
    private Clock clock = Clock.systemUTC();

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
        request.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

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
        verify(notificationEventPublisher).publishRequestAccepted(2L, "Ahmet");
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
        verify(notificationEventPublisher).publishRequestRejected(2L, "Ahmet");
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
        mentorship.setStartDate(OffsetDateTime.now(ZoneOffset.UTC));
        mentorship.setEndDate(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(3));
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
        mentorship.setStartDate(OffsetDateTime.now(ZoneOffset.UTC));
        mentorship.setEndDate(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(3));
        mentorship.setDuration(3);

        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));
        when(mentorshipRepository.save(any())).thenReturn(mentorship);

        SharedGoalRequest goalDto = new SharedGoalRequest();
        goalDto.setSharedGoal("Build a portfolio project");

        MentorshipResponse response = mentorshipService.setSharedGoal(1L, 100L, goalDto);

        assertThat(response.getSharedGoal()).isEqualTo("Build a portfolio project");
        assertThat(response.isGoalDefined()).isTrue();
    }

    @Test
    void setSharedGoalSuccessAsMentee() {
        Mentorship mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);

        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));
        when(mentorshipRepository.save(any())).thenReturn(mentorship);

        SharedGoalRequest goalDto = new SharedGoalRequest();
        goalDto.setSharedGoal("Mentee-led goal");

        MentorshipResponse response = mentorshipService.setSharedGoal(2L, 100L, goalDto);

        assertThat(response.getSharedGoal()).isEqualTo("Mentee-led goal");
        assertThat(response.isGoalDefined()).isTrue();
    }

    @Test
    void setSharedGoalWrongUser() {
        when(mentorshipRepository.findByIdAndParticipant(100L, 999L)).thenReturn(Optional.empty());

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

        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));

        SharedGoalRequest goalDto = new SharedGoalRequest();
        goalDto.setSharedGoal("test");

        assertThatThrownBy(() -> mentorshipService.setSharedGoal(1L, 100L, goalDto))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("not active");
    }

    // ── Get mentorship ──────────────────────────────────────────────────────

    @Test
    void getMentorshipReturnsResponseForMentor() {
        Mentorship mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setStartDate(OffsetDateTime.now(ZoneOffset.UTC));
        mentorship.setEndDate(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(3));
        mentorship.setDuration(3);
        mentorship.setSharedGoal("Build a portfolio");

        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));

        MentorshipResponse response = mentorshipService.getMentorship(1L, 100L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getMentorFirstName()).isEqualTo("Ahmet");
        assertThat(response.isGoalDefined()).isTrue();
    }

    @Test
    void getMentorshipReturnsResponseForMentee() {
        Mentorship mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setStartDate(OffsetDateTime.now(ZoneOffset.UTC));
        mentorship.setEndDate(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(3));
        mentorship.setDuration(3);

        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));

        MentorshipResponse response = mentorshipService.getMentorship(2L, 100L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.isGoalDefined()).isFalse();
    }

    @Test
    void getMentorshipReturns404ForNonParticipant() {
        when(mentorshipRepository.findByIdAndParticipant(100L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipService.getMentorship(999L, 100L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMentorshipReturns404ForUnknownId() {
        when(mentorshipRepository.findByIdAndParticipant(404L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipService.getMentorship(1L, 404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMentorshipExposesGoalDefinedFalseForBlankGoal() {
        Mentorship mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setSharedGoal("   ");

        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));

        MentorshipResponse response = mentorshipService.getMentorship(1L, 100L);

        assertThat(response.isGoalDefined()).isFalse();
        assertThat(response.getSharedGoal()).isEqualTo("   ");
    }

    // ── Cancel mentorship (#133) ────────────────────────────────────────────

    private Mentorship activeMentorship() {
        Mentorship m = new Mentorship();
        m.setId(100L);
        m.setMentor(mentor);
        m.setMentee(mentee);
        m.setStatus(MentorshipStatus.ACTIVE);
        m.setStartDate(OffsetDateTime.now(ZoneOffset.UTC));
        m.setEndDate(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(3));
        m.setDuration(3);
        return m;
    }

    private CancelMentorshipRequest cancelDto(String reason) {
        CancelMentorshipRequest dto = new CancelMentorshipRequest();
        dto.setReason(reason);
        return dto;
    }

    @Test
    void cancelMentorshipByMenteeMarksCancelledAndCleansUp() {
        Mentorship mentorship = activeMentorship();
        mentee.setActiveMentorId(mentor.getId());
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> inv.getArgument(0));

        MentorshipResponse response = mentorshipService.cancelMentorship(2L, 100L, cancelDto("Schedule clash"));

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getCancellationReason()).isEqualTo("Schedule clash");
        assertThat(response.getTerminatedAt()).isNotNull();
        assertThat(mentorship.getTerminatedByUserId()).isEqualTo(2L);
        assertThat(mentee.getActiveMentorId()).isNull();
        assertThat(mentor.getCurrentMenteeCount()).isEqualTo(0);
        verify(mentorshipCleanupService).cleanupChildren(100L);
        verify(notificationEventPublisher).publishMentorshipCancelled(1L, "Elif", "Schedule clash");
        verify(banService).recordCancellation(2L, "Cancelled active mentorship: Schedule clash");

        ArgumentCaptor<MentorshipAuditLog> captor = ArgumentCaptor.forClass(MentorshipAuditLog.class);
        verify(mentorshipAuditLogRepository).save(captor.capture());
        MentorshipAuditLog logged = captor.getValue();
        assertThat(logged.getMentorshipId()).isEqualTo(100L);
        assertThat(logged.getFromStatus()).isEqualTo(MentorshipStatus.ACTIVE);
        assertThat(logged.getToStatus()).isEqualTo(MentorshipStatus.CANCELLED);
        assertThat(logged.getActorUserId()).isEqualTo(2L);
        assertThat(logged.getReason()).isEqualTo("Schedule clash");
    }

    @Test
    void cancelMentorshipRejectsMentorActor() {
        // Mentor must use /end instead; /cancel is mentee-only after #237.
        Mentorship mentorship = activeMentorship();
        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> mentorshipService.cancelMentorship(1L, 100L, cancelDto("nope")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("/end");
        verify(mentorshipCleanupService, never()).cleanupChildren(any());
        verify(banService, never()).recordCancellation(any(), any());
    }

    @Test
    void cancelMentorshipRejectsNonParticipant() {
        when(mentorshipRepository.findByIdAndParticipant(100L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipService.cancelMentorship(999L, 100L, cancelDto("nope")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(mentorshipCleanupService, never()).cleanupChildren(any());
    }

    @Test
    void cancelMentorshipRejectsAlreadyCancelled() {
        Mentorship mentorship = activeMentorship();
        mentorship.setStatus(MentorshipStatus.CANCELLED);
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> mentorshipService.cancelMentorship(2L, 100L, cancelDto("again")))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("not active");
        verify(mentorshipCleanupService, never()).cleanupChildren(any());
        verify(mentorshipAuditLogRepository, never()).save(any());
        verify(banService, never()).recordCancellation(any(), any());
    }

    @Test
    void cancelMentorshipPreservesActiveMentorIdWhenItPointsElsewhere() {
        // A stale activeMentorId pointing at a different mentor should not be cleared.
        Mentorship mentorship = activeMentorship();
        mentee.setActiveMentorId(999L);
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> inv.getArgument(0));

        mentorshipService.cancelMentorship(2L, 100L, cancelDto("done"));

        assertThat(mentee.getActiveMentorId()).isEqualTo(999L);
    }

    // ── End mentorship (#237) ───────────────────────────────────────────────

    private EndMentorshipRequest endDto(String reason) {
        EndMentorshipRequest dto = new EndMentorshipRequest();
        dto.setReason(reason);
        return dto;
    }

    @Test
    void endMentorshipByMentorSetsCompletedAndCleansUp() {
        Mentorship mentorship = activeMentorship();
        mentee.setActiveMentorId(mentor.getId());
        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> inv.getArgument(0));

        MentorshipResponse response = mentorshipService.endMentorship(1L, 100L, endDto("Goal achieved"));

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(mentorship.getTerminatedByUserId()).isEqualTo(1L);
        assertThat(mentorship.getTerminatedAt()).isNotNull();
        assertThat(mentorship.getEndDate()).isEqualTo(mentorship.getTerminatedAt());
        assertThat(mentee.getActiveMentorId()).isNull();
        assertThat(mentor.getCurrentMenteeCount()).isEqualTo(0);
        verify(mentorshipCleanupService).cleanupChildren(100L);
        verify(notificationEventPublisher).publishMentorshipEnded(2L, "Ahmet", "Goal achieved");
        verify(banService, never()).recordCancellation(any(), any());

        ArgumentCaptor<MentorshipAuditLog> captor = ArgumentCaptor.forClass(MentorshipAuditLog.class);
        verify(mentorshipAuditLogRepository).save(captor.capture());
        MentorshipAuditLog logged = captor.getValue();
        assertThat(logged.getFromStatus()).isEqualTo(MentorshipStatus.ACTIVE);
        assertThat(logged.getToStatus()).isEqualTo(MentorshipStatus.COMPLETED);
        assertThat(logged.getActorUserId()).isEqualTo(1L);
    }

    @Test
    void endMentorshipRejectsMenteeActor() {
        Mentorship mentorship = activeMentorship();
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> mentorshipService.endMentorship(2L, 100L, endDto(null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("/cancel");
        verify(mentorshipCleanupService, never()).cleanupChildren(any());
    }

    @Test
    void endMentorshipRejectsAlreadyTerminated() {
        Mentorship mentorship = activeMentorship();
        mentorship.setStatus(MentorshipStatus.COMPLETED);
        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> mentorshipService.endMentorship(1L, 100L, endDto(null)))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("not active");
    }

    // ── Extend mentorship (#237) ────────────────────────────────────────────

    private ExtendMentorshipRequest extendDto(int additionalMonths) {
        ExtendMentorshipRequest dto = new ExtendMentorshipRequest();
        dto.setAdditionalMonths(additionalMonths);
        return dto;
    }

    @Test
    void extendMentorshipPushesEndDateAndAudits() {
        Mentorship mentorship = activeMentorship();
        OffsetDateTime originalEnd = mentorship.getEndDate();
        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> inv.getArgument(0));

        MentorshipResponse response = mentorshipService.extendMentorship(1L, 100L, extendDto(3));

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(mentorship.getEndDate()).isEqualTo(originalEnd.plusMonths(3));
        assertThat(mentorship.getDuration()).isEqualTo(6);
        verify(notificationEventPublisher).publishMentorshipExtended(2L, "Ahmet", 3, mentorship.getEndDate());

        ArgumentCaptor<MentorshipAuditLog> captor = ArgumentCaptor.forClass(MentorshipAuditLog.class);
        verify(mentorshipAuditLogRepository).save(captor.capture());
        MentorshipAuditLog logged = captor.getValue();
        assertThat(logged.getFromStatus()).isEqualTo(MentorshipStatus.ACTIVE);
        assertThat(logged.getToStatus()).isEqualTo(MentorshipStatus.ACTIVE);
        assertThat(logged.getReason()).contains("Extended by 3");
    }

    @Test
    void extendMentorshipRejectsMenteeActor() {
        Mentorship mentorship = activeMentorship();
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> mentorshipService.extendMentorship(2L, 100L, extendDto(3)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void extendMentorshipRejectsInvalidMonths() {
        Mentorship mentorship = activeMentorship();
        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> mentorshipService.extendMentorship(1L, 100L, extendDto(2)))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("1, 3, or 6");
    }

    @Test
    void extendMentorshipRejectsNotActive() {
        Mentorship mentorship = activeMentorship();
        mentorship.setStatus(MentorshipStatus.COMPLETED);
        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> mentorshipService.extendMentorship(1L, 100L, extendDto(3)))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("not active");
    }

    // ── Accept request: cool-down + initial audit row (#133) ────────────────

    @Test
    void acceptRequestWritesInitialAuditRow() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(mentorshipRepository.save(any(Mentorship.class))).thenAnswer(inv -> {
            Mentorship m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        mentorshipService.acceptRequest(1L, 10L, acceptDto);

        ArgumentCaptor<MentorshipAuditLog> captor = ArgumentCaptor.forClass(MentorshipAuditLog.class);
        verify(mentorshipAuditLogRepository).save(captor.capture());
        MentorshipAuditLog logged = captor.getValue();
        assertThat(logged.getMentorshipId()).isEqualTo(100L);
        assertThat(logged.getFromStatus()).isNull();
        assertThat(logged.getToStatus()).isEqualTo(MentorshipStatus.ACTIVE);
        assertThat(logged.getActorUserId()).isEqualTo(1L);
        assertThat(logged.getReason()).isNull();
    }

    @Test
    void acceptRequestBlockedDuringCooldown() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        org.mockito.Mockito.doThrow(new MentorshipRequestException("cool-down active"))
                .when(mentorshipCooldownPolicy).assertNotInCooldown(1L, 2L);

        assertThatThrownBy(() -> mentorshipService.acceptRequest(1L, 10L, acceptDto))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("cool-down");
        verify(mentorshipRepository, never()).save(any());
        verify(mentorshipAuditLogRepository, never()).save(any());
    }

    // ── Audit trail endpoint (#133) ─────────────────────────────────────────

    @Test
    void getAuditTrailReturnsParticipantView() {
        Mentorship mentorship = activeMentorship();
        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));

        MentorshipAuditLog row = MentorshipAuditLog.of(100L, null, MentorshipStatus.ACTIVE, 1L, null);
        row.setId(1L);
        row.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(mentorshipAuditLogRepository.findByMentorshipIdOrderByCreatedAtAsc(100L))
                .thenReturn(List.of(row));

        List<MentorshipAuditLogResponse> trail = mentorshipService.getAuditTrail(1L, 100L);

        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getToStatus()).isEqualTo("ACTIVE");
        assertThat(trail.get(0).getFromStatus()).isNull();
    }

    @Test
    void getAuditTrailRejectsNonParticipant() {
        when(mentorshipRepository.findByIdAndParticipant(100L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipService.getAuditTrail(999L, 100L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
