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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage of {@link AvailabilityOverrideService}. Pins the
 * non-overlap rule (same-kind only), the cross-kind allow rule (the whole
 * point of overrides — UNAVAILABLE can sit on top of recurring AVAILABLE),
 * the ownership check on delete, and the mentor-not-found 404 paths.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityOverrideServiceTest {

    private static final Long MENTOR_ID = 100L;
    private static final Long OTHER_MENTOR_ID = 200L;

    @Mock private AvailabilityOverrideRepository overrideRepository;
    @Mock private MentorRepository mentorRepository;

    private AvailabilityOverrideService service;

    private Mentor mentor;

    @BeforeEach
    void setUp() {
        service = new AvailabilityOverrideService(overrideRepository, mentorRepository);
        mentor = new Mentor();
        mentor.setId(MENTOR_ID);
    }

    // ── add ────────────────────────────────────────────────────────────────

    @Test
    void add_persistsOverride_whenNoSameKindOverlap() {
        when(mentorRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        when(overrideRepository.findByMentorIdAndKindOrderByStartAtAscIdAsc(
                MENTOR_ID, AvailabilityOverrideKind.UNAVAILABLE))
                .thenReturn(List.of());
        when(overrideRepository.save(any(AvailabilityOverride.class)))
                .thenAnswer(inv -> {
                    AvailabilityOverride o = inv.getArgument(0);
                    o.setId(1L);
                    return o;
                });

        AvailabilityOverrideRequest req = request(
                AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-10T09:00:00Z", "2026-06-20T17:00:00Z");

        AvailabilityOverrideResponse result = service.add(MENTOR_ID, req);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getKind()).isEqualTo(AvailabilityOverrideKind.UNAVAILABLE);
        verify(overrideRepository).save(any(AvailabilityOverride.class));
    }

    @Test
    void add_rejectsSameKindOverlap() {
        when(mentorRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        // Existing UNAVAILABLE: 06-15..06-25; new UNAVAILABLE: 06-20..06-30 overlaps.
        AvailabilityOverride existing = override(
                7L, AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-15T00:00:00Z", "2026-06-25T00:00:00Z");
        when(overrideRepository.findByMentorIdAndKindOrderByStartAtAscIdAsc(
                MENTOR_ID, AvailabilityOverrideKind.UNAVAILABLE))
                .thenReturn(List.of(existing));

        AvailabilityOverrideRequest req = request(
                AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-20T00:00:00Z", "2026-06-30T00:00:00Z");

        assertThatThrownBy(() -> service.add(MENTOR_ID, req))
                .isInstanceOf(OverlappingSlotException.class);
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void add_allowsCrossKindOverlap_thePointOfTheModel() {
        // Mentor has an AVAILABLE override 09:00–12:00; they want to add an
        // UNAVAILABLE 10:00–11:00 to carve out the middle. This must succeed
        // — the whole reason the two-layer model exists is so consumers can
        // see "available, except this hour".
        when(mentorRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        // The check is for SAME-kind only — UNAVAILABLE find returns empty
        // even though an AVAILABLE row exists in the DB at 09:00–12:00 (not
        // stubbed because the service never queries that side).
        when(overrideRepository.findByMentorIdAndKindOrderByStartAtAscIdAsc(
                MENTOR_ID, AvailabilityOverrideKind.UNAVAILABLE))
                .thenReturn(List.of());
        when(overrideRepository.save(any(AvailabilityOverride.class)))
                .thenAnswer(inv -> {
                    AvailabilityOverride o = inv.getArgument(0);
                    o.setId(8L);
                    return o;
                });

        AvailabilityOverrideRequest req = request(
                AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-15T10:00:00Z", "2026-06-15T11:00:00Z");

        AvailabilityOverrideResponse result = service.add(MENTOR_ID, req);

        assertThat(result.getId()).isEqualTo(8L);
        // Confirm we never even queried the AVAILABLE list — only same-kind.
        verify(overrideRepository).findByMentorIdAndKindOrderByStartAtAscIdAsc(
                MENTOR_ID, AvailabilityOverrideKind.UNAVAILABLE);
        verify(overrideRepository, never()).findByMentorIdAndKindOrderByStartAtAscIdAsc(
                MENTOR_ID, AvailabilityOverrideKind.AVAILABLE);
    }

    @Test
    void add_throws404_whenMentorMissing() {
        when(mentorRepository.findById(MENTOR_ID)).thenReturn(Optional.empty());

        AvailabilityOverrideRequest req = request(
                AvailabilityOverrideKind.AVAILABLE,
                "2026-06-15T10:00:00Z", "2026-06-15T11:00:00Z");

        assertThatThrownBy(() -> service.add(MENTOR_ID, req))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void add_allowsAdjacentRanges() {
        // Two ranges that touch at a single instant (end == next start) must
        // not be considered overlapping. The overlap predicate uses strict
        // inequality so back-to-back blocks are legal.
        when(mentorRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        AvailabilityOverride existing = override(
                7L, AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-15T09:00:00Z", "2026-06-15T10:00:00Z");
        when(overrideRepository.findByMentorIdAndKindOrderByStartAtAscIdAsc(
                MENTOR_ID, AvailabilityOverrideKind.UNAVAILABLE))
                .thenReturn(List.of(existing));
        when(overrideRepository.save(any(AvailabilityOverride.class)))
                .thenAnswer(inv -> {
                    AvailabilityOverride o = inv.getArgument(0);
                    o.setId(8L);
                    return o;
                });

        AvailabilityOverrideRequest req = request(
                AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-15T10:00:00Z", "2026-06-15T11:00:00Z");

        assertThat(service.add(MENTOR_ID, req).getId()).isEqualTo(8L);
    }

    // ── remove ─────────────────────────────────────────────────────────────

    @Test
    void remove_deletesOwnOverride() {
        AvailabilityOverride existing = override(
                42L, AvailabilityOverrideKind.AVAILABLE,
                "2026-06-15T10:00:00Z", "2026-06-15T11:00:00Z");
        existing.setMentor(mentor);
        when(overrideRepository.findById(42L)).thenReturn(Optional.of(existing));

        service.remove(MENTOR_ID, 42L);

        verify(overrideRepository).delete(existing);
    }

    @Test
    void remove_throws403_whenOverrideBelongsToDifferentMentor() {
        Mentor other = new Mentor();
        other.setId(OTHER_MENTOR_ID);
        AvailabilityOverride existing = override(
                42L, AvailabilityOverrideKind.AVAILABLE,
                "2026-06-15T10:00:00Z", "2026-06-15T11:00:00Z");
        existing.setMentor(other);
        when(overrideRepository.findById(42L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.remove(MENTOR_ID, 42L))
                .isInstanceOf(ProfileNotVisibleException.class);
        verify(overrideRepository, never()).delete(any());
    }

    @Test
    void remove_throws404_whenOverrideMissing() {
        when(overrideRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(MENTOR_ID, 42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── list ───────────────────────────────────────────────────────────────

    @Test
    void list_returnsPagedResponses() {
        Pageable pageable = PageRequest.of(0, 20);
        AvailabilityOverride a = override(1L, AvailabilityOverrideKind.AVAILABLE,
                "2026-06-15T09:00:00Z", "2026-06-15T12:00:00Z");
        AvailabilityOverride b = override(2L, AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-20T00:00:00Z", "2026-06-25T00:00:00Z");
        when(mentorRepository.existsById(MENTOR_ID)).thenReturn(true);
        when(overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(eq(MENTOR_ID), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(a, b), pageable, 2));

        Page<AvailabilityOverrideResponse> result = service.list(MENTOR_ID, pageable);

        assertThat(result.getContent())
                .extracting(AvailabilityOverrideResponse::getId)
                .containsExactly(1L, 2L);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void list_throws404_whenMentorMissing() {
        when(mentorRepository.existsById(MENTOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.list(MENTOR_ID, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static AvailabilityOverrideRequest request(AvailabilityOverrideKind kind,
                                                       String startIso, String endIso) {
        AvailabilityOverrideRequest r = new AvailabilityOverrideRequest();
        r.setKind(kind);
        r.setStartAt(OffsetDateTime.parse(startIso));
        r.setEndAt(OffsetDateTime.parse(endIso));
        return r;
    }

    private static AvailabilityOverride override(Long id, AvailabilityOverrideKind kind,
                                                 String startIso, String endIso) {
        AvailabilityOverride o = new AvailabilityOverride();
        o.setId(id);
        o.setKind(kind);
        o.setStartAt(OffsetDateTime.parse(startIso));
        o.setEndAt(OffsetDateTime.parse(endIso));
        return o;
    }
}
