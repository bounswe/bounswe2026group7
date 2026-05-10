package com.group7.backend.repository;

import com.group7.backend.entity.MentorshipRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorshipRatingRepository extends JpaRepository<MentorshipRating, Long> {

    List<MentorshipRating> findByMentorshipIdOrderByCreatedAtDesc(Long mentorshipId);

    boolean existsByMentorshipIdAndRater_Id(Long mentorshipId, Long raterUserId);
}
