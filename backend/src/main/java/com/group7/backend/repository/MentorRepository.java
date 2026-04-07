package com.group7.backend.repository;

import com.group7.backend.entity.Mentor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentorRepository extends JpaRepository<Mentor, Long> {
    List<Mentor> findByExpertise(String expertise);
}
