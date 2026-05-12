package com.group7.backend.service;

import com.group7.backend.dto.response.MenteeCandidateResponse;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.MatchingNotAllowedException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.service.explanation.MatchExplanationService;
import com.group7.backend.service.ranking.MentorRanker;
import com.group7.backend.service.ranking.MentorScoringPipeline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mentor/mentee matching service. The data-loading layer is fully SQL-side
 * (#262); this service orchestrates the search → batch-slot-fetch → score →
 * paginate pipeline.
 *
 * <p>Read-only after #273. The match-found notification was previously
 * fired from this service on every page-0 browse; that side effect moved to
 * {@code MatchNotificationProcessor}, which is invoked daily by
 * {@code MatchNotificationScheduler} only when the user's top match has
 * actually changed since the last notification. Pagination, page refreshes,
 * and deep-links no longer publish anything.
 *
 * <h2>Query budget</h2>
 * Four JPQL/HQL queries per matching call regardless of result size:
 * <ol>
 *   <li>Load the mentee/mentor (via {@code findById}).</li>
 *   <li>Candidate query — {@code findRankingCandidates} (List return, no count).</li>
 *   <li>Mentor-side slot batch — {@code findByMentorIdIn} for the page.</li>
 *   <li>Mentee-side slots — {@code findByMenteeId} for the requesting mentee.</li>
 * </ol>
 * Plus up to 2 collection-fetch SQL statements from {@code @Fetch(SUBSELECT)}
 * on {@code Mentor.interests} and {@code Mentor.preferredMenteeSkills} when
 * the ranker reads them. Hibernate's {@code getQueryExecutionCount()} counts
 * the explicit four; collection fetches accrue under
 * {@code getCollectionFetchCount()} separately. {@code SearchPerformanceTest}
 * pins the explicit-query count at ≤ 4.
 *
 * <h2>Almost-global ranking</h2>
 * Pagination over a scored set is approximate: the SQL layer fetches a
 * fixed top-200 candidates ordered by {@code id DESC} (the deterministic
 * default — no semantic signal), the ranker scores them in memory, and the
 * paginated page is sliced from the in-memory ranking. For filtered pools
 * ≤ 200 (typical for keyword searches), this is exact global ranking. For
 * broader pools (no-filter mentee browsing 5000+ mentors), the user sees
 * the top of the most-recent 200; this is acceptable for a recommendation
 * surface where mentees aren't paginating to mentor #500 by score. The
 * unpaginated {@link #getTopMentorsList} variant uses the same 200 cap for
 * consistency.
 */
@Service
public class MatchingService {

    /**
     * Default oversample window if {@code app.matching.ranking-window} is
     * absent. Pages beyond the window return empty content; the frontend
     * should treat that as "end of results".
     */
    private static final int DEFAULT_RANKING_WINDOW = 200;

    private final MenteeRepository menteeRepository;
    private final MentorRepository mentorRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;
    private final MentorScoringPipeline scoringPipeline;
    private final MatchExplanationService explanationService;
    private final TransactionTemplate readOnlyTx;
    private final int rankingWindow;

    public MatchingService(MenteeRepository menteeRepository,
                           MentorRepository mentorRepository,
                           AvailabilitySlotRepository availabilitySlotRepository,
                           MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository,
                           MentorScoringPipeline scoringPipeline,
                           MatchExplanationService explanationService,
                           PlatformTransactionManager transactionManager,
                           @Value("${app.matching.ranking-window:" + DEFAULT_RANKING_WINDOW + "}")
                           int rankingWindow) {
        this.menteeRepository = menteeRepository;
        this.mentorRepository = mentorRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.menteeAvailabilitySlotRepository = menteeAvailabilitySlotRepository;
        this.scoringPipeline = scoringPipeline;
        this.explanationService = explanationService;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.rankingWindow = rankingWindow;
    }

    /**
     * Detachable mentee data the LLM-prose attach step needs after the JPA
     * session closes. Currently only id + goals are read by the prompt
     * builder; the record is the natural extension point if a future
     * prompt revision needs more fields.
     */
    private record MenteeSnapshot(Long id, String goals) {
        static MenteeSnapshot of(Mentee mentee) {
            return new MenteeSnapshot(mentee.getId(), mentee.getGoals());
        }
        Mentee toDetachedMentee() {
            Mentee m = new Mentee();
            m.setId(id);
            m.setGoals(goals);
            return m;
        }
    }

    public Page<MentorMatchResponse> getTopMentors(Long menteeId, String keyword, Pageable pageable) {
        return getTopMentors(menteeId, keyword, null, pageable);
    }

    /**
     * Two-phase: (1) inside a read-only transaction, load + rank + slice;
     * (2) outside the transaction, attach LLM prose to the page content.
     * Splitting the LLM call out releases the JDBC connection before the
     * (potentially multi-second) OpenAI request — without this, every
     * matching call holds a Postgres connection for the full prose latency.
     */
    public Page<MentorMatchResponse> getTopMentors(Long menteeId, String keyword,
                                                   Double maxDistanceKm, Pageable pageable) {
        var loaded = readOnlyTx.execute(status -> {
            Mentee mentee = loadEligibleMentee(menteeId);
            List<MentorMatchResponse> ranked = rankMentorsFor(mentee, keyword, maxDistanceKm);
            Page<MentorMatchResponse> page = slicePage(ranked, pageable);
            return new LoadedPage(page, MenteeSnapshot.of(mentee));
        });
        // Prose-attach runs without holding a JDBC connection so a slow
        // OpenAI round-trip can't park Hikari slots. Never throws — the
        // service degrades to null prose on any failure.
        explanationService.attach(loaded.page().getContent(), loaded.snapshot().toDetachedMentee());
        return loaded.page();
    }

    /** Pair returned from the transactional load step. */
    private record LoadedPage(Page<MentorMatchResponse> page, MenteeSnapshot snapshot) {}

    private Mentee loadEligibleMentee(Long menteeId) {
        Mentee mentee = menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));
        if (mentee.getActiveMentorId() != null) {
            throw new MatchingNotAllowedException("You already have an active mentor");
        }
        return mentee;
    }

    public List<MentorMatchResponse> getTopMentorsList(Long menteeId, String keyword) {
        record Loaded(List<MentorMatchResponse> mentors, MenteeSnapshot snapshot) {}
        Loaded loaded = readOnlyTx.execute(status -> {
            Mentee mentee = loadEligibleMentee(menteeId);
            return new Loaded(rankMentorsFor(mentee, keyword, null), MenteeSnapshot.of(mentee));
        });
        explanationService.attach(loaded.mentors(), loaded.snapshot().toDetachedMentee());
        return loaded.mentors();
    }

    @Transactional(readOnly = true)
    public Page<MenteeCandidateResponse> getCandidateMentees(Long mentorId, String keyword, Pageable pageable) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));
        if (mentor.getCurrentMenteeCount() >= mentor.getMaxMenteeCapacity()) {
            throw new MatchingNotAllowedException("You have reached your maximum mentee capacity");
        }
        return slicePage(findCandidateMenteesFor(mentor, keyword), pageable);
    }

    /**
     * ID-based wrapper: loads the mentee, verifies eligibility, then delegates
     * to {@link #rankMentorsFor(Mentee, String)}. Used by the public matching
     * methods. Throws {@link MatchingNotAllowedException} when the mentee
     * already has an active mentor (a business condition mapped to HTTP 403
     * by the global handler) and {@link ResourceNotFoundException} (HTTP 404)
     * when the mentee row is missing.
     *
     * <p>Naming parallels the pure variant {@link #rankMentorsFor(Mentee, String)}
     * — same root verb, the {@code ForId} suffix signals "give me a Long, I'll
     * handle the load + check."
     *
     * <p>No {@code @Transactional} annotation on purpose: Spring AOP's default
     * proxies do not apply transaction advice to package-private methods, and
     * the public callers ({@code getTopMentors}, {@code getTopMentorsList})
     * already declare {@code readOnly = true} which propagates here. A future
     * caller that needs a different transaction shape should declare it on
     * their own public entry point and pass through.
     */
    List<MentorMatchResponse> rankMentorsForId(Long menteeId, String keyword, Double maxDistanceKm) {
        Mentee mentee = menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));
        if (mentee.getActiveMentorId() != null) {
            throw new MatchingNotAllowedException("You already have an active mentor");
        }
        return rankMentorsFor(mentee, keyword, maxDistanceKm);
    }

    /**
     * Pure ranking core for an already-loaded eligible mentee. Skips the
     * {@code findById} + active-mentor check, so callers who have a Mentee
     * in hand (e.g., {@code MatchNotificationProcessor.processMentee}) avoid
     * a redundant DB round-trip.
     *
     * <p><b>Precondition:</b> the mentee must be non-null and have no active
     * mentor. Violations throw {@link IllegalStateException} (programmer
     * error, not a business condition — not mapped to HTTP).
     *
     * <p>No {@code @Transactional}: package-private method advice is ignored
     * by Spring's proxy. Runs inside the caller's transaction.
     */
    List<MentorMatchResponse> rankMentorsFor(Mentee mentee, String keyword) {
        return rankMentorsFor(mentee, keyword, null);
    }

    /**
     * Pure ranking core with optional max-distance ceiling. The two-arg
     * overload {@link #rankMentorsFor(Mentee, String)} is kept for
     * {@code MatchNotificationProcessor}, which still calls the no-distance
     * variant. New callers should prefer this three-arg method.
     */
    List<MentorMatchResponse> rankMentorsFor(Mentee mentee, String keyword, Double maxDistanceKm) {
        if (mentee == null) {
            throw new IllegalStateException("rankMentorsFor: mentee must not be null");
        }
        if (mentee.getActiveMentorId() != null) {
            throw new IllegalStateException(
                    "rankMentorsFor: mentee " + mentee.getId()
                            + " has an active mentor; caller must check eligibility first");
        }

        Pageable fetchPage = PageRequest.of(0, rankingWindow);
        List<Mentor> raw = mentorRepository.findRankingCandidates(
                SearchNormaliser.keyword(keyword), null, null, null,
                /*requireCapacity*/ true,
                /*requesterMenteeId*/ null,
                fetchPage);

        if (raw.isEmpty()) {
            return List.of();
        }

        List<Long> mentorIds = raw.stream().map(Mentor::getId).toList();
        Map<Long, List<AvailabilitySlot>> slotsByMentor = availabilitySlotRepository
                .findByMentorIdIn(mentorIds).stream()
                .collect(Collectors.groupingBy(s -> s.getMentor().getId()));
        List<MenteeAvailabilitySlot> menteeSlots =
                menteeAvailabilitySlotRepository.findByMenteeId(mentee.getId());

        return scoringPipeline.rank(raw, mentee, slotsByMentor, menteeSlots, maxDistanceKm);
    }

    /**
     * Pure candidate-mentee filter for an already-loaded eligible mentor.
     * Skips the {@code findById} + capacity check; caller must verify.
     *
     * <p><b>Precondition:</b> the mentor must be non-null and have spare
     * capacity. Violations throw {@link IllegalStateException}.
     */
    List<MenteeCandidateResponse> findCandidateMenteesFor(Mentor mentor, String keyword) {
        if (mentor == null) {
            throw new IllegalStateException("findCandidateMenteesFor: mentor must not be null");
        }
        if (mentor.getCurrentMenteeCount() >= mentor.getMaxMenteeCapacity()) {
            throw new IllegalStateException(
                    "findCandidateMenteesFor: mentor " + mentor.getId()
                            + " is at full capacity; caller must check eligibility first");
        }

        // requesterMentorId is null on the matching path: slot overlap is part
        // of the SCORING in the mentor-side path (the ranker's availability
        // score), but the mentee-side path here doesn't score. Passing
        // mentor.id would silently exclude every mentee whenever the mentor
        // has zero availability slots set — not the intended behaviour.
        Pageable fetchPage = PageRequest.of(0, rankingWindow);
        List<Mentee> raw = menteeRepository.findRankingCandidates(
                SearchNormaliser.keyword(keyword), null, null, null,
                /*requireUnattached*/ true,
                /*requesterMentorId*/ null,
                fetchPage);

        // In-memory post-filter: mentees must match at least one of the mentor's
        // preferences (interest overlap, skill, preferred major, or field). This
        // OR-of-categories semantic doesn't compose well with the AND-across-
        // filters JPQL `searchByFilters` shape, and pushing it to SQL would mean
        // splitting the repo method or introducing a 5th boolean param. The
        // post-filter runs on at most {@code RANKING_WINDOW} rows already in
        // memory, so the cost is negligible and the SQL stays clean.
        return raw.stream()
                .filter(me -> matchesMentorPreferences(mentor, me))
                .map(MenteeCandidateResponse::from)
                .toList();
    }

    private static <T> Page<T> slicePage(List<T> ranked, Pageable pageable) {
        // totalElements equals the in-window total so pagination metadata stays
        // self-consistent — the frontend's pagination control reflects the
        // visible 200-item window, not the underlying SQL match count.
        //
        // Compare offset against ranked.size() as `long` BEFORE narrowing to
        // int. Pageable.getOffset() is `long` (pageNumber * pageSize); a
        // pathological page=Integer.MAX_VALUE with size > 1 overflows when
        // cast directly to int and produces a negative `start`, which would
        // throw IndexOutOfBoundsException from List.subList. Comparing in
        // long-space and short-circuiting on out-of-range offsets makes the
        // narrowing cast safe (offset < ranked.size() ≤ rankingWindow).
        long offset = pageable.getOffset();
        if (offset >= ranked.size()) {
            return new PageImpl<>(List.of(), pageable, ranked.size());
        }
        int start = (int) offset;
        int end = Math.min(start + pageable.getPageSize(), ranked.size());
        return new PageImpl<>(ranked.subList(start, end), pageable, ranked.size());
    }

    /**
     * True iff the mentee matches any of the mentor's preferences — interest
     * overlap, skill present in the mentor's preferred-mentee skills, mentee
     * major equals the mentor's preferred major, or mentee major equals the
     * mentor's field. OR-of-categories rather than AND, which is why this
     * lives outside the SQL {@code searchByFilters} shape.
     */
    static boolean matchesMentorPreferences(Mentor mentor, Mentee mentee) {
        List<String> mentorInterests = mentor.getInterests();
        if (mentorInterests != null) {
            for (String interest : nullSafe(mentee.getInterests())) {
                if (containsIgnoreCase(mentorInterests, interest)) return true;
            }
        }
        List<String> preferredSkills = mentor.getPreferredMenteeSkills();
        if (preferredSkills != null) {
            for (String skill : nullSafe(mentee.getSkills())) {
                if (containsIgnoreCase(preferredSkills, skill)) return true;
            }
        }
        if (mentee.getMajor() != null) {
            if (mentor.getPreferredMenteeMajor() != null
                    && mentee.getMajor().equalsIgnoreCase(mentor.getPreferredMenteeMajor())) {
                return true;
            }
            if (mentor.getField() != null
                    && mentee.getMajor().equalsIgnoreCase(mentor.getField())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> nullSafe(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (value == null) return false;
        return list.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(value));
    }
}
