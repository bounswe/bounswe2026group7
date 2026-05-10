package com.group7.backend.repository;

import com.group7.backend.entity.Milestone;
import com.group7.backend.entity.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByMentorshipIdOrderByOrderIndexAsc(Long mentorshipId);

    @Query("SELECT m FROM Milestone m JOIN FETCH m.mentorship WHERE m.id = :id")
    Optional<Milestone> findByIdWithMentorship(Long id);

    long countByMentorshipId(Long mentorshipId);

    long countByMentorshipIdAndStatus(Long mentorshipId, MilestoneStatus status);

    @Query("SELECT MAX(m.completedAt) FROM Milestone m WHERE m.mentorship.id = :mentorshipId")
    OffsetDateTime findMaxCompletedAtForMentorship(@Param("mentorshipId") Long mentorshipId);

    /**
     * Milestones of a mentorship whose {@code targetDate} falls inside the inclusive
     * {@code [from, to]} window. Milestones with a null {@code targetDate} are
     * excluded naturally by the {@code BETWEEN} predicate — they have no place on a
     * chronological timeline. Used by the mentorship timeline aggregation (#332).
     */
    @Query("SELECT m FROM Milestone m "
            + "WHERE m.mentorship.id = :mentorshipId "
            + "AND m.targetDate BETWEEN :from AND :to")
    List<Milestone> findInWindow(
            @Param("mentorshipId") Long mentorshipId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
