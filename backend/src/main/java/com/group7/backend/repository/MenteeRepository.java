package com.group7.backend.repository;

import com.group7.backend.entity.Mentee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenteeRepository extends JpaRepository<Mentee, Long> {
    List<Mentee> findByLearningInterest(String learningInterest);
}
