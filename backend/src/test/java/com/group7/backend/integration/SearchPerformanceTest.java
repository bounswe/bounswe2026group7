package com.group7.backend.integration;

import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.MatchingService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManagerFactory;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query-budget guard for the matching path (#262). Asserts that {@code
 * MatchingService.getTopMentors} executes within the documented ≤7-query
 * envelope regardless of result-set size, demonstrating the N+1 elimination.
 *
 * <p>Hibernate Statistics counts JPQL queries only (native queries and
 * L1-cache hits are excluded), which matches what we want — the search path
 * is JPQL throughout.
 *
 * <p>Wall-clock perf is intentionally not asserted here; CI variance makes
 * those bounds flaky. The query-count claim is the structural invariant
 * worth pinning.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class SearchPerformanceTest {

    /**
     * Hibernate {@code Statistics.getQueryExecutionCount()} counts JPQL/HQL/
     * native query executions but excludes collection fetches (those have
     * their own counter). For the matching path this comes out to:
     * {@code findById(mentee)} + {@code findRankingCandidates} +
     * {@code findByMentorIdIn} + {@code findByMenteeId} = 4. Tightened
     * from the prior {@code ≤6} budget so any new query — re-introduced N+1
     * fetch, audit log call, etc. — fails the test rather than silently
     * eating headroom.
     */
    private static final int MAX_QUERIES_PER_SEARCH = 4;

    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AvailabilitySlotRepository availabilitySlotRepository;
    @Autowired private MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;
    @Autowired private MatchingService matchingService;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Long menteeId;
    private Long mentorId;

    @BeforeEach
    void seed() {
        availabilitySlotRepository.deleteAll();
        menteeAvailabilitySlotRepository.deleteAll();
        userRepository.deleteAll();

        // 20 mentors with varying interests/skills/slots — large enough that
        // a per-mentor slot fetch (the pre-#262 N+1 shape) would push the
        // count well above 7. Small enough to keep the test fast.
        for (int i = 0; i < 20; i++) {
            Mentor m = new Mentor();
            m.setFirstName("Mentor" + i);
            m.setLastName("L");
            m.setEmail("perf_mentor" + i + "@example.com");
            m.setPasswordHash("x");
            m.setIsEmailVerified(true);
            m.setMaxMenteeCapacity(3);
            m.setCurrentMenteeCount(0);
            m.setExpertise("backend java systems " + i);
            m.setInterests(List.of("AI", "Systems"));
            m.setPreferredMenteeSkills(List.of("Java", "Python"));
            mentorRepository.save(m);
            if (i == 0) {
                mentorId = m.getId();
            }

            AvailabilitySlot slot = new AvailabilitySlot();
            slot.setMentor(m);
            slot.setDayOfWeek(DayOfWeek.MONDAY);
            slot.setStartTime(LocalTime.of(9, 0));
            slot.setEndTime(LocalTime.of(11, 0));
            availabilitySlotRepository.save(slot);
        }

        Mentee me = new Mentee();
        me.setFirstName("Test");
        me.setLastName("Mentee");
        me.setEmail("perf_mentee@example.com");
        me.setPasswordHash("x");
        me.setIsEmailVerified(true);
        me.setInterests(List.of("AI"));
        me.setSkills(List.of("Java"));
        menteeRepository.save(me);
        menteeId = me.getId();

        MenteeAvailabilitySlot meSlot = new MenteeAvailabilitySlot();
        meSlot.setMentee(me);
        meSlot.setDayOfWeek(DayOfWeek.MONDAY);
        meSlot.setStartTime(LocalTime.of(10, 0));
        meSlot.setEndTime(LocalTime.of(12, 0));
        menteeAvailabilitySlotRepository.save(meSlot);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void getTopMentors_completesWithinQueryBudget() {
        Statistics stats = statistics();
        // Warmup: prime the prepared-statement cache and any first-call
        // class-loading so the measured invocation reflects steady state.
        matchingService.getTopMentors(menteeId, null, PageRequest.of(0, 10));

        long baseline = stats.getQueryExecutionCount();
        Page<?> page = matchingService.getTopMentors(menteeId, null, PageRequest.of(0, 10));
        long delta = stats.getQueryExecutionCount() - baseline;

        assertThat(page.getContent()).isNotEmpty();
        assertThat(delta)
                .as("query count over a 20-mentor pool — should not scale with N")
                .isLessThanOrEqualTo(MAX_QUERIES_PER_SEARCH);
    }

    @Test
    void getTopMentors_keywordFilter_completesWithinQueryBudget() {
        Statistics stats = statistics();
        matchingService.getTopMentors(menteeId, "java", PageRequest.of(0, 10));

        long baseline = stats.getQueryExecutionCount();
        matchingService.getTopMentors(menteeId, "java", PageRequest.of(0, 10));
        long delta = stats.getQueryExecutionCount() - baseline;

        assertThat(delta).isLessThanOrEqualTo(MAX_QUERIES_PER_SEARCH);
    }

    @Test
    void getTopMentors_emptyResult_doesNotIncurExtraQueries() {
        Statistics stats = statistics();
        matchingService.getTopMentors(menteeId, "nothingmatches", PageRequest.of(0, 10));

        long baseline = stats.getQueryExecutionCount();
        Page<?> page = matchingService.getTopMentors(menteeId, "nothingmatches", PageRequest.of(0, 10));
        long delta = stats.getQueryExecutionCount() - baseline;

        // Empty-result path skips the slot-batch fetch (early-return on raw.isEmpty),
        // so the count is strictly lower than the populated path.
        assertThat(page.getContent()).isEmpty();
        assertThat(delta).isLessThanOrEqualTo(MAX_QUERIES_PER_SEARCH);
    }

    @Test
    void getCandidateMentees_completesWithinQueryBudget() {
        // Mentee path is filter-only (no scoring), so it doesn't fetch slots.
        // Expected explicit JPQL queries: findById(mentor) + findRankingCandidates
        // = 2. Plus collection-fetch SUBSELECTs (Mentor.interests/skills,
        // Mentee.interests/skills) which don't show up in
        // getQueryExecutionCount.
        Statistics stats = statistics();
        matchingService.getCandidateMentees(mentorId, null, PageRequest.of(0, 10));

        long baseline = stats.getQueryExecutionCount();
        Page<?> page = matchingService.getCandidateMentees(mentorId, null, PageRequest.of(0, 10));
        long delta = stats.getQueryExecutionCount() - baseline;

        assertThat(page.getContent()).isNotEmpty();
        assertThat(delta)
                .as("mentee-side path should issue 2 explicit queries (findById + findRankingCandidates)")
                .isLessThanOrEqualTo(2);
    }
}
