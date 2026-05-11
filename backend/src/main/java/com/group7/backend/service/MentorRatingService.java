package com.group7.backend.service;

import com.group7.backend.dto.request.CreateMentorRatingRequest;
import com.group7.backend.dto.response.MentorRatingResponse;
import com.group7.backend.dto.response.UserRatingSummary;
import com.group7.backend.entity.MentorRating;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorRatingRepository;
import com.group7.backend.repository.MentorshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mentee → mentor rating after a mentorship has terminated (#237).
 *
 * <p>Validation order: 404 (not a participant) → 403 (not the mentee) →
 * 409 (mentorship still active) → 409 (already rated). Persistence catches
 * {@link DataIntegrityViolationException} as the race-loss path because the
 * {@code UNIQUE(mentorship_id)} constraint can fire even when the existsBy
 * check above passed (two concurrent POSTs).
 */
@Service
public class MentorRatingService {

    private static final Logger log = LoggerFactory.getLogger(MentorRatingService.class);

    private final MentorRatingRepository mentorRatingRepository;
    private final MentorshipRepository mentorshipRepository;

    public MentorRatingService(MentorRatingRepository mentorRatingRepository,
                               MentorshipRepository mentorshipRepository) {
        this.mentorRatingRepository = mentorRatingRepository;
        this.mentorshipRepository = mentorshipRepository;
    }

    @Transactional
    public MentorRatingResponse createRating(Long menteeUserId,
                                             Long mentorshipId,
                                             CreateMentorRatingRequest dto) {
        Mentorship mentorship = mentorshipRepository
                .findByIdAndParticipant(mentorshipId, menteeUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found"));

        if (!mentorship.getMentee().getId().equals(menteeUserId)) {
            throw new AccessDeniedException("Only the mentee can rate the mentor");
        }

        MentorshipStatus status = mentorship.getStatus();
        if (status != MentorshipStatus.COMPLETED && status != MentorshipStatus.CANCELLED) {
            log.warn("Rating rejected: mentorshipId={} is in status {}", mentorshipId, status);
            throw new MentorshipRequestException("Mentorship has not ended yet");
        }

        if (mentorRatingRepository.existsByMentorshipId(mentorshipId)) {
            throw new MentorshipRequestException("This mentorship has already been rated");
        }

        MentorRating rating = MentorRating.of(
                mentorshipId,
                mentorship.getMentor().getId(),
                menteeUserId,
                dto.getScore(),
                dto.getComment());

        try {
            MentorRating saved = mentorRatingRepository.save(rating);
            log.info("Mentor rating created: ratingId={}, mentorshipId={}, mentorId={}, score={}",
                    saved.getId(), mentorshipId, saved.getMentorId(), saved.getScore());
            return MentorRatingResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(mentorship_id) lost the race against a concurrent POST.
            log.warn("Mentor rating race-lost on UNIQUE(mentorship_id): mentorshipId={}", mentorshipId);
            throw new MentorshipRequestException("This mentorship has already been rated");
        }
    }

    @Transactional(readOnly = true)
    public UserRatingSummary aggregateForMentor(Long mentorId) {
        UserRatingSummary summary = mentorRatingRepository.aggregateForMentor(mentorId);
        return summary != null ? summary : UserRatingSummary.empty();
    }
}
