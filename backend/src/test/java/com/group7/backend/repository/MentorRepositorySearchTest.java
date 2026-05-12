package com.group7.backend.repository;

import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres SQL-filter coverage for {@link MentorRepository#searchByFilters}.
 * Exercises the JPQL paths that the matching service and {@code /api/users/search}
 * funnel through. H2 lacks {@code pg_trgm}, so this test pins to the {@code test}
 * profile (Postgres on localhost:5433).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MentorRepositorySearchTest {

    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AvailabilitySlotRepository availabilitySlotRepository;
    @Autowired private MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;

    @BeforeEach
    void cleanDb() {
        availabilitySlotRepository.deleteAll();
        menteeAvailabilitySlotRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Mentor newMentor(String suffix) {
        Mentor m = new Mentor();
        m.setFirstName("F" + suffix);
        m.setLastName("L" + suffix);
        m.setEmail("m" + suffix + "@example.com");
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        return m;
    }

    private Mentee newMentee(String suffix) {
        Mentee m = new Mentee();
        m.setFirstName("F" + suffix);
        m.setLastName("L" + suffix);
        m.setEmail("me" + suffix + "@example.com");
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        return m;
    }

    // ── Keyword paths across each searchable field ───────────────────────────

    @Test
    void keywordMatches_expertise() {
        Mentor m = newMentor("a"); m.setExpertise("backend java systems");
        mentorRepository.save(m);

        Page<Mentor> p = mentorRepository.searchByFilters(
                "%java%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId).containsExactly(m.getId());
    }

    @Test
    void keywordMatches_field() {
        Mentor m = newMentor("b"); m.setField("Computer Science");
        mentorRepository.save(m);

        Page<Mentor> p = mentorRepository.searchByFilters(
                "%computer%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    @Test
    void keywordMatches_mentoringGoals() {
        Mentor m = newMentor("c"); m.setMentoringGoals("Help students with thesis writing");
        mentorRepository.save(m);

        Page<Mentor> p = mentorRepository.searchByFilters(
                "%thesis%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    @Test
    void keywordMatches_interestsElementCollection() {
        Mentor m = newMentor("d"); m.setInterests(List.of("Machine Learning", "Robotics"));
        mentorRepository.save(m);

        Page<Mentor> p = mentorRepository.searchByFilters(
                "%machine%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    @Test
    void keywordMatches_preferredMenteeSkillsElementCollection() {
        Mentor m = newMentor("e"); m.setPreferredMenteeSkills(List.of("Python", "Kotlin"));
        mentorRepository.save(m);

        Page<Mentor> p = mentorRepository.searchByFilters(
                "%kotlin%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    @Test
    void keywordIsCaseInsensitive() {
        Mentor m = newMentor("f"); m.setExpertise("Backend Java");
        mentorRepository.save(m);

        // Caller normalises to lowercase before passing — JPQL uses LOWER(col) LIKE :keyword.
        Page<Mentor> p = mentorRepository.searchByFilters(
                "%java%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    // ── Multi-value filters (OR semantics within a category) ─────────────────

    @Test
    void interestsFilter_orAcrossValues() {
        Mentor a = newMentor("g1"); a.setInterests(List.of("AI"));
        Mentor b = newMentor("g2"); b.setInterests(List.of("Robotics"));
        Mentor c = newMentor("g3"); c.setInterests(List.of("Music"));
        mentorRepository.saveAll(List.of(a, b, c));

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, List.of("ai", "robotics"), null, null, false, false, null,
                PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId)
                .containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void skillsFilter_orAcrossValues() {
        Mentor a = newMentor("h1"); a.setPreferredMenteeSkills(List.of("Java"));
        Mentor b = newMentor("h2"); b.setPreferredMenteeSkills(List.of("Go"));
        mentorRepository.saveAll(List.of(a, b));

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, null, List.of("java", "go"), null, false, false, null,
                PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId)
                .containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void majorFilter_matchesPreferredMenteeMajorOrField() {
        Mentor a = newMentor("i1"); a.setPreferredMenteeMajor("Computer Science");
        Mentor b = newMentor("i2"); b.setField("Computer Science");
        Mentor c = newMentor("i3"); c.setField("Mathematics");
        mentorRepository.saveAll(List.of(a, b, c));

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, null, null, "computer science", false, false, null,
                PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId)
                .containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    // ── Capacity gate ────────────────────────────────────────────────────────

    @Test
    void requireCapacityTrue_excludesFullMentor() {
        Mentor full = newMentor("j1");
        full.setMaxMenteeCapacity(2); full.setCurrentMenteeCount(2);
        Mentor avail = newMentor("j2");
        avail.setMaxMenteeCapacity(2); avail.setCurrentMenteeCount(1);
        mentorRepository.saveAll(List.of(full, avail));

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, null, null, null, true, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId).containsExactly(avail.getId());
    }

    @Test
    void requireCapacityFalse_includesFullMentor() {
        Mentor full = newMentor("k");
        full.setMaxMenteeCapacity(1); full.setCurrentMenteeCount(1);
        mentorRepository.save(full);

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    @Test
    void capacityBoundary_oneLessThanMaxIsIncluded() {
        Mentor m = newMentor("l");
        m.setMaxMenteeCapacity(3); m.setCurrentMenteeCount(2);
        mentorRepository.save(m);

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, null, null, null, true, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    // ── Slot-overlap gate (requesterMenteeId) ────────────────────────────────

    @Test
    void requesterMenteeId_filtersByOverlap() {
        Mentor m = newMentor("n");
        mentorRepository.save(m);
        AvailabilitySlot mSlot = new AvailabilitySlot();
        mSlot.setMentor(m);
        mSlot.setDayOfWeek(DayOfWeek.MONDAY);
        mSlot.setStartTime(LocalTime.of(9, 0));
        mSlot.setEndTime(LocalTime.of(11, 0));
        availabilitySlotRepository.save(mSlot);

        Mentee me = newMentee("n");
        menteeRepository.save(me);
        MenteeAvailabilitySlot meSlot = new MenteeAvailabilitySlot();
        meSlot.setMentee(me);
        meSlot.setDayOfWeek(DayOfWeek.MONDAY);
        meSlot.setStartTime(LocalTime.of(10, 0));
        meSlot.setEndTime(LocalTime.of(12, 0));
        menteeAvailabilitySlotRepository.save(meSlot);

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, null, null, null, false, false, me.getId(), PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId).containsExactly(m.getId());
    }

    @Test
    void requesterMenteeId_excludesNonOverlapping() {
        Mentor m = newMentor("o");
        mentorRepository.save(m);
        AvailabilitySlot mSlot = new AvailabilitySlot();
        mSlot.setMentor(m);
        mSlot.setDayOfWeek(DayOfWeek.MONDAY);
        mSlot.setStartTime(LocalTime.of(9, 0));
        mSlot.setEndTime(LocalTime.of(10, 0));
        availabilitySlotRepository.save(mSlot);

        Mentee me = newMentee("o");
        menteeRepository.save(me);
        MenteeAvailabilitySlot meSlot = new MenteeAvailabilitySlot();
        meSlot.setMentee(me);
        meSlot.setDayOfWeek(DayOfWeek.MONDAY);
        // Adjacent but non-overlapping: 10:00-11:00 vs mentor 09:00-10:00.
        // Strict < on both inequalities.
        meSlot.setStartTime(LocalTime.of(10, 0));
        meSlot.setEndTime(LocalTime.of(11, 0));
        menteeAvailabilitySlotRepository.save(meSlot);

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, null, null, null, false, false, me.getId(), PageRequest.of(0, 20));
        assertThat(p.getContent()).isEmpty();
    }

    @Test
    void requesterMenteeId_crossDayDoesNotOverlap() {
        Mentor m = newMentor("p");
        mentorRepository.save(m);
        AvailabilitySlot mSlot = new AvailabilitySlot();
        mSlot.setMentor(m);
        mSlot.setDayOfWeek(DayOfWeek.MONDAY);
        mSlot.setStartTime(LocalTime.of(9, 0));
        mSlot.setEndTime(LocalTime.of(11, 0));
        availabilitySlotRepository.save(mSlot);

        Mentee me = newMentee("p");
        menteeRepository.save(me);
        MenteeAvailabilitySlot meSlot = new MenteeAvailabilitySlot();
        meSlot.setMentee(me);
        meSlot.setDayOfWeek(DayOfWeek.TUESDAY);
        meSlot.setStartTime(LocalTime.of(9, 0));
        meSlot.setEndTime(LocalTime.of(11, 0));
        menteeAvailabilitySlotRepository.save(meSlot);

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, null, null, null, false, false, me.getId(), PageRequest.of(0, 20));
        assertThat(p.getContent()).isEmpty();
    }

    // ── Wildcard escape (correctness-required) ───────────────────────────────

    @Test
    void wildcardPercent_escapedAsLiteral() {
        Mentor a = newMentor("q1"); a.setExpertise("abc%def");
        Mentor b = newMentor("q2"); b.setExpertise("abcXdef");
        mentorRepository.saveAll(List.of(a, b));

        // Caller escapes % as |% (ESCAPE '|') so the search treats it as literal.
        Page<Mentor> p = mentorRepository.searchByFilters(
                "%abc|%def%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId).containsExactly(a.getId());
    }

    @Test
    void wildcardUnderscore_escapedAsLiteral() {
        Mentor a = newMentor("r1"); a.setExpertise("abc_def");
        Mentor b = newMentor("r2"); b.setExpertise("abcXdef");
        mentorRepository.saveAll(List.of(a, b));

        Page<Mentor> p = mentorRepository.searchByFilters(
                "%abc|_def%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId).containsExactly(a.getId());
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    void emptyResult_returnsEmptyPage() {
        Page<Mentor> p = mentorRepository.searchByFilters(
                "%nothingmatchesthis%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).isEmpty();
        assertThat(p.getTotalElements()).isZero();
    }

    @Test
    void nullColumns_doNotMatchKeyword() {
        // Mentor with no fields populated — keyword search should not match
        // and not throw NPE on the LIKE evaluation.
        Mentor m = newMentor("s");  // expertise/field/etc. all null
        mentorRepository.save(m);

        Page<Mentor> p = mentorRepository.searchByFilters(
                "%anything%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).isEmpty();
    }

    @Test
    void emptyElementCollection_doesNotMatchInterestFilter() {
        Mentor m = newMentor("t");  // no interests set
        mentorRepository.save(m);

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, List.of("ai"), null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).isEmpty();
    }

    @Test
    void duplicateInterestValues_idempotent() {
        Mentor m = newMentor("u"); m.setInterests(List.of("AI"));
        mentorRepository.save(m);

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, List.of("ai", "ai"), null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId).containsExactly(m.getId());
    }

    @Test
    void combinedFilters_appliedAsAndAcrossCategories() {
        // Capacity AND interest must both match.
        Mentor a = newMentor("v1");
        a.setMaxMenteeCapacity(3); a.setCurrentMenteeCount(0);
        a.setInterests(List.of("AI"));

        Mentor b = newMentor("v2");
        b.setMaxMenteeCapacity(1); b.setCurrentMenteeCount(1);  // full
        b.setInterests(List.of("AI"));

        Mentor c = newMentor("v3");
        c.setMaxMenteeCapacity(3); c.setCurrentMenteeCount(0);
        c.setInterests(List.of("Music"));

        mentorRepository.saveAll(List.of(a, b, c));

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, List.of("ai"), null, null, true, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentor::getId).containsExactly(a.getId());
    }

    // ── SQL-injection probe ─────────────────────────────────────────────────

    @Test
    void sqlInjectionAttempt_treatedAsLiteralSubstring() {
        Mentor m = newMentor("w"); m.setExpertise("backend");
        mentorRepository.save(m);

        long before = mentorRepository.count();
        // Caller would send escaped form. The keyword is matched as a literal
        // — no parser interpretation. The mentors table is not dropped.
        Page<Mentor> p = mentorRepository.searchByFilters(
                "%'; drop table mentors; --%", null, null, null, false, false, null,
                PageRequest.of(0, 20));
        long after = mentorRepository.count();
        assertThat(after).isEqualTo(before);
        assertThat(p.getContent()).isEmpty();
    }

    // ── Pagination ──────────────────────────────────────────────────────────

    @Test
    void pagination_returnsRequestedSliceWithCorrectTotal() {
        for (int i = 0; i < 5; i++) {
            mentorRepository.save(newMentor("x" + i));
        }

        Page<Mentor> page0 = mentorRepository.searchByFilters(
                null, null, null, null, false, false, null, PageRequest.of(0, 2));
        Page<Mentor> page1 = mentorRepository.searchByFilters(
                null, null, null, null, false, false, null, PageRequest.of(1, 2));

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page1.getContent()).hasSize(2);
        // Page 0 and page 1 must be disjoint.
        assertThat(page0.getContent()).noneMatch(m1 ->
                page1.getContent().stream().anyMatch(m2 -> m2.getId().equals(m1.getId())));
    }

    @Test
    void pagination_orderByIdDescIsDeterministic() {
        Mentor a = newMentor("y1");
        Mentor b = newMentor("y2");
        Mentor c = newMentor("y3");
        mentorRepository.saveAll(List.of(a, b, c));

        Page<Mentor> p = mentorRepository.searchByFilters(
                null, null, null, null, false, false, null, PageRequest.of(0, 10));
        // Default ORDER BY m.id DESC — newer first.
        List<Long> ids = p.getContent().stream().map(Mentor::getId).toList();
        assertThat(ids).containsExactly(c.getId(), b.getId(), a.getId());
    }
}
