package com.group7.backend.repository;

import com.group7.backend.entity.MeetingActionItem;
import com.group7.backend.repository.projection.ActionItemCountTuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MeetingActionItemRepository extends JpaRepository<MeetingActionItem, Long> {

    List<MeetingActionItem> findByMeetingIdOrderByOrderIndexAscIdAsc(Long meetingId);

    /**
     * Bulk-load action-item counts for a set of meeting ids in one query.
     * Returns one tuple per meeting that has at least one action item; meetings with
     * zero action items don't appear in the result and the caller defaults them to
     * {@code (0, 0)}. Used by the mentorship timeline aggregation (#332) to avoid N+1.
     *
     * <p>Caller must short-circuit on an empty {@code ids} collection — Hibernate 6
     * emits invalid SQL ({@code WHERE x IN ()}) for empty IN parameters.
     */
    /**
     * The {@code CAST(... AS Long)} on the {@code SUM} pins the expression's return type so
     * Hibernate's JPQL constructor-expression resolution against
     * {@link ActionItemCountTuple} (which takes three {@code Long} components) cannot
     * silently drift on a future Hibernate / driver upgrade where {@code SUM} of a
     * {@code CASE} integer might widen to a different numeric type.
     */
    @Query("SELECT new com.group7.backend.repository.projection.ActionItemCountTuple("
            + "a.meeting.id, COUNT(a), "
            + "CAST(COALESCE(SUM(CASE WHEN a.completed = true THEN 1L ELSE 0L END), 0L) AS java.lang.Long)) "
            + "FROM MeetingActionItem a "
            + "WHERE a.meeting.id IN :ids "
            + "GROUP BY a.meeting.id")
    List<ActionItemCountTuple> countByMeetingIds(@Param("ids") Collection<Long> ids);
}
