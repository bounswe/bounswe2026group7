package com.group7.backend.repository;

import com.group7.backend.entity.Task;
import com.group7.backend.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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

    @Query("SELECT t FROM Task t JOIN FETCH t.mentorship m JOIN FETCH m.mentee WHERE t.status = 'PENDING' AND t.dueDate BETWEEN :from AND :to")
    List<Task> findPendingTasksDueWithin(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
