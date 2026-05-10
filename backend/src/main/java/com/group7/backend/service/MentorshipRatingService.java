package com.group7.backend.service;

import com.group7.backend.dto.request.CreateRatingRequest;
import com.group7.backend.dto.response.RatingResponse;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipRating;
import com.group7.backend.entity.User;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorshipRatingRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Minimal mentorship-rating service introduced for issue #254 so the
 * cross-feature E2E suite can drive a real "rate" step. Out of scope here:
 * averages, aggregates, mentor-feedback expansion, and notifications —
 * those can layer on top without a schema change.
 *
 * <p>Authorization mirrors {@code MentorshipController}: non-participants
 * receive 404 (via {@link ResourceNotFoundException}) so the existence of
 * an id is not leaked.
 */
@Service
public class MentorshipRatingService {

    private final MentorshipRatingRepository ratingRepository;
    private final MentorshipRepository mentorshipRepository;
    private final UserRepository userRepository;

    public MentorshipRatingService(MentorshipRatingRepository ratingRepository,
                                   MentorshipRepository mentorshipRepository,
                                   UserRepository userRepository) {
        this.ratingRepository = ratingRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RatingResponse create(Long userId, Long mentorshipId, CreateRatingRequest request) {
        Mentorship mentorship = loadParticipantMentorship(userId, mentorshipId);

        Long mentorId = mentorship.getMentor().getId();
        Long menteeId = mentorship.getMentee().getId();
        Long ratedId = userId.equals(mentorId) ? menteeId : mentorId;

        if (ratingRepository.existsByMentorshipIdAndRater_Id(mentorshipId, userId)) {
            throw new MentorshipRequestException("You have already submitted a rating for this mentorship");
        }

        User rater = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User rated = userRepository.findById(ratedId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        MentorshipRating rating = new MentorshipRating();
        rating.setMentorship(mentorship);
        rating.setRater(rater);
        rating.setRated(rated);
        rating.setStars(request.getStars());
        rating.setComment(request.getComment());
        return RatingResponse.from(ratingRepository.save(rating));
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> list(Long userId, Long mentorshipId) {
        loadParticipantMentorship(userId, mentorshipId);
        return ratingRepository.findByMentorshipIdOrderByCreatedAtDesc(mentorshipId).stream()
                .map(RatingResponse::from)
                .toList();
    }

    private Mentorship loadParticipantMentorship(Long userId, Long mentorshipId) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found"));
        Long mentorId = mentorship.getMentor().getId();
        Long menteeId = mentorship.getMentee().getId();
        if (!userId.equals(mentorId) && !userId.equals(menteeId)) {
            throw new ResourceNotFoundException("Mentorship not found");
        }
        return mentorship;
    }
}
