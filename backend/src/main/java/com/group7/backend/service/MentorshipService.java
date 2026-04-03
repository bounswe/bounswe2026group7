package com.group7.backend.service;

import com.group7.backend.dto.request.AcceptRequestRequest;
import com.group7.backend.dto.request.SharedGoalRequest;
import com.group7.backend.dto.response.MentorshipResponse;
import com.group7.backend.entity.*;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class MentorshipService {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(1, 3, 6);

    private final MentorshipRepository mentorshipRepository;
    private final MentorshipRequestRepository mentorshipRequestRepository;

    public MentorshipService(MentorshipRepository mentorshipRepository,
                             MentorshipRequestRepository mentorshipRequestRepository) {
        this.mentorshipRepository = mentorshipRepository;
        this.mentorshipRequestRepository = mentorshipRequestRepository;
    }

    @Transactional
    public MentorshipResponse acceptRequest(Long mentorId, Long requestId, AcceptRequestRequest dto) {
        MentorshipRequest request = mentorshipRequestRepository.findById(requestId)
                .filter(r -> r.getMentor().getId().equals(mentorId))
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship request not found"));

        if (request.getStatus() != MentorshipRequestStatus.PENDING) {
            throw new MentorshipRequestException("Request is no longer pending");
        }

        Mentee mentee = request.getMentee();
        Mentor mentor = request.getMentor();

        if (mentee.getActiveMentorId() != null) {
            throw new MentorshipRequestException("Mentee already has an active mentor");
        }

        if (mentor.getCurrentMenteeCount() >= mentor.getMaxMenteeCapacity()) {
            throw new MentorshipRequestException("You have reached your maximum mentee capacity");
        }

        if (!ALLOWED_DURATIONS.contains(dto.getDuration())) {
            throw new MentorshipRequestException("Duration must be 1, 3, or 6 months");
        }

        request.setStatus(MentorshipRequestStatus.ACCEPTED);

        LocalDateTime now = LocalDateTime.now();
        Mentorship mentorship = new Mentorship();
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setRequest(request);
        mentorship.setStartDate(now);
        mentorship.setEndDate(now.plusMonths(dto.getDuration()));
        mentorship.setDuration(dto.getDuration());

        mentee.setActiveMentorId(mentorId);
        mentor.setCurrentMenteeCount(mentor.getCurrentMenteeCount() + 1);

        mentorshipRequestRepository.cancelOtherPendingRequests(mentee.getId(), requestId);

        Mentorship saved = mentorshipRepository.save(mentorship);
        return MentorshipResponse.from(saved);
    }

    @Transactional
    public void rejectRequest(Long mentorId, Long requestId) {
        MentorshipRequest request = mentorshipRequestRepository.findById(requestId)
                .filter(r -> r.getMentor().getId().equals(mentorId))
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship request not found"));

        if (request.getStatus() != MentorshipRequestStatus.PENDING) {
            throw new MentorshipRequestException("Request is no longer pending");
        }

        request.setStatus(MentorshipRequestStatus.REJECTED);
    }

    @Transactional(readOnly = true)
    public List<MentorshipResponse> getActiveMentorships(Long userId) {
        return mentorshipRepository.findByUserIdAndStatus(userId, MentorshipStatus.ACTIVE).stream()
                .map(MentorshipResponse::from)
                .toList();
    }

    @Transactional
    public MentorshipResponse setSharedGoal(Long userId, Long mentorshipId, SharedGoalRequest dto) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .filter(m -> m.getMentor().getId().equals(userId) || m.getMentee().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found"));

        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            throw new MentorshipRequestException("Mentorship is not active");
        }

        mentorship.setSharedGoal(dto.getSharedGoal());
        return MentorshipResponse.from(mentorshipRepository.save(mentorship));
    }
}
