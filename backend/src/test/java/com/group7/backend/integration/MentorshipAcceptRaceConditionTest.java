package com.group7.backend.integration;

import com.group7.backend.dto.request.AcceptRequestRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.MentorshipRequest;
import com.group7.backend.entity.MentorshipRequestStatus;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import com.group7.backend.service.MentorshipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MentorshipAcceptRaceConditionTest {

    private static final Logger log = LoggerFactory.getLogger(MentorshipAcceptRaceConditionTest.class);

    /** Race 2 is on mentee.activeMentorId, not mentor capacity — any non-zero capacity is sufficient. */
    private static final int RACE2_MENTOR_CAPACITY = 1;

    /** Race tests bypass HTTP login and call the service directly, so the password hash is never read. */
    private static final String UNUSED_PASSWORD_HASH = "not-used-in-race-test";

    @Autowired private MentorshipService mentorshipService;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorshipRepository mentorshipRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private AvailabilitySlotRepository availabilitySlotRepository;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        mentorshipRepository.deleteAll();
        availabilitySlotRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @RepeatedTest(20)
    void race1_capacityNotExceeded_whenTwoAcceptsHitSameMentor() throws Exception {
        Mentor mentor = createMentor(1);
        Mentee mentee1 = createMentee();
        Mentee mentee2 = createMentee();
        MentorshipRequest req1 = createPendingRequest(mentor, mentee1);
        MentorshipRequest req2 = createPendingRequest(mentor, mentee2);

        RaceOutcome outcome = runRace(
                () -> mentorshipService.acceptRequest(mentor.getId(), req1.getId(), acceptDto()),
                () -> mentorshipService.acceptRequest(mentor.getId(), req2.getId(), acceptDto())
        );

        Mentor refreshed = mentorRepository.findById(mentor.getId()).orElseThrow();
        assertThat(refreshed.getCurrentMenteeCount())
                .as("mentor capacity must never be exceeded")
                .isEqualTo(1);
        assertThat(mentorshipRepository.count())
                .as("exactly one mentorship should be created")
                .isEqualTo(1);
        assertThat(outcome.successes()).as("exactly one acceptance should succeed").isEqualTo(1);
        assertThat(outcome.conflicts()).as("exactly one acceptance should conflict").isEqualTo(1);
    }

    @RepeatedTest(20)
    void race2_menteeNeverHasMultipleActiveMentors_whenTwoAcceptsHitSameMentee() throws Exception {
        Mentor mentor1 = createMentor(RACE2_MENTOR_CAPACITY);
        Mentor mentor2 = createMentor(RACE2_MENTOR_CAPACITY);
        Mentee mentee = createMentee();
        MentorshipRequest req1 = createPendingRequest(mentor1, mentee);
        MentorshipRequest req2 = createPendingRequest(mentor2, mentee);

        RaceOutcome outcome = runRace(
                () -> mentorshipService.acceptRequest(mentor1.getId(), req1.getId(), acceptDto()),
                () -> mentorshipService.acceptRequest(mentor2.getId(), req2.getId(), acceptDto())
        );

        Mentee refreshed = menteeRepository.findById(mentee.getId()).orElseThrow();
        assertThat(refreshed.getActiveMentorId())
                .as("mentee must have exactly one active mentor (req 1.1.1.1.9)")
                .isNotNull();
        assertThat(refreshed.getActiveMentorId())
                .isIn(mentor1.getId(), mentor2.getId());
        assertThat(mentorshipRepository.count())
                .as("exactly one mentorship should be created")
                .isEqualTo(1);
        assertThat(outcome.successes()).as("exactly one acceptance should succeed").isEqualTo(1);
        assertThat(outcome.conflicts()).as("exactly one acceptance should conflict").isEqualTo(1);
    }

    // ── Helpers ─────────────────────────────────────────────

    private RaceOutcome runRace(Runnable task1, Runnable task2) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        Callable<Void> wrap1 = wrap(start, task1, successes, conflicts);
        Callable<Void> wrap2 = wrap(start, task2, successes, conflicts);

        try {
            Future<Void> f1 = pool.submit(wrap1);
            Future<Void> f2 = pool.submit(wrap2);
            start.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }

        return new RaceOutcome(successes.get(), conflicts.get());
    }

    private static Callable<Void> wrap(CountDownLatch start,
                                       Runnable task,
                                       AtomicInteger successes,
                                       AtomicInteger conflicts) {
        return () -> {
            start.await();
            try {
                task.run();
                successes.incrementAndGet();
            } catch (ConcurrencyFailureException | MentorshipRequestException e) {
                // Expected race outcome — log at DEBUG so a regression to a different
                // exception type is diagnosable from test logs.
                log.debug("Race conflict (expected): {} — {}",
                        e.getClass().getSimpleName(), e.getMessage());
                conflicts.incrementAndGet();
            }
            return null;
        };
    }

    private Mentor createMentor(int capacity) {
        Mentor mentor = new Mentor();
        mentor.setFirstName("Race");
        mentor.setLastName("Mentor");
        mentor.setEmail("rc_mentor_" + UUID.randomUUID() + "@test.com");
        mentor.setPasswordHash(UNUSED_PASSWORD_HASH);
        mentor.setIsEmailVerified(true);
        mentor.setMaxMenteeCapacity(capacity);
        mentor.setCurrentMenteeCount(0);
        return mentorRepository.save(mentor);
    }

    private Mentee createMentee() {
        Mentee mentee = new Mentee();
        mentee.setFirstName("Race");
        mentee.setLastName("Mentee");
        mentee.setEmail("rc_mentee_" + UUID.randomUUID() + "@test.com");
        mentee.setPasswordHash(UNUSED_PASSWORD_HASH);
        mentee.setIsEmailVerified(true);
        mentee.setProfileVisibility(true);
        mentee.setCancelCount(0);
        return menteeRepository.save(mentee);
    }

    private MentorshipRequest createPendingRequest(Mentor mentor, Mentee mentee) {
        MentorshipRequest request = new MentorshipRequest();
        request.setMentor(mentor);
        request.setMentee(mentee);
        request.setStatus(MentorshipRequestStatus.PENDING);
        request.setMessage("race-test request");
        return mentorshipRequestRepository.save(request);
    }

    private static AcceptRequestRequest acceptDto() {
        AcceptRequestRequest dto = new AcceptRequestRequest();
        dto.setDuration(3);
        return dto;
    }

    private record RaceOutcome(int successes, int conflicts) {
    }
}
