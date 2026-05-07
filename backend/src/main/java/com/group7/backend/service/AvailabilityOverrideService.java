package com.group7.backend.service;

import com.group7.backend.dto.request.AvailabilityOverrideRequest;
import com.group7.backend.dto.response.AvailabilityOverrideResponse;
import com.group7.backend.entity.AvailabilityOverride;
import com.group7.backend.entity.AvailabilityOverrideKind;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.OverlappingSlotException;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AvailabilityOverrideRepository;
import com.group7.backend.repository.MentorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lifecycle service for one-off mentor {@link AvailabilityOverride} rows.
 * Layered on top of the weekly-recurring {@code mentor_availability_slots}
 * table — see {@code AvailabilityService} for the recurring base.
 *
 * <h2>Concurrency note</h2>
 * The same-kind overlap check is read-then-insert without an explicit lock.
 * Two near-simultaneous adds from the same mentor for the same kind could
 * both pass the check and create overlapping rows under the default
 * {@code READ_COMMITTED} isolation. Realistic concurrency from a single
 * mentor's session is ~0; a stronger guarantee (SERIALIZABLE isolation or a
 * Postgres exclusion constraint via {@code btree_gist}) is intentionally
 * deferred until a real conflict surfaces.
 */
@Service
public class AvailabilityOverrideService {

    private final AvailabilityOverrideRepository overrideRepository;
    private final MentorRepository mentorRepository;

    public AvailabilityOverrideService(AvailabilityOverrideRepository overrideRepository,
                                       MentorRepository mentorRepository) {
        this.overrideRepository = overrideRepository;
        this.mentorRepository = mentorRepository;
    }

    /**
     * Adds a new override for the authenticated mentor. Caller MUST pass
     * their own {@code mentorId} (resolved from JWT credentials in the
     * controller); the service does not consult the request body for the
     * mentor identity.
     *
     * @throws ResourceNotFoundException if the mentor row does not exist
     * @throws OverlappingSlotException  if the new range overlaps an
     *                                   existing same-kind override
     */
    @Transactional
    public AvailabilityOverrideResponse add(Long mentorId, AvailabilityOverrideRequest request) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));

        rejectIfOverlapsExistingSameKind(mentorId, request.getKind(),
                request.getStartAt(), request.getEndAt());

        AvailabilityOverride entity = new AvailabilityOverride();
        entity.setMentor(mentor);
        entity.setKind(request.getKind());
        entity.setStartAt(request.getStartAt());
        entity.setEndAt(request.getEndAt());
        return AvailabilityOverrideResponse.from(overrideRepository.save(entity));
    }

    /**
     * Removes a single override owned by the authenticated mentor.
     *
     * @throws ResourceNotFoundException if the override does not exist
     * @throws ProfileNotVisibleException if the override exists but belongs
     *                                    to a different mentor (404 would
     *                                    leak existence; 403 is the right
     *                                    signal for "not yours")
     */
    @Transactional
    public void remove(Long mentorId, Long overrideId) {
        AvailabilityOverride existing = overrideRepository.findById(overrideId)
                .orElseThrow(() -> new ResourceNotFoundException("Override not found"));
        if (!existing.getMentor().getId().equals(mentorId)) {
            throw new ProfileNotVisibleException("You can only delete your own overrides");
        }
        overrideRepository.delete(existing);
    }

    /**
     * Paginated listing for a mentor's overrides. Verifies the mentor
     * exists so callers get a clean 404 rather than an empty page when the
     * id is bogus.
     */
    @Transactional(readOnly = true)
    public Page<AvailabilityOverrideResponse> list(Long mentorId, Pageable pageable) {
        if (!mentorRepository.existsById(mentorId)) {
            throw new ResourceNotFoundException("Mentor not found");
        }
        return overrideRepository
                .findByMentorIdOrderByStartAtAscIdAsc(mentorId, pageable)
                .map(AvailabilityOverrideResponse::from);
    }

    private void rejectIfOverlapsExistingSameKind(Long mentorId,
                                                  AvailabilityOverrideKind kind,
                                                  OffsetDateTime newStart,
                                                  OffsetDateTime newEnd) {
        List<AvailabilityOverride> sameKind = overrideRepository
                .findByMentorIdAndKindOrderByStartAtAscIdAsc(mentorId, kind);
        for (AvailabilityOverride existing : sameKind) {
            if (overlaps(newStart, newEnd, existing.getStartAt(), existing.getEndAt())) {
                throw new OverlappingSlotException(
                        "Override overlaps an existing " + kind + " override on "
                                + existing.getStartAt() + " — " + existing.getEndAt());
            }
        }
    }

    private static boolean overlaps(OffsetDateTime aStart, OffsetDateTime aEnd,
                                    OffsetDateTime bStart, OffsetDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}
