package com.group7.backend.service;

import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.response.MenteeAvailabilitySlotResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.exception.OverlappingSlotException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
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
class MenteeAvailabilityServiceTest {

    @Mock
    private MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;

    @Mock
    private MenteeRepository menteeRepository;

    @InjectMocks
    private MenteeAvailabilityService menteeAvailabilityService;

    private Mentee mentee;

    @BeforeEach
    void setUp() {
        mentee = new Mentee();
        mentee.setId(1L);
    }

    private MenteeAvailabilitySlot slot(Long id, DayOfWeek day, String start, String end) {
        MenteeAvailabilitySlot s = new MenteeAvailabilitySlot();
        s.setId(id);
        s.setMentee(mentee);
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

    @Test
    void getSlotsReturnsSorted() {
        MenteeAvailabilitySlot friday = slot(1L, DayOfWeek.FRIDAY, "14:00", "16:00");
        MenteeAvailabilitySlot monday = slot(2L, DayOfWeek.MONDAY, "09:00", "12:00");

        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(menteeAvailabilitySlotRepository.findByMenteeId(1L)).thenReturn(List.of(friday, monday));

        List<MenteeAvailabilitySlotResponse> result = menteeAvailabilityService.getSlots(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDayOfWeek()).isEqualTo("MONDAY");
        assertThat(result.get(1).getDayOfWeek()).isEqualTo("FRIDAY");
    }

    @Test
    void bulkUpdateReplacesAll() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(menteeAvailabilitySlotRepository.save(any(MenteeAvailabilitySlot.class))).thenAnswer(inv -> {
            MenteeAvailabilitySlot s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        List<MenteeAvailabilitySlotResponse> result = menteeAvailabilityService.bulkUpdate(1L,
                List.of(request(DayOfWeek.MONDAY, "09:00", "12:00")));

        verify(menteeAvailabilitySlotRepository).deleteByMenteeId(1L);
        assertThat(result).hasSize(1);
    }

    @Test
    void bulkUpdateRejectsOverlapsWithinSet() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));

        assertThatThrownBy(() -> menteeAvailabilityService.bulkUpdate(1L, List.of(
                request(DayOfWeek.MONDAY, "09:00", "12:00"),
                request(DayOfWeek.MONDAY, "11:00", "14:00"))))
                .isInstanceOf(OverlappingSlotException.class);
    }

    @Test
    void addSlotSuccess() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(mentee));
        when(menteeAvailabilitySlotRepository.findByMenteeId(1L)).thenReturn(List.of());
        when(menteeAvailabilitySlotRepository.save(any(MenteeAvailabilitySlot.class))).thenAnswer(inv -> {
            MenteeAvailabilitySlot s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        MenteeAvailabilitySlotResponse result = menteeAvailabilityService.addSlot(1L,
                request(DayOfWeek.MONDAY, "09:00", "12:00"));

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getDayOfWeek()).isEqualTo("MONDAY");
    }

    @Test
    void removeSlotNotFound() {
        when(menteeAvailabilitySlotRepository.deleteByIdAndMenteeId(99L, 1L)).thenReturn(0L);

        assertThatThrownBy(() -> menteeAvailabilityService.removeSlot(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
