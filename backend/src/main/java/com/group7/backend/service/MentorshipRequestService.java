package com.group7.backend.service;

import com.group7.backend.dto.request.MentorshipRequestCreateRequest;
import com.group7.backend.dto.response.MentorshipRequestResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MentorshipRequestService {

    private static final Logger log = LoggerFactory.getLogger(MentorshipRequestService.class);

    private final MentorshipRequestRepository mentorshipRequestRepository;
    private final MenteeRepository menteeRepository;
    private final MentorRepository mentorRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final BanService banService;
    private final MentorshipCooldownPolicy mentorshipCooldownPolicy;

    public MentorshipRequestService(MentorshipRequestRepository mentorshipRequestRepository,
                                    MenteeRepository menteeRepository,
                                    MentorRepository mentorRepository,
                                    NotificationEventPublisher notificationEventPublisher,
                                    BanService banService,
                                    MentorshipCooldownPolicy mentorshipCooldownPolicy) {
        this.mentorshipRequestRepository = mentorshipRequestRepository;
        this.menteeRepository = menteeRepository;
        this.mentorRepository = mentorRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.banService = banService;
        this.mentorshipCooldownPolicy = mentorshipCooldownPolicy;
    }

    @Transactional
    public MentorshipRequestResponse createRequest(Long menteeId, MentorshipRequestCreateRequest dto) {
        // Ban gate (#134, req 2.2.4): banned mentees cannot submit new requests.
        // Runs first so the 403 response carries expiresAt + reason without
        // touching mentor / capacity / dedup state. Read paths remain open.
        banService.getActiveBan(menteeId).ifPresent(ban -> {
            log.warn("Mentorship request rejected: menteeId={} is banned until {}",
                    menteeId, ban.getExpiresAt());
            throw new UserBannedException(ban);
        });

        Mentee mentee = menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));

        Mentor mentor = mentorRepository.findById(dto.getMentorId())
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));

        if (mentee.getActiveMentorId() != null) {
            log.warn("Mentorship request rejected: menteeId={} already has active mentor", menteeId);
            throw new MentorshipRequestException("You already have an active mentor");
        }

        if (mentor.getCurrentMenteeCount() >= mentor.getMaxMenteeCapacity()) {
            log.warn("Mentorship request rejected: mentorId={} is at capacity", dto.getMentorId());
            throw new MentorshipRequestException("Mentor has reached maximum mentee capacity");
        }

        // Cool-down (#133): block re-requests if this pair just terminated a mentorship.
        mentorshipCooldownPolicy.assertNotInCooldown(dto.getMentorId(), menteeId);

        if (mentorshipRequestRepository.existsByMentee_IdAndMentor_IdAndStatus(
                menteeId, dto.getMentorId(), MentorshipRequestStatus.PENDING)) {
            log.warn("Mentorship request rejected: duplicate pending request for menteeId={}, mentorId={}",
                    menteeId, dto.getMentorId());
            throw new MentorshipRequestException("You already have a pending request to this mentor");
        }

        MentorshipRequest request = new MentorshipRequest();
        request.setMentee(mentee);
        request.setMentor(mentor);
        request.setMessage(dto.getMessage());

        try {
            MentorshipRequest saved = mentorshipRequestRepository.save(request);
            notificationEventPublisher.publishRequestReceived(mentor.getId(), mentee.getFirstName());
            notificationEventPublisher.publishRequestSubmitted(mentee.getId(), mentor.getFirstName());
            log.info("Mentorship request created: requestId={}, menteeId={}, mentorId={}",
                    saved.getId(), menteeId, dto.getMentorId());
            return MentorshipRequestResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Mentorship request rejected by DB constraint: menteeId={}, mentorId={}",
                    menteeId, dto.getMentorId());
            throw new MentorshipRequestException("You already have a pending request to this mentor");
        }
    }

    @Transactional(readOnly = true)
    public Page<MentorshipRequestResponse> getSentRequests(Long menteeId, Pageable pageable) {
        menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));

        return mentorshipRequestRepository.findByMenteeIdWithUsers(menteeId, pageable)
                .map(MentorshipRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<MentorshipRequestResponse> getReceivedRequests(Long mentorId, Pageable pageable) {
        mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));

        return mentorshipRequestRepository.findByMentorIdWithUsers(mentorId, pageable)
                .map(MentorshipRequestResponse::from);
    }

    /**
     * Mentee cancels their own pending request (#134). Flips the request to
     * {@code CANCELLED} and records the violation via {@link BanService},
     * which may auto-impose a ban once the cancellation threshold is crossed.
     *
     * @throws ResourceNotFoundException if the request does not exist
     * @throws MentorshipRequestException if the caller does not own the request
     *         or the request is no longer in {@code PENDING} state
     */
    @Transactional
    public void cancelOwnPendingRequest(Long menteeId, Long requestId) {
        MentorshipRequest request = mentorshipRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship request not found"));

        if (!request.getMentee().getId().equals(menteeId)) {
            log.warn("Cancel rejected: menteeId={} does not own requestId={}", menteeId, requestId);
            throw new MentorshipRequestException("You do not own this mentorship request");
        }

        if (request.getStatus() != MentorshipRequestStatus.PENDING) {
            log.warn("Cancel rejected: requestId={} is in status {}", requestId, request.getStatus());
            throw new MentorshipRequestException("Only pending requests can be cancelled");
        }

        request.setStatus(MentorshipRequestStatus.CANCELLED);
        mentorshipRequestRepository.save(request);

        banService.recordCancellation(menteeId, "Frequent mentorship request cancellations");
        log.info("Mentorship request cancelled by mentee: requestId={}, menteeId={}", requestId, menteeId);
    }
}
