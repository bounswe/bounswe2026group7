package com.group7.backend.service;

import com.group7.backend.entity.AvailabilityOverride;
import com.group7.backend.entity.AvailabilityOverrideKind;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AvailabilityOverrideRepository;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MentorRepository;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus;
import net.fortuna.ical4j.model.property.immutable.ImmutableTransp;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.validate.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Builds an RFC 5545 (iCalendar) export of a mentor's published availability.
 *
 * <h2>What gets exported</h2>
 * <ul>
 *   <li>Each weekly-recurring {@link AvailabilitySlot} → one {@code VEVENT}
 *       with {@code RRULE:FREQ=WEEKLY} and {@code TRANSP:TRANSPARENT} (the
 *       slot is informational, not blocking the consumer's own calendar).</li>
 *   <li>Each {@link AvailabilityOverrideKind#AVAILABLE} override → one
 *       {@code VEVENT} (no RRULE), {@code TRANSP:TRANSPARENT}.</li>
 *   <li>Each {@link AvailabilityOverrideKind#UNAVAILABLE} override → one
 *       {@code VEVENT}, {@code TRANSP:OPAQUE} (does block the consumer's
 *       calendar — "I'm taken at this time").</li>
 * </ul>
 *
 * <h2>Time-zone strategy</h2>
 * Recurring slots carry a {@code DayOfWeek} + {@code LocalTime} (no date),
 * so {@code DTSTART} is anchored to the first matching weekday on/after a
 * fixed epoch ({@link #RRULE_EPOCH}). This keeps re-exports deterministic
 * regardless of when the request runs. {@code TZID=Europe/Istanbul} is used
 * so consumers see correct local times; the bundled VTIMEZONE block carries
 * the offset history so calendars without an external TZ database render
 * correctly.
 *
 * <p>Overrides already carry an absolute {@code OffsetDateTime}, so their
 * {@code DTSTART}/{@code DTEND} are emitted as UTC instants.
 *
 * <h2>Stable UIDs</h2>
 * UIDs are derived from the source row's id ({@code availability-slot-{id}}
 * or {@code availability-override-{id}}) so re-exports produce identical
 * UIDs and calendar apps deduplicate cleanly when subscribing to the
 * publication URL.
 */
@Service
public class CalendarExportService {

    /** Fixed anchor week for weekly-recurring DTSTART (a Monday). */
    private static final LocalDate RRULE_EPOCH = LocalDate.of(2024, 1, 1);

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    private static final String UID_DOMAIN = "@bounswe2026group7";

    private static final String PROD_ID =
            "-//Group7 Bounswe//Mentor Availability Export 1.0//EN";

    private final MentorRepository mentorRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final AvailabilityOverrideRepository overrideRepository;

    public CalendarExportService(MentorRepository mentorRepository,
                                 AvailabilitySlotRepository slotRepository,
                                 AvailabilityOverrideRepository overrideRepository) {
        this.mentorRepository = mentorRepository;
        this.slotRepository = slotRepository;
        this.overrideRepository = overrideRepository;
    }

    /**
     * Builds the ICS document and returns it as UTF-8 bytes ready for the
     * controller to set as the response body. The structure is validated
     * against RFC 5545 via ical4j's built-in validator before bytes leave;
     * a violation throws {@link IllegalStateException} so a misshaped
     * publication never reaches a calendar app.
     *
     * @throws ResourceNotFoundException if the mentor does not exist
     */
    @Transactional(readOnly = true)
    public byte[] exportMentorAvailability(Long mentorId) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));

        List<AvailabilitySlot> slots = slotRepository.findByMentorId(mentorId);
        List<AvailabilityOverride> overrides =
                overrideRepository.findByMentorIdOrderByStartAtAscIdAsc(mentorId);

        Calendar calendar = new Calendar();
        calendar.add(new ProdId(PROD_ID));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        // METHOD:PUBLISH is intentionally omitted: ical4j's strict validator
        // enforces RFC 5545's "PUBLISH requires ORGANIZER on every VEVENT"
        // rule, but we have no meaningful ORGANIZER value to attach (the
        // mentor isn't an email-routable invitee — they're the schedule's
        // subject). Without METHOD the calendar is treated as a plain
        // publication, which is exactly what we want.

        // Bundle the VTIMEZONE so consumers without the IANA TZ database
        // (some Outlook installs, generic ICS importers) render correctly.
        TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
        VTimeZone tz = registry.getTimeZone(DEFAULT_ZONE.getId()).getVTimeZone();
        calendar.add(tz);

        Instant now = Instant.now();
        String mentorName = displayName(mentor);

        for (AvailabilitySlot slot : slots) {
            calendar.add(buildRecurringEvent(slot, mentorName, now));
        }
        for (AvailabilityOverride override : overrides) {
            calendar.add(buildOverrideEvent(override, mentorName, now));
        }

        ValidationResult validation = calendar.validate();
        if (validation.hasErrors()) {
            throw new IllegalStateException(
                    "Generated iCalendar failed RFC 5545 validation: " + validation);
        }

        return calendar.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static VEvent buildRecurringEvent(AvailabilitySlot slot,
                                              String mentorName,
                                              Instant dtstamp) {
        // Anchor DTSTART to the first matching DayOfWeek on/after the fixed
        // epoch; RRULE:FREQ=WEEKLY then expands forward indefinitely. We
        // emit DTSTART/DTEND as floating LocalDateTime values + a TZID
        // parameter so the wall-clock time is preserved across DST changes
        // in the consumer's locale (RFC 5545 §3.3.5).
        LocalDate firstOccurrence = RRULE_EPOCH.with(
                TemporalAdjusters.nextOrSame(slot.getDayOfWeek()));
        LocalDateTime start = firstOccurrence.atTime(slot.getStartTime());
        LocalDateTime end = firstOccurrence.atTime(slot.getEndTime());

        DtStart<LocalDateTime> dtStart = new DtStart<>(start);
        dtStart.add(new TzId(DEFAULT_ZONE.getId()));
        DtEnd<LocalDateTime> dtEnd = new DtEnd<>(end);
        dtEnd.add(new TzId(DEFAULT_ZONE.getId()));

        // false: skip ical4j's auto-population of DTSTAMP/UID. We add both
        // explicitly with stable values so re-exports produce identical
        // output for calendar-app deduplication.
        VEvent event = new VEvent(false);
        event.add(new Uid("availability-slot-" + slot.getId() + UID_DOMAIN));
        event.add(new DtStamp(dtstamp));
        event.add(dtStart);
        event.add(dtEnd);
        event.add(new Summary("Available — " + mentorName));
        event.add(ImmutableStatus.VEVENT_CONFIRMED);
        event.add(ImmutableTransp.TRANSPARENT);
        event.add(new RRule<>("FREQ=WEEKLY"));
        return event;
    }

    private static VEvent buildOverrideEvent(AvailabilityOverride override,
                                             String mentorName,
                                             Instant dtstamp) {
        boolean available = override.getKind() == AvailabilityOverrideKind.AVAILABLE;
        String summaryPrefix = available ? "Available — " : "Unavailable — ";
        // AVAILABLE additions are informational (TRANSPARENT); UNAVAILABLE
        // blocks should overlay on a consumer's calendar as busy (OPAQUE).
        Transp transp = available ? ImmutableTransp.TRANSPARENT : ImmutableTransp.OPAQUE;

        // false: skip ical4j's auto-population of DTSTAMP/UID. We add both
        // explicitly with stable values so re-exports produce identical
        // output for calendar-app deduplication.
        VEvent event = new VEvent(false);
        event.add(new Uid("availability-override-" + override.getId() + UID_DOMAIN));
        event.add(new DtStamp(dtstamp));
        event.add(new DtStart<>(override.getStartAt().toInstant()));
        event.add(new DtEnd<>(override.getEndAt().toInstant()));
        event.add(new Summary(summaryPrefix + mentorName));
        event.add(ImmutableStatus.VEVENT_CONFIRMED);
        event.add(transp);
        return event;
    }

    private static String displayName(Mentor mentor) {
        String first = mentor.getFirstName() == null ? "" : mentor.getFirstName();
        String last = mentor.getLastName() == null ? "" : mentor.getLastName();
        String joined = (first + " " + last).trim();
        return joined.isEmpty() ? "Mentor" : joined;
    }
}
