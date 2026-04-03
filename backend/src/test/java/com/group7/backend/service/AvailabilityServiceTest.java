package com.group7.backend.service;

import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.response.AvailabilitySlotResponse;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.OverlappingSlotException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MentorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Mock
    private MentorRepository mentorRepository;

    @InjectMocks
    private AvailabilityService availabilityService;

    private Mentor mentor;

    @BeforeEach
    void setUp() {
        mentor = new Mentor();
        mentor.setId(1L);
    }

    private AvailabilitySlot slot(Long id, DayOfWeek day, String start, String end) {
        AvailabilitySlot s = new AvailabilitySlot();
        s.setId(id);
        s.setMentor(mentor);
        s.setDayOfWeek(day);
        s.setStartTime(LocalTime.parse(start));
        s.setEndTime(LocalTime.parse(end));
        s.setRecurring(true);
        return s;
    }

    private AvailabilitySlotRequest request(DayOfWeek day, String start, String end) {
        AvailabilitySlotRequest r = new AvailabilitySlotRequest();
        r.setDayOfWeek(day);
        r.setStartTime(LocalTime.parse(start));
        r.setEndTime(LocalTime.parse(end));
        r.setRecurring(true);
        return r;
    }

    // ── getSlots ────────────────────────────────────────────────────────────

    @Test
    void getSlotsReturnsSorted() {
        AvailabilitySlot friday = slot(1L, DayOfWeek.FRIDAY, "14:00", "16:00");
        AvailabilitySlot monday = slot(2L, DayOfWeek.MONDAY, "09:00", "12:00");

        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(availabilitySlotRepository.findByMentorId(1L)).thenReturn(List.of(friday, monday));

        List<AvailabilitySlotResponse> result = availabilityService.getSlots(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDayOfWeek()).isEqualTo("MONDAY");
        assertThat(result.get(1).getDayOfWeek()).isEqualTo("FRIDAY");
    }

    @Test
    void getSlotsMentorNotFound() {
        when(mentorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.getSlots(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── bulkUpdate ──────────────────────────────────────────────────────────

    @Test
    void bulkUpdateReplacesAll() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(availabilitySlotRepository.save(any(AvailabilitySlot.class))).thenAnswer(inv -> {
            AvailabilitySlot s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        List<AvailabilitySlotResponse> result = availabilityService.bulkUpdate(1L,
                List.of(request(DayOfWeek.MONDAY, "09:00", "12:00")));

        verify(availabilitySlotRepository).deleteByMentorId(1L);
        assertThat(result).hasSize(1);
    }

    @Test
    void bulkUpdateEmptyListClearsAll() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));

        List<AvailabilitySlotResponse> result = availabilityService.bulkUpdate(1L, List.of());

        verify(availabilitySlotRepository).deleteByMentorId(1L);
        assertThat(result).isEmpty();
    }

    @Test
    void bulkUpdateRejectsOverlapsWithinSet() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));

        assertThatThrownBy(() -> availabilityService.bulkUpdate(1L, List.of(
                request(DayOfWeek.MONDAY, "09:00", "12:00"),
                request(DayOfWeek.MONDAY, "11:00", "14:00"))))
                .isInstanceOf(OverlappingSlotException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void bulkUpdateAllowsAdjacentSlots() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(availabilitySlotRepository.save(any(AvailabilitySlot.class))).thenAnswer(inv -> {
            AvailabilitySlot s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        List<AvailabilitySlotResponse> result = availabilityService.bulkUpdate(1L, List.of(
                request(DayOfWeek.MONDAY, "09:00", "12:00"),
                request(DayOfWeek.MONDAY, "12:00", "15:00")));

        assertThat(result).hasSize(2);
    }

    @Test
    void bulkUpdateAllowsDifferentDays() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(availabilitySlotRepository.save(any(AvailabilitySlot.class))).thenAnswer(inv -> {
            AvailabilitySlot s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        List<AvailabilitySlotResponse> result = availabilityService.bulkUpdate(1L, List.of(
                request(DayOfWeek.MONDAY, "09:00", "12:00"),
                request(DayOfWeek.TUESDAY, "09:00", "12:00")));

        assertThat(result).hasSize(2);
    }

    // ── addSlot ─────────────────────────────────────────────────────────────

    @Test
    void addSlotSuccess() {
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(availabilitySlotRepository.findByMentorId(1L)).thenReturn(List.of());
        when(availabilitySlotRepository.save(any(AvailabilitySlot.class))).thenAnswer(inv -> {
            AvailabilitySlot s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        AvailabilitySlotResponse result = availabilityService.addSlot(1L,
                request(DayOfWeek.MONDAY, "09:00", "12:00"));

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getDayOfWeek()).isEqualTo("MONDAY");
    }

    @Test
    void addSlotRejectsOverlapWithExisting() {
        AvailabilitySlot existing = slot(1L, DayOfWeek.MONDAY, "09:00", "12:00");
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(availabilitySlotRepository.findByMentorId(1L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> availabilityService.addSlot(1L,
                request(DayOfWeek.MONDAY, "11:00", "14:00")))
                .isInstanceOf(OverlappingSlotException.class);
    }

    @Test
    void addSlotAllowsAdjacentToExisting() {
        AvailabilitySlot existing = slot(1L, DayOfWeek.MONDAY, "09:00", "12:00");
        when(mentorRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(availabilitySlotRepository.findByMentorId(1L)).thenReturn(List.of(existing));
        when(availabilitySlotRepository.save(any(AvailabilitySlot.class))).thenAnswer(inv -> {
            AvailabilitySlot s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        AvailabilitySlotResponse result = availabilityService.addSlot(1L,
                request(DayOfWeek.MONDAY, "12:00", "15:00"));

        assertThat(result).isNotNull();
    }

    @Test
    void addSlotMentorNotFound() {
        when(mentorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.addSlot(99L,
                request(DayOfWeek.MONDAY, "09:00", "12:00")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── removeSlot ──────────────────────────────────────────────────────────

    @Test
    void removeSlotSuccess() {
        when(availabilitySlotRepository.deleteByIdAndMentorId(10L, 1L)).thenReturn(1L);

        availabilityService.removeSlot(1L, 10L);

        verify(availabilitySlotRepository).deleteByIdAndMentorId(10L, 1L);
    }

    @Test
    void removeSlotNotFound() {
        when(availabilitySlotRepository.deleteByIdAndMentorId(99L, 1L)).thenReturn(0L);

        assertThatThrownBy(() -> availabilityService.removeSlot(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
