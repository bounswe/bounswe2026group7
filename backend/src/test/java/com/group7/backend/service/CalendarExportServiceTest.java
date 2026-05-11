package com.group7.backend.service;

import com.group7.backend.entity.AvailabilityOverride;
import com.group7.backend.entity.AvailabilityOverrideKind;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AvailabilityOverrideRepository;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MentorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;

/**
 * Unit-level coverage of {@link CalendarExportService}. Each test parses
 * the produced ICS bytes via ical4j's own {@link CalendarBuilder} and
 * inspects the resulting model — that gives both syntactic validation and
 * structural assertions in one round trip.
 */
@ExtendWith(MockitoExtension.class)
class CalendarExportServiceTest {

    private static final Long MENTOR_ID = 100L;

    @Mock private MentorRepository mentorRepository;
    @Mock private AvailabilitySlotRepository slotRepository;
    @Mock private AvailabilityOverrideRepository overrideRepository;

    private CalendarExportService service;
    private Mentor mentor;

    @BeforeEach
    void setUp() {
        service = new CalendarExportService(
                mentorRepository, slotRepository, overrideRepository);
        mentor = new Mentor();
        mentor.setId(MENTOR_ID);
        mentor.setFirstName("Mira");
        mentor.setLastName("Mentor");
    }

    @Test
    void exportThrows404WhenMentorMissing() {
        when(mentorRepository.findById(MENTOR_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.exportMentorAvailability(MENTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void emptyMentor_emitsValidVCalendarWithNoEvents() throws Exception {
        stubMentor();
        when(slotRepository.findByMentorId(MENTOR_ID)).thenReturn(List.of());
        when(overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(MENTOR_ID))
                .thenReturn(List.of());

        Calendar parsed = parse(service.exportMentorAvailability(MENTOR_ID));

        assertThat(parsed.<VEvent>getComponents("VEVENT")).isEmpty();
        // VTIMEZONE is always emitted so consumers without an external TZ
        // database render correctly.
        assertThat(parsed.<VTimeZone>getComponents("VTIMEZONE")).hasSize(1);
    }

    @Test
    void recurringSlot_emitsVEventWithRruleWeeklyAndTransparent() throws Exception {
        stubMentor();
        AvailabilitySlot mondayMorning = slot(1L, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0));
        when(slotRepository.findByMentorId(MENTOR_ID)).thenReturn(List.of(mondayMorning));
        when(overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(MENTOR_ID))
                .thenReturn(List.of());

        Calendar parsed = parse(service.exportMentorAvailability(MENTOR_ID));

        List<VEvent> events = parsed.getComponents("VEVENT");
        assertThat(events).hasSize(1);
        VEvent event = events.get(0);
        assertThat(event.getProperty("UID").orElseThrow().getValue())
                .isEqualTo("availability-slot-1@bounswe2026group7");
        assertThat(event.getProperty("RRULE").orElseThrow().getValue())
                .contains("FREQ=WEEKLY");
        assertThat(event.getProperty("TRANSP").orElseThrow().getValue())
                .isEqualTo("TRANSPARENT");
        assertThat(event.getProperty("STATUS").orElseThrow().getValue())
                .isEqualTo("CONFIRMED");
        assertThat(event.getProperty("SUMMARY").orElseThrow().getValue())
                .isEqualTo("Available — Mira Mentor");
        // DTSTART must be anchored to a Monday since slot.dayOfWeek = MONDAY.
        // The fixed epoch (2024-01-01) is itself a Monday, so DTSTART falls
        // exactly on that date.
        assertThat(event.getProperty("DTSTART").orElseThrow().getValue())
                .isEqualTo("20240101T090000");
    }

    @Test
    void availableOverride_emitsVEventWithoutRruleAndTransparent() throws Exception {
        stubMentor();
        when(slotRepository.findByMentorId(MENTOR_ID)).thenReturn(List.of());
        AvailabilityOverride extra = override(7L, AvailabilityOverrideKind.AVAILABLE,
                "2026-06-15T09:00:00Z", "2026-06-15T12:00:00Z");
        when(overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(MENTOR_ID))
                .thenReturn(List.of(extra));

        Calendar parsed = parse(service.exportMentorAvailability(MENTOR_ID));

        List<VEvent> events = parsed.getComponents("VEVENT");
        assertThat(events).hasSize(1);
        VEvent event = events.get(0);
        assertThat(event.getProperty("UID").orElseThrow().getValue())
                .isEqualTo("availability-override-7@bounswe2026group7");
        assertThat(event.getProperty("RRULE")).isEmpty();
        assertThat(event.getProperty("TRANSP").orElseThrow().getValue())
                .isEqualTo("TRANSPARENT");
        assertThat(event.getProperty("SUMMARY").orElseThrow().getValue())
                .isEqualTo("Available — Mira Mentor");
    }

    @Test
    void unavailableOverride_emitsVEventWithOpaqueAndUnavailableSummary() throws Exception {
        stubMentor();
        when(slotRepository.findByMentorId(MENTOR_ID)).thenReturn(List.of());
        AvailabilityOverride vacation = override(8L, AvailabilityOverrideKind.UNAVAILABLE,
                "2026-06-10T00:00:00Z", "2026-06-20T00:00:00Z");
        when(overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(MENTOR_ID))
                .thenReturn(List.of(vacation));

        Calendar parsed = parse(service.exportMentorAvailability(MENTOR_ID));

        List<VEvent> events = parsed.getComponents("VEVENT");
        assertThat(events).hasSize(1);
        VEvent event = events.get(0);
        // OPAQUE so consumers' personal calendars render the block as busy.
        assertThat(event.getProperty("TRANSP").orElseThrow().getValue())
                .isEqualTo("OPAQUE");
        assertThat(event.getProperty("SUMMARY").orElseThrow().getValue())
                .isEqualTo("Unavailable — Mira Mentor");
    }

    @Test
    void mixedSlotsAndOverrides_emitOneVEventEach() throws Exception {
        stubMentor();
        when(slotRepository.findByMentorId(MENTOR_ID))
                .thenReturn(List.of(
                        slot(1L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        slot(2L, DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(16, 0))));
        when(overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(MENTOR_ID))
                .thenReturn(List.of(
                        override(7L, AvailabilityOverrideKind.AVAILABLE,
                                "2026-06-15T09:00:00Z", "2026-06-15T12:00:00Z"),
                        override(8L, AvailabilityOverrideKind.UNAVAILABLE,
                                "2026-06-20T00:00:00Z", "2026-06-25T00:00:00Z")));

        Calendar parsed = parse(service.exportMentorAvailability(MENTOR_ID));

        List<VEvent> events = parsed.getComponents("VEVENT");
        assertThat(events).hasSize(4);
    }

    @Test
    void uidsAreStableAcrossExports() throws Exception {
        stubMentor();
        AvailabilitySlot slot = slot(42L, DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(11, 0));
        when(slotRepository.findByMentorId(MENTOR_ID)).thenReturn(List.of(slot));
        when(overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(MENTOR_ID))
                .thenReturn(List.of());

        String first = new String(service.exportMentorAvailability(MENTOR_ID), StandardCharsets.UTF_8);
        String second = new String(service.exportMentorAvailability(MENTOR_ID), StandardCharsets.UTF_8);

        // UID is the deduplication key for calendar subscribers; re-exports
        // must produce the same UID for the same source row so consumers
        // don't see duplicates after refresh.
        assertThat(first).contains("UID:availability-slot-42@bounswe2026group7");
        assertThat(second).contains("UID:availability-slot-42@bounswe2026group7");
    }

    @Test
    void mentorWithMissingName_fallsBackToGenericLabel() throws Exception {
        Mentor anonymous = new Mentor();
        anonymous.setId(MENTOR_ID);
        // No first/last name set → null/null.
        when(mentorRepository.findById(MENTOR_ID)).thenReturn(Optional.of(anonymous));
        when(slotRepository.findByMentorId(MENTOR_ID))
                .thenReturn(List.of(slot(1L, DayOfWeek.MONDAY,
                        LocalTime.of(9, 0), LocalTime.of(10, 0))));
        when(overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(MENTOR_ID))
                .thenReturn(List.of());

        Calendar parsed = parse(service.exportMentorAvailability(MENTOR_ID));

        VEvent event = parsed.<VEvent>getComponents("VEVENT").get(0);
        assertThat(event.getProperty("SUMMARY").orElseThrow().getValue())
                .isEqualTo("Available — Mentor");
    }

    @Test
    void calendarHeaderCarriesProductIdVersionAndCalscale() throws Exception {
        stubMentor();
        when(slotRepository.findByMentorId(MENTOR_ID)).thenReturn(List.of());
        when(overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(MENTOR_ID))
                .thenReturn(List.of());

        String body = new String(service.exportMentorAvailability(MENTOR_ID), StandardCharsets.UTF_8);

        assertThat(body)
                .startsWith("BEGIN:VCALENDAR")
                .contains("PRODID:-//Group7 Bounswe//Mentor Availability Export 1.0//EN")
                .contains("VERSION:2.0")
                .contains("CALSCALE:GREGORIAN")
                .endsWith("END:VCALENDAR\r\n");
        // METHOD is deliberately absent — see CalendarExportService comment
        // about RFC 5545's METHOD:PUBLISH/ORGANIZER coupling.
        assertThat(body).doesNotContain("METHOD:");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void stubMentor() {
        when(mentorRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
    }

    private static Calendar parse(byte[] icsBytes) throws Exception {
        return new CalendarBuilder().build(new ByteArrayInputStream(icsBytes));
    }

    private static AvailabilitySlot slot(Long id, DayOfWeek day, LocalTime start, LocalTime end) {
        AvailabilitySlot s = new AvailabilitySlot();
        s.setId(id);
        s.setDayOfWeek(day);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setRecurring(true);
        return s;
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
