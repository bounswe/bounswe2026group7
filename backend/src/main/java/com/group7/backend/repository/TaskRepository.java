package com.group7.backend.repository;

import com.group7.backend.entity.Task;
import com.group7.backend.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByMentorshipIdOrderByCreatedAtDesc(Long mentorshipId);

    @Query("SELECT t FROM Task t JOIN FETCH t.mentorship m JOIN FETCH m.mentor JOIN FETCH m.mentee WHERE t.id = :id")
    Optional<Task> findByIdWithMentorship(@Param("id") Long id);

    long countByMentorshipId(Long mentorshipId);

    long countByMentorshipIdAndStatus(Long mentorshipId, TaskStatus status);

    /**
     * Tasks of a mentorship whose {@code dueDate} falls inside the inclusive
     * {@code [from, to]} window. Tasks with a null {@code dueDate} are excluded
     * naturally by the {@code BETWEEN} predicate — they have no place on a
     * chronological timeline. Used by the mentorship timeline aggregation (#332).
     */
    @Query("SELECT t FROM Task t "
            + "WHERE t.mentorship.id = :mentorshipId "
            + "AND t.dueDate BETWEEN :from AND :to")
    List<Task> findInWindow(
            @Param("mentorshipId") Long mentorshipId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /**
     * Tasks the mentee still owes work on (PENDING or REVISION_REQUESTED) whose
     * dueDate is in the [from, to] window. REVISION_REQUESTED is included so a
     * task the mentor bounced back still triggers the 24-hour deadline reminder.
     */
    @Query("SELECT t FROM Task t JOIN FETCH t.mentorship m JOIN FETCH m.mentee "
            + "WHERE t.status IN ('PENDING', 'REVISION_REQUESTED') "
            + "AND t.dueDate BETWEEN :from AND :to")
    List<Task> findPendingTasksDueWithin(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // Stats aggregations (#253). Two-hop nested predicate (`t.mentorship.mentor.id`)
    // requires explicit JPQL; Spring Data derived names get unwieldy at this depth.

    @Query("SELECT COUNT(t) FROM Task t WHERE t.mentorship.mentor.id = :mentorId")
    long countByMentorId(@Param("mentorId") Long mentorId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.mentorship.mentor.id = :mentorId AND t.status = :status")
    long countByMentorIdAndStatus(@Param("mentorId") Long mentorId, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.mentorship.mentee.id = :menteeId AND t.status = :status")
    long countByMenteeIdAndStatus(@Param("menteeId") Long menteeId, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.mentorship.mentee.id = :menteeId AND t.status IN :statuses")
    long countByMenteeIdAndStatusIn(@Param("menteeId") Long menteeId,
                                    @Param("statuses") Collection<TaskStatus> statuses);
}
