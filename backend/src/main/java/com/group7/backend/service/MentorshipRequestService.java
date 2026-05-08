package com.group7.backend.service;

import com.group7.backend.dto.request.MentorshipRequestCreateRequest;
import com.group7.backend.dto.response.MentorshipRequestResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.MentorshipRequest;
import com.group7.backend.entity.MentorshipRequestStatus;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
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

    public MentorshipRequestService(MentorshipRequestRepository mentorshipRequestRepository,
                                    MenteeRepository menteeRepository,
                                    MentorRepository mentorRepository,
                                    NotificationEventPublisher notificationEventPublisher) {
        this.mentorshipRequestRepository = mentorshipRequestRepository;
        this.menteeRepository = menteeRepository;
        this.mentorRepository = mentorRepository;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional
    public MentorshipRequestResponse createRequest(Long menteeId, MentorshipRequestCreateRequest dto) {
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
}
