package com.group7.backend.service.ranking;

import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level coverage of the legacy weighted-score algorithm, extracted
 * from {@code MatchingService} into {@link RuleBasedMentorRanker} as part
 * of #262. These assertions used to live in {@code MatchingServiceTest};
 * they're co-located with the ranker itself so a future swap (e.g., an
 * AI-driven ranker) doesn't drag legacy-algorithm tests into its own test
 * class.
 */
class RuleBasedMentorRankerTest {

    private RuleBasedMentorRanker ranker;
    private Mentor mentor;
    private Mentee mentee;

    @BeforeEach
    void setUp() {
        ranker = new RuleBasedMentorRanker();
        mentor = new Mentor();
        mentor.setMaxMenteeCapacity(3);
        mentor.setCurrentMenteeCount(1);
        mentor.setField("Computer Science");
        mentor.setExpertise("backend engineering Java");
        mentor.setPreferredMenteeMajor("Computer Science");
        mentor.setPreferredMenteeSkills(List.of("Java", "Kotlin"));
        mentor.setInterests(List.of("AI", "Systems"));
        mentor.setMentoringGoals("Help with career and machine learning projects");

        mentee = new Mentee();
        mentee.setMajor("Computer Science");
        mentee.setGoals("career machine learning");
        mentee.setCareerInterest("backend engineering");
        mentee.setInterests(List.of("AI", "Databases"));
        mentee.setSkills(List.of("Java", "Python"));
    }

    // ── Profile scoring (formerly MatchingService.calculateScore) ──────────

    @Test
    void scoreOverlappingInterests() {
        // Mentee has AI and Databases; mentor has AI and Systems → 1 overlap = +3.
        int score = ranker.score(mentor, mentee, List.of(), List.of()).score();
        assertThat(score).isGreaterThanOrEqualTo(3);
    }

    @Test
    void scoreSkillMatch() {
        Mentor m = new Mentor();
        m.setPreferredMenteeSkills(List.of("Java"));
        Mentee me = new Mentee();
        me.setSkills(List.of("Java"));

        assertThat(ranker.score(m, me, List.of(), List.of()).score()).isEqualTo(3);
    }

    @Test
    void scoreMajorMatchesPreferredMenteeMajor() {
        Mentor m = new Mentor();
        m.setPreferredMenteeMajor("Computer Science");
        Mentee me = new Mentee();
        me.setMajor("Computer Science");

        assertThat(ranker.score(m, me, List.of(), List.of()).score()).isEqualTo(5);
    }

    @Test
    void scoreMajorMatchesField() {
        Mentor m = new Mentor();
        m.setField("Computer Science");
        Mentee me = new Mentee();
        me.setMajor("Computer Science");

        assertThat(ranker.score(m, me, List.of(), List.of()).score()).isEqualTo(3);
    }

    @Test
    void scoreGoalsKeywordOverlap() {
        // "machine" and "learning" appear in mentor.mentoringGoals → +2 each.
        Mentor m = new Mentor();
        m.setMentoringGoals("machine learning and career guidance");
        Mentee me = new Mentee();
        me.setGoals("machine learning");

        assertThat(ranker.score(m, me, List.of(), List.of()).score()).isEqualTo(4);
    }

    @Test
    void scoreNullFieldsDoNotCrash() {
        Mentor m = new Mentor();
        Mentee me = new Mentee();

        assertThat(ranker.score(m, me, List.of(), List.of()).score()).isEqualTo(0);
    }

    // ── Availability scoring (formerly calculateAvailabilityScore) ─────────

    @Test
    void availabilityScoreUsesOverlapAndCapsAtTwelve() {
        // 8 hours of overlap → 480 minutes / 30 = 16, capped to 12.
        List<AvailabilitySlot> mentorSlots = List.of(
                mentorSlot(DayOfWeek.MONDAY, "09:00", "18:00"));
        List<MenteeAvailabilitySlot> menteeSlots = List.of(
                menteeSlot(DayOfWeek.MONDAY, "09:00", "17:00"));

        // Use blank entities so profile score is 0; the assertion isolates
        // the availability portion at exactly the cap.
        int score = ranker.score(new Mentor(), new Mentee(), mentorSlots, menteeSlots).score();
        assertThat(score).isEqualTo(12);
    }

    @Test
    void availabilityScoreReturnsZeroWithoutSlots() {
        int score = ranker.score(new Mentor(), new Mentee(), List.of(), List.of()).score();
        assertThat(score).isZero();
    }

    @Test
    void availabilityScoreReturnsZeroWhenNoDayOfWeekOverlap() {
        // Mentor MONDAY, mentee TUESDAY — no overlap regardless of time.
        List<AvailabilitySlot> mentorSlots = List.of(
                mentorSlot(DayOfWeek.MONDAY, "09:00", "10:00"));
        List<MenteeAvailabilitySlot> menteeSlots = List.of(
                menteeSlot(DayOfWeek.TUESDAY, "09:00", "10:00"));

        int score = ranker.score(new Mentor(), new Mentee(), mentorSlots, menteeSlots).score();
        assertThat(score).isZero();
    }

    @Test
    void availabilityScoreReturnsZeroForAdjacentNonOverlappingSlots() {
        // 09:00–10:00 vs 10:00–11:00 — touching but not overlapping (strict <).
        List<AvailabilitySlot> mentorSlots = List.of(
                mentorSlot(DayOfWeek.MONDAY, "09:00", "10:00"));
        List<MenteeAvailabilitySlot> menteeSlots = List.of(
                menteeSlot(DayOfWeek.MONDAY, "10:00", "11:00"));

        int score = ranker.score(new Mentor(), new Mentee(), mentorSlots, menteeSlots).score();
        assertThat(score).isZero();
    }

    // ── Combined scoring contract ──────────────────────────────────────────

    @Test
    void scoreSumsProfileAndAvailabilityComponents() {
        // Profile contribution:
        //   AI overlap                                                     +3
        //   Java skill match (Java in mentor.preferredMenteeSkills)        +3
        //   CS major matches mentor.preferredMenteeMajor                   +5
        //   CS major matches mentor.field                                  +3
        //   Goal words: "career" + "machine" + "learning"  (each > 3 chars)+6
        //   Career-interest words: "backend" + "engineering"               +4
        //                                                          subtotal 24
        // Availability: MONDAY 09:00–11:00 vs MONDAY 09:30–10:30 = 60 min  +2
        // Total expected: 26.
        List<AvailabilitySlot> mentorSlots = List.of(
                mentorSlot(DayOfWeek.MONDAY, "09:00", "11:00"));
        List<MenteeAvailabilitySlot> menteeSlots = List.of(
                menteeSlot(DayOfWeek.MONDAY, "09:30", "10:30"));

        int score = ranker.score(mentor, mentee, mentorSlots, menteeSlots).score();
        assertThat(score).isEqualTo(26);
    }

    @Test
    void rankerIsStateless_consecutiveCallsReturnSameScore() {
        // Sanity check that the ranker doesn't accumulate state between calls.
        int first = ranker.score(mentor, mentee, List.of(), List.of()).score();
        int second = ranker.score(mentor, mentee, List.of(), List.of()).score();
        assertThat(second).isEqualTo(first);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static AvailabilitySlot mentorSlot(DayOfWeek day, String start, String end) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.parse(start));
        slot.setEndTime(LocalTime.parse(end));
        slot.setRecurring(true);
        return slot;
    }

    private static MenteeAvailabilitySlot menteeSlot(DayOfWeek day, String start, String end) {
        MenteeAvailabilitySlot slot = new MenteeAvailabilitySlot();
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.parse(start));
        slot.setEndTime(LocalTime.parse(end));
        slot.setRecurring(true);
        return slot;
    }
}
