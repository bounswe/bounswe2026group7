package com.group7.backend.service;

import com.group7.backend.dto.request.MentorshipRequestCreateRequest;
import com.group7.backend.dto.response.MentorshipRequestResponse;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.MentorshipRequest;
import com.group7.backend.entity.MentorshipRequestStatus;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.exception.UserBannedException;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipRequestServiceTest {

    @Mock
    private MentorshipRequestRepository mentorshipRequestRepository;

    @Mock
    private MenteeRepository menteeRepository;

    @Mock
    private MentorRepository mentorRepository;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @Mock
    private BanService banService;

    @Mock
    private MentorshipCooldownPolicy mentorshipCooldownPolicy;

    @InjectMocks
    private MentorshipRequestService mentorshipRequestService;

    private Mentee mentee;
    private Mentor mentor;
    private MentorshipRequestCreateRequest createRequest;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        mentee = new Mentee();
        mentee.setId(1L);
        mentee.setFirstName("Elif");

        mentor = new Mentor();
        mentor.setId(2L);
        mentor.setFirstName("Ahmet");
        mentor.setMaxMenteeCapacity(3);
        mentor.setCurrentMenteeCount(1);

        createRequest = new MentorshipRequestCreateRequest();
        createRequest.setMentorId(2L);
        createRequest.setMessage("I'd love to learn from you!");

        pageable = PageRequest.of(0, 20);
    }

    // ── Create request: happy path ──────────────────────────────────────────

    @Test
    void createRequestSuccess() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findById(2L)).thenReturn(Optional.of(mentor));
        when(mentorshipRequestRepository.existsByMentee_IdAndMentor_IdAndStatus(
                1L, 2L, MentorshipRequestStatus.PENDING)).thenReturn(false);
        when(mentorshipRequestRepository.save(any(MentorshipRequest.class))).thenAnswer(invocation -> {
            MentorshipRequest req = invocation.getArgument(0);
            req.setId(10L);
            req.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return req;
        });

        MentorshipRequestResponse response = mentorshipRequestService.createRequest(1L, createRequest);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getMenteeId()).isEqualTo(1L);
        assertThat(response.getMenteeFirstName()).isEqualTo("Elif");
        assertThat(response.getMentorId()).isEqualTo(2L);
        assertThat(response.getMentorFirstName()).isEqualTo("Ahmet");
        assertThat(response.getMessage()).isEqualTo("I'd love to learn from you!");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(notificationEventPublisher).publishRequestReceived(2L, "Elif");
    }

    @Test
    void createRequestSavesCorrectEntity() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findById(2L)).thenReturn(Optional.of(mentor));
        when(mentorshipRequestRepository.existsByMentee_IdAndMentor_IdAndStatus(
                1L, 2L, MentorshipRequestStatus.PENDING)).thenReturn(false);
        when(mentorshipRequestRepository.save(any(MentorshipRequest.class))).thenAnswer(invocation -> {
            MentorshipRequest req = invocation.getArgument(0);
            req.setId(10L);
            req.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return req;
        });

        mentorshipRequestService.createRequest(1L, createRequest);

        ArgumentCaptor<MentorshipRequest> captor = ArgumentCaptor.forClass(MentorshipRequest.class);
        verify(mentorshipRequestRepository).save(captor.capture());
        MentorshipRequest saved = captor.getValue();
        assertThat(saved.getMentee()).isEqualTo(mentee);
        assertThat(saved.getMentor()).isEqualTo(mentor);
        assertThat(saved.getMessage()).isEqualTo("I'd love to learn from you!");
        assertThat(saved.getStatus()).isEqualTo(MentorshipRequestStatus.PENDING);
    }

    @Test
    void createRequestWithoutMessage() {
        createRequest.setMessage(null);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findById(2L)).thenReturn(Optional.of(mentor));
        when(mentorshipRequestRepository.existsByMentee_IdAndMentor_IdAndStatus(
                1L, 2L, MentorshipRequestStatus.PENDING)).thenReturn(false);
        when(mentorshipRequestRepository.save(any(MentorshipRequest.class))).thenAnswer(invocation -> {
            MentorshipRequest req = invocation.getArgument(0);
            req.setId(10L);
            req.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return req;
        });

        MentorshipRequestResponse response = mentorshipRequestService.createRequest(1L, createRequest);

        assertThat(response.getMessage()).isNull();
    }

    // ── Create request: validation failures ─────────────────────────────────

    @Test
    void createRequestMenteeNotFound() {
        when(menteeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipRequestService.createRequest(99L, createRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Mentee not found");
    }

    @Test
    void createRequestMentorNotFound() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        createRequest.setMentorId(99L);
        when(mentorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipRequestService.createRequest(1L, createRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Mentor not found");
    }

    @Test
    void createRequestMenteeHasActiveMentor() {
        mentee.setActiveMentorId(50L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findById(2L)).thenReturn(Optional.of(mentor));

        assertThatThrownBy(() -> mentorshipRequestService.createRequest(1L, createRequest))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("active mentor");
    }

    @Test
    void createRequestMentorAtCapacity() {
        mentor.setCurrentMenteeCount(3);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findById(2L)).thenReturn(Optional.of(mentor));

        assertThatThrownBy(() -> mentorshipRequestService.createRequest(1L, createRequest))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void createRequestDuplicatePending() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findById(2L)).thenReturn(Optional.of(mentor));
        when(mentorshipRequestRepository.existsByMentee_IdAndMentor_IdAndStatus(
                1L, 2L, MentorshipRequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> mentorshipRequestService.createRequest(1L, createRequest))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("pending request");
    }

    @Test
    void createRequestConcurrentDuplicateCaughtByDb() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorRepository.findById(2L)).thenReturn(Optional.of(mentor));
        when(mentorshipRequestRepository.existsByMentee_IdAndMentor_IdAndStatus(
                1L, 2L, MentorshipRequestStatus.PENDING)).thenReturn(false);
        when(mentorshipRequestRepository.save(any(MentorshipRequest.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violated"));

        assertThatThrownBy(() -> mentorshipRequestService.createRequest(1L, createRequest))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("pending request");
    }

    // ── Get sent requests ───────────────────────────────────────────────────

    @Test
    void getSentRequestsSuccess() {
        MentorshipRequest req = new MentorshipRequest();
        req.setId(10L);
        req.setMentee(mentee);
        req.setMentor(mentor);
        req.setStatus(MentorshipRequestStatus.PENDING);
        req.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(mentorshipRequestRepository.findByMenteeIdWithUsers(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req)));

        Page<MentorshipRequestResponse> result = mentorshipRequestService.getSentRequests(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMentorFirstName()).isEqualTo("Ahmet");
    }

    @Test
    void getSentRequestsMenteeNotFound() {
        when(menteeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipRequestService.getSentRequests(99L, pageable))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Get received requests ───────────────────────────────────────────────

    @Test
    void getReceivedRequestsSuccess() {
        MentorshipRequest req = new MentorshipRequest();
        req.setId(10L);
        req.setMentee(mentee);
        req.setMentor(mentor);
        req.setStatus(MentorshipRequestStatus.PENDING);
        req.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(mentorRepository.findById(2L)).thenReturn(Optional.of(mentor));
        when(mentorshipRequestRepository.findByMentorIdWithUsers(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req)));

        Page<MentorshipRequestResponse> result = mentorshipRequestService.getReceivedRequests(2L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMenteeFirstName()).isEqualTo("Elif");
    }

    @Test
    void getReceivedRequestsMentorNotFound() {
        when(mentorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipRequestService.getReceivedRequests(99L, pageable))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Ban gate (#134) ─────────────────────────────────────────────────────

    @Test
    void createRequest_throwsUserBannedException_whenMenteeIsBanned() {
        Ban activeBan = new Ban();
        activeBan.setUser(mentee);
        activeBan.setReason("Frequent mentorship request cancellations");
        activeBan.setBanCount(1);
        activeBan.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
        when(banService.getActiveBan(1L)).thenReturn(Optional.of(activeBan));

        assertThatThrownBy(() -> mentorshipRequestService.createRequest(1L, createRequest))
                .isInstanceOf(UserBannedException.class)
                .extracting(ex -> ((UserBannedException) ex).getBan().getReason())
                .isEqualTo("Frequent mentorship request cancellations");
        verify(mentorshipRequestRepository, org.mockito.Mockito.never())
                .save(any(MentorshipRequest.class));
    }

    // ── cancelOwnPendingRequest (#134) ──────────────────────────────────────

    @Test
    void cancelOwnPendingRequest_happyPath_flipsStatusAndRecordsViolation() {
        MentorshipRequest request = new MentorshipRequest();
        request.setId(10L);
        request.setMentee(mentee);
        request.setMentor(mentor);
        request.setStatus(MentorshipRequestStatus.PENDING);
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        mentorshipRequestService.cancelOwnPendingRequest(1L, 10L);

        assertThat(request.getStatus()).isEqualTo(MentorshipRequestStatus.CANCELLED);
        verify(mentorshipRequestRepository).save(request);
        verify(banService).recordCancellation(eq(1L),
                eq("Frequent mentorship request cancellations"));
    }

    @Test
    void cancelOwnPendingRequest_throwsWhenCallerIsNotOwner() {
        Mentee other = new Mentee();
        other.setId(99L);
        MentorshipRequest request = new MentorshipRequest();
        request.setId(10L);
        request.setMentee(other);
        request.setMentor(mentor);
        request.setStatus(MentorshipRequestStatus.PENDING);
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> mentorshipRequestService.cancelOwnPendingRequest(1L, 10L))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("do not own");
        verify(mentorshipRequestRepository, org.mockito.Mockito.never())
                .save(any(MentorshipRequest.class));
        verify(banService, org.mockito.Mockito.never())
                .recordCancellation(any(), any());
    }

    @Test
    void cancelOwnPendingRequest_throwsWhenStatusIsNotPending() {
        MentorshipRequest request = new MentorshipRequest();
        request.setId(10L);
        request.setMentee(mentee);
        request.setMentor(mentor);
        request.setStatus(MentorshipRequestStatus.ACCEPTED);
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> mentorshipRequestService.cancelOwnPendingRequest(1L, 10L))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("Only pending");
        verify(banService, org.mockito.Mockito.never())
                .recordCancellation(any(), any());
    }

    @Test
    void cancelOwnPendingRequest_throwsWhenRequestMissing() {
        when(mentorshipRequestRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorshipRequestService.cancelOwnPendingRequest(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
