package com.group7.backend.repository;

import com.group7.backend.entity.MenteeAvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MenteeAvailabilitySlotRepository extends JpaRepository<MenteeAvailabilitySlot, Long> {

    List<MenteeAvailabilitySlot> findByMenteeId(Long menteeId);

    /**
     * Boolean-only existence check; symmetric to
     * {@code AvailabilitySlotRepository.existsByMentorId}. Used by
     * {@code UserService.searchUsers} for the {@code hasAvailability=true}
     * slot-presence guard.
     */
    boolean existsByMenteeId(Long menteeId);

    /**
     * Symmetric to {@code AvailabilitySlotRepository.findByMentorIdIn}.
     * Reserved for the mentor-side matching path's eventual N+1 elimination
     * if scoring against mentee slots ever becomes per-mentee.
     */
    List<MenteeAvailabilitySlot> findByMenteeIdIn(Collection<Long> menteeIds);

    void deleteByMenteeId(Long menteeId);

    long deleteByIdAndMenteeId(Long id, Long menteeId);
}