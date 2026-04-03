package com.group7.backend.repository;

import com.group7.backend.entity.Mentor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MentorRepository extends JpaRepository<Mentor, Long> {
    List<Mentor> findByExpertise(String expertise);

    @Query("SELECT DISTINCT m FROM Mentor m LEFT JOIN FETCH m.interests LEFT JOIN FETCH m.preferredMenteeSkills")
    List<Mentor> findAllWithCollections();
}
