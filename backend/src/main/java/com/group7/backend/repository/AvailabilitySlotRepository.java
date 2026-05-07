package com.group7.backend.repository;

import com.group7.backend.entity.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    List<AvailabilitySlot> findByMentorId(Long mentorId);

    /**
     * Boolean-only existence check. Cheaper than {@code findByMentorId(...).isEmpty()}
     * because Spring Data emits {@code SELECT 1 ... LIMIT 1} (no entity hydration).
     * Used by {@code UserService.searchUsers} to validate that a mentor has at
     * least one slot before allowing a {@code hasAvailability=true} search.
     */
    boolean existsByMentorId(Long mentorId);

    /**
     * Batch fetch slots for many mentors at once — eliminates the
     * one-query-per-mentor N+1 in {@code MatchingService} when scoring
     * availability overlap. The service groups results by mentor id before
     * passing each mentor's slots to the ranker.
     *
     * <p>Caller should short-circuit on an empty input collection rather than
     * relying on Hibernate's empty-IN rewrite — both for clarity and because
     * the calling site has already paid for the SQL fetch that surfaced no
     * candidates.
     */
    List<AvailabilitySlot> findByMentorIdIn(Collection<Long> mentorIds);

    void deleteByMentorId(Long mentorId);

    long deleteByIdAndMentorId(Long id, Long mentorId);
}
