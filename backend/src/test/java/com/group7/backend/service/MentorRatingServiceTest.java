package com.group7.backend.service;

import com.group7.backend.dto.request.CreateMentorRatingRequest;
import com.group7.backend.dto.response.MentorRatingResponse;
import com.group7.backend.dto.response.UserRatingSummary;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.MentorRating;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorRatingRepository;
import com.group7.backend.repository.MentorshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorRatingServiceTest {

    @Mock private MentorRatingRepository mentorRatingRepository;
    @Mock private MentorshipRepository mentorshipRepository;

    @InjectMocks private MentorRatingService mentorRatingService;

    private Mentor mentor;
    private Mentee mentee;
    private Mentorship mentorship;

    @BeforeEach
    void setUp() {
        mentor = new Mentor();
        mentor.setId(1L);
        mentor.setFirstName("Ahmet");

        mentee = new Mentee();
        mentee.setId(2L);
        mentee.setFirstName("Elif");

        mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.COMPLETED);
    }

    private CreateMentorRatingRequest dto(int score, String comment) {
        CreateMentorRatingRequest d = new CreateMentorRatingRequest();
        d.setScore(score);
        d.setComment(comment);
        return d;
    }

    @Test
    void createRating_persistsAndReturns() {
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));
        when(mentorRatingRepository.existsByMentorshipId(100L)).thenReturn(false);
        when(mentorRatingRepository.save(any(MentorRating.class))).thenAnswer(inv -> {
            MentorRating r = inv.getArgument(0);
            r.setId(42L);
            return r;
        });

        MentorRatingResponse response = mentorRatingService.createRating(2L, 100L, dto(5, "Great!"));

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getMentorId()).isEqualTo(1L);
        assertThat(response.getMenteeId()).isEqualTo(2L);
        assertThat(response.getScore()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Great!");

        ArgumentCaptor<MentorRating> captor = ArgumentCaptor.forClass(MentorRating.class);
        verify(mentorRatingRepository).save(captor.capture());
        assertThat(captor.getValue().getMentorshipId()).isEqualTo(100L);
    }

    @Test
    void createRating_alsoAllowedAfterCancelled() {
        mentorship.setStatus(MentorshipStatus.CANCELLED);
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));
        when(mentorRatingRepository.existsByMentorshipId(100L)).thenReturn(false);
        when(mentorRatingRepository.save(any(MentorRating.class))).thenAnswer(inv -> inv.getArgument(0));

        mentorRatingService.createRating(2L, 100L, dto(3, null));

        verify(mentorRatingRepository).save(any());
    }

    @Test
    void createRating_rejectsActiveMentorship() {
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> mentorRatingService.createRating(2L, 100L, dto(5, null)))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("not ended");
        verify(mentorRatingRepository, never()).save(any());
    }

    @Test
    void createRating_rejectsMentorActor() {
        when(mentorshipRepository.findByIdAndParticipant(100L, 1L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> mentorRatingService.createRating(1L, 100L, dto(5, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createRating_rejectsNonParticipant() {
        when(mentorshipRepository.findByIdAndParticipant(100L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mentorRatingService.createRating(999L, 100L, dto(5, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRating_rejectsDuplicate_existsByCheck() {
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));
        when(mentorRatingRepository.existsByMentorshipId(100L)).thenReturn(true);

        assertThatThrownBy(() -> mentorRatingService.createRating(2L, 100L, dto(5, null)))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("already been rated");
        verify(mentorRatingRepository, never()).save(any());
    }

    @Test
    void createRating_rejectsDuplicate_raceLost() {
        when(mentorshipRepository.findByIdAndParticipant(100L, 2L)).thenReturn(Optional.of(mentorship));
        when(mentorRatingRepository.existsByMentorshipId(100L)).thenReturn(false);
        when(mentorRatingRepository.save(any(MentorRating.class)))
                .thenThrow(new DataIntegrityViolationException("UNIQUE(mentorship_id)"));

        assertThatThrownBy(() -> mentorRatingService.createRating(2L, 100L, dto(5, null)))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("already been rated");
    }

    @Test
    void aggregateForMentor_returnsZerosWhenNone() {
        when(mentorRatingRepository.aggregateForMentor(1L)).thenReturn(UserRatingSummary.empty());

        UserRatingSummary summary = mentorRatingService.aggregateForMentor(1L);

        assertThat(summary.averageRating()).isNull();
        assertThat(summary.ratingCount()).isZero();
    }

    @Test
    void aggregateForMentor_returnsAggregate() {
        when(mentorRatingRepository.aggregateForMentor(1L))
                .thenReturn(new UserRatingSummary(4.5, 12L));

        UserRatingSummary summary = mentorRatingService.aggregateForMentor(1L);

        assertThat(summary.averageRating()).isEqualTo(4.5);
        assertThat(summary.ratingCount()).isEqualTo(12L);
    }

    @Test
    void aggregateForMentor_handlesNullFromRepository() {
        // Some repo configurations return null for an empty aggregate; service
        // must coalesce to UserRatingSummary.empty() to keep callers safe.
        when(mentorRatingRepository.aggregateForMentor(1L)).thenReturn(null);

        UserRatingSummary summary = mentorRatingService.aggregateForMentor(1L);

        assertThat(summary.averageRating()).isNull();
        assertThat(summary.ratingCount()).isZero();
    }
}
