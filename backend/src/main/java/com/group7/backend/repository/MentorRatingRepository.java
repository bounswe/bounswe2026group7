package com.group7.backend.repository;

import com.group7.backend.dto.response.UserRatingSummary;
import com.group7.backend.entity.MentorRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MentorRatingRepository extends JpaRepository<MentorRating, Long> {

    Optional<MentorRating> findByMentorshipId(Long mentorshipId);

    boolean existsByMentorshipId(Long mentorshipId);

    /**
     * Aggregate average + count for a mentor in one query (#237). Returns
     * {@code (null, 0)} when the mentor has no ratings — JPQL {@code AVG}
     * over the empty set is {@code null}, which the constructor expression
     * passes straight through.
     */
    @Query("SELECT new com.group7.backend.dto.response.UserRatingSummary("
            + "AVG(CAST(r.score AS double)), COUNT(r)) "
            + "FROM MentorRating r WHERE r.mentorId = :mentorId")
    UserRatingSummary aggregateForMentor(@Param("mentorId") Long mentorId);
}
