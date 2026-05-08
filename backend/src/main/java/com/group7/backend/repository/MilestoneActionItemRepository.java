package com.group7.backend.repository;

import com.group7.backend.entity.MilestoneActionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneActionItemRepository extends JpaRepository<MilestoneActionItem, Long> {

    List<MilestoneActionItem> findByMilestoneIdOrderByOrderIndexAsc(Long milestoneId);

    @Query("SELECT a FROM MilestoneActionItem a JOIN FETCH a.milestone m JOIN FETCH m.mentorship WHERE a.id = :id")
    Optional<MilestoneActionItem> findByIdWithMilestoneAndMentorship(Long id);
}
