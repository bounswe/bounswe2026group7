package com.group7.backend.repository;

import com.group7.backend.entity.MenteeAvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenteeAvailabilitySlotRepository extends JpaRepository<MenteeAvailabilitySlot, Long> {

    List<MenteeAvailabilitySlot> findByMenteeId(Long menteeId);

    void deleteByMenteeId(Long menteeId);

    long deleteByIdAndMenteeId(Long id, Long menteeId);
}