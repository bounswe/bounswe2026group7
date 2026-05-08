package com.group7.backend.repository;

import com.group7.backend.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByMentorshipIdOrderByCreatedAtDesc(Long mentorshipId);

    @Query("SELECT t FROM Task t JOIN FETCH t.mentorship m JOIN FETCH m.mentor JOIN FETCH m.mentee WHERE t.id = :id")
    Optional<Task> findByIdWithMentorship(@Param("id") Long id);
}
