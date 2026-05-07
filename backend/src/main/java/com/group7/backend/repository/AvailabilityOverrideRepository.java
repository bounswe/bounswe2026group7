package com.group7.backend.repository;

import com.group7.backend.entity.AvailabilityOverride;
import com.group7.backend.entity.AvailabilityOverrideKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvailabilityOverrideRepository extends JpaRepository<AvailabilityOverride, Long> {

    /**
     * Paginated listing for a mentor's overrides, ordered by {@code startAt}
     * with {@code id} as deterministic tiebreaker (two overrides created in
     * the same millisecond would otherwise shift between requests). Backed
     * by {@code idx_avail_ovr_mentor_start}.
     */
    Page<AvailabilityOverride> findByMentorIdOrderByStartAtAscIdAsc(
            Long mentorId, Pageable pageable);

    /**
     * All overrides of a given kind for a mentor — used by the service's
     * same-kind overlap check.
     */
    List<AvailabilityOverride> findByMentorIdAndKindOrderByStartAtAscIdAsc(
            Long mentorId, AvailabilityOverrideKind kind);

    /**
     * Unfiltered list (both kinds) for a mentor — used by
     * {@code CalendarExportService} to walk all overrides in one query when
     * building the iCalendar export.
     */
    List<AvailabilityOverride> findByMentorIdOrderByStartAtAscIdAsc(Long mentorId);
}
