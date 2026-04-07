package com.group7.backend.repository;

import com.group7.backend.entity.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    List<AvailabilitySlot> findByMentorId(Long mentorId);

    void deleteByMentorId(Long mentorId);

    long deleteByIdAndMentorId(Long id, Long mentorId);
}
