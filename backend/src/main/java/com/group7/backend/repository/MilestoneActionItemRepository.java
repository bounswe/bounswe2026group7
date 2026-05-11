package com.group7.backend.repository;

import com.group7.backend.entity.MilestoneActionItem;
import com.group7.backend.repository.projection.ActionItemCountTuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneActionItemRepository extends JpaRepository<MilestoneActionItem, Long> {

    List<MilestoneActionItem> findByMilestoneIdOrderByOrderIndexAsc(Long milestoneId);

    @Query("SELECT a FROM MilestoneActionItem a JOIN FETCH a.milestone m JOIN FETCH m.mentorship WHERE a.id = :id")
    Optional<MilestoneActionItem> findByIdWithMilestoneAndMentorship(Long id);

    /**
     * Bulk-load action-item counts for a set of milestone ids in one query.
     * Returns one tuple per milestone that has at least one action item; milestones
     * with zero action items don't appear in the result and the caller defaults them
     * to {@code (0, 0)}. Used by the mentorship timeline aggregation (#332) to avoid N+1.
     *
     * <p>Caller must short-circuit on an empty {@code ids} collection — Hibernate 6
     * emits invalid SQL ({@code WHERE x IN ()}) for empty IN parameters.
     */
    /**
     * The {@code CAST(... AS Long)} on the {@code SUM} pins the expression's return type so
     * Hibernate's JPQL constructor-expression resolution against
     * {@link ActionItemCountTuple} (which takes three {@code Long} components) cannot
     * silently drift on a future Hibernate / driver upgrade.
     */
    @Query("SELECT new com.group7.backend.repository.projection.ActionItemCountTuple("
            + "a.milestone.id, COUNT(a), "
            + "CAST(COALESCE(SUM(CASE WHEN a.isCompleted = true THEN 1L ELSE 0L END), 0L) AS java.lang.Long)) "
            + "FROM MilestoneActionItem a "
            + "WHERE a.milestone.id IN :ids "
            + "GROUP BY a.milestone.id")
    List<ActionItemCountTuple> countByMilestoneIds(@Param("ids") Collection<Long> ids);
}
