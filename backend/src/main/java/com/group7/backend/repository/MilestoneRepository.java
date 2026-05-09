package com.group7.backend.repository;

import com.group7.backend.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByMentorshipIdOrderByOrderIndexAsc(Long mentorshipId);

    @Query("SELECT m FROM Milestone m JOIN FETCH m.mentorship WHERE m.id = :id")
    Optional<Milestone> findByIdWithMentorship(Long id);
}
