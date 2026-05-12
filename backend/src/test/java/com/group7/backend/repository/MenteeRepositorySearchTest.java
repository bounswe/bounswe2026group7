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
 * Real-Postgres SQL-filter coverage for {@link MenteeRepository#searchByFilters}.
 * Symmetric to {@link MentorRepositorySearchTest}; this class focuses on the
 * filters that differ from the mentor side — chiefly {@code requireUnattached}
 * which has no mentor-side equivalent.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MenteeRepositorySearchTest {

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

    private Mentee newMentee(String suffix) {
        Mentee m = new Mentee();
        m.setFirstName("F" + suffix);
        m.setLastName("L" + suffix);
        m.setEmail("me" + suffix + "@example.com");
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        return m;
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

    @Test
    void keywordMatches_goalsAndMajor() {
        Mentee a = newMentee("a"); a.setGoals("learn machine learning");
        Mentee b = newMentee("b"); b.setMajor("Computer Science");
        menteeRepository.saveAll(List.of(a, b));

        Page<Mentee> ml = menteeRepository.searchByFilters(
                "%machine%", null, null, null, false, false, null, PageRequest.of(0, 20));
        Page<Mentee> cs = menteeRepository.searchByFilters(
                "%computer%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(ml.getContent()).extracting(Mentee::getId).containsExactly(a.getId());
        assertThat(cs.getContent()).extracting(Mentee::getId).containsExactly(b.getId());
    }

    @Test
    void keywordMatches_interestsElementCollection() {
        Mentee m = newMentee("c"); m.setInterests(List.of("Robotics", "Drones"));
        menteeRepository.save(m);

        Page<Mentee> p = menteeRepository.searchByFilters(
                "%drone%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    @Test
    void keywordMatches_skillsElementCollection() {
        Mentee m = newMentee("d"); m.setSkills(List.of("Rust", "Haskell"));
        menteeRepository.save(m);

        Page<Mentee> p = menteeRepository.searchByFilters(
                "%haskell%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    @Test
    void interestsFilter_orAcrossValues() {
        Mentee a = newMentee("e1"); a.setInterests(List.of("AI"));
        Mentee b = newMentee("e2"); b.setInterests(List.of("Robotics"));
        Mentee c = newMentee("e3"); c.setInterests(List.of("Music"));
        menteeRepository.saveAll(List.of(a, b, c));

        Page<Mentee> p = menteeRepository.searchByFilters(
                null, List.of("ai", "robotics"), null, null, false, false, null,
                PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentee::getId)
                .containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    // ── requireUnattached: the filter that distinguishes the mentee side ─────

    @Test
    void requireUnattachedTrue_excludesAttachedMentees() {
        Mentor mentor = newMentor("f");
        mentorRepository.save(mentor);

        Mentee attached = newMentee("g1");
        attached.setActiveMentorId(mentor.getId());
        Mentee unattached = newMentee("g2");
        menteeRepository.saveAll(List.of(attached, unattached));

        Page<Mentee> p = menteeRepository.searchByFilters(
                null, null, null, null, true, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentee::getId)
                .containsExactly(unattached.getId());
    }

    @Test
    void requireUnattachedFalse_includesAttachedMentees() {
        Mentor mentor = newMentor("h");
        mentorRepository.save(mentor);

        Mentee attached = newMentee("i");
        attached.setActiveMentorId(mentor.getId());
        menteeRepository.save(attached);

        Page<Mentee> p = menteeRepository.searchByFilters(
                null, null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).hasSize(1);
    }

    // ── Slot overlap (requesterMentorId) ────────────────────────────────────

    @Test
    void requesterMentorId_filtersByOverlap() {
        Mentor mentor = newMentor("j");
        mentorRepository.save(mentor);
        AvailabilitySlot mSlot = new AvailabilitySlot();
        mSlot.setMentor(mentor);
        mSlot.setDayOfWeek(DayOfWeek.WEDNESDAY);
        mSlot.setStartTime(LocalTime.of(14, 0));
        mSlot.setEndTime(LocalTime.of(16, 0));
        availabilitySlotRepository.save(mSlot);

        Mentee me = newMentee("j");
        menteeRepository.save(me);
        MenteeAvailabilitySlot meSlot = new MenteeAvailabilitySlot();
        meSlot.setMentee(me);
        meSlot.setDayOfWeek(DayOfWeek.WEDNESDAY);
        meSlot.setStartTime(LocalTime.of(15, 0));
        meSlot.setEndTime(LocalTime.of(17, 0));
        menteeAvailabilitySlotRepository.save(meSlot);

        Page<Mentee> p = menteeRepository.searchByFilters(
                null, null, null, null, false, false, mentor.getId(),
                PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentee::getId).containsExactly(me.getId());
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Test
    void emptyResult_returnsEmptyPage() {
        Page<Mentee> p = menteeRepository.searchByFilters(
                "%nothingmatches%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).isEmpty();
    }

    @Test
    void wildcardEscape_treatedAsLiteral() {
        Mentee a = newMentee("k1"); a.setGoals("100% throughput");
        Mentee b = newMentee("k2"); b.setGoals("100X throughput");
        menteeRepository.saveAll(List.of(a, b));

        Page<Mentee> p = menteeRepository.searchByFilters(
                "%100|%%", null, null, null, false, false, null, PageRequest.of(0, 20));
        assertThat(p.getContent()).extracting(Mentee::getId).containsExactly(a.getId());
    }

    @Test
    void sqlInjectionAttempt_treatedAsLiteralSubstring() {
        Mentee m = newMentee("l"); m.setGoals("hello");
        menteeRepository.save(m);

        long before = menteeRepository.count();
        Page<Mentee> p = menteeRepository.searchByFilters(
                "%'; drop table mentees; --%", null, null, null, false, false, null,
                PageRequest.of(0, 20));
        long after = menteeRepository.count();
        assertThat(after).isEqualTo(before);
        assertThat(p.getContent()).isEmpty();
    }
}
