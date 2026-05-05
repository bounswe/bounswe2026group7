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
import com.group7.backend.service.ranking.MentorRanker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mentor/mentee matching service. The data-loading layer is fully SQL-side
 * (#262); this service orchestrates the search → batch-slot-fetch → score →
 * paginate pipeline.
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
     * Fixed-size oversample window for in-memory scoring + ranking. Pages
     * beyond this window return empty content; the frontend should treat
     * that as "end of results".
     */
    private static final int RANKING_WINDOW = 200;

    private final MenteeRepository menteeRepository;
    private final MentorRepository mentorRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;
    private final MentorRanker mentorRanker;
    private final NotificationEventPublisher notificationEventPublisher;

    public MatchingService(MenteeRepository menteeRepository,
                           MentorRepository mentorRepository,
                           AvailabilitySlotRepository availabilitySlotRepository,
                           MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository,
                           MentorRanker mentorRanker,
                           NotificationEventPublisher notificationEventPublisher) {
        this.menteeRepository = menteeRepository;
        this.mentorRepository = mentorRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.menteeAvailabilitySlotRepository = menteeAvailabilitySlotRepository;
        this.mentorRanker = mentorRanker;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<MentorMatchResponse> getTopMentors(Long menteeId, String keyword, Pageable pageable) {
        List<MentorMatchResponse> ranked = rankAvailableMentors(menteeId, keyword);
        // Match-found notification only on page 0 — pre-refactor it fired on the
        // globally-top mentor, so the post-refactor equivalent is the top of
        // the first page. Subsequent pages don't surface "found you a match!"
        // because the user is already past the headline result.
        if (pageable.getPageNumber() == 0 && !ranked.isEmpty()) {
            notificationEventPublisher.publishMatchFound(menteeId, ranked.get(0).getFirstName());
        }
        return slicePage(ranked, pageable);
    }

    @Transactional(readOnly = true)
    public List<MentorMatchResponse> getTopMentorsList(Long menteeId, String keyword) {
        List<MentorMatchResponse> ranked = rankAvailableMentors(menteeId, keyword);
        if (!ranked.isEmpty()) {
            notificationEventPublisher.publishMatchFound(menteeId, ranked.get(0).getFirstName());
        }
        return ranked;
    }

    @Transactional(readOnly = true)
    public Page<MenteeCandidateResponse> getCandidateMentees(Long mentorId, String keyword, Pageable pageable) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));
        if (mentor.getCurrentMenteeCount() >= mentor.getMaxMenteeCapacity()) {
            throw new MatchingNotAllowedException("You have reached your maximum mentee capacity");
        }

        // Mentee-side has no scoring algorithm today (filter-only). The
        // existing notification semantics fire when the candidate set is
        // non-empty, gated to page 0 to match the mentor path.
        // requesterMentorId is null on the matching path: slot overlap is part
        // of the SCORING in the mentor-side path (the ranker's availability
        // score), but the mentee-side path here doesn't score, and the
        // pre-refactor behaviour did not slot-filter candidate mentees. Passing
        // mentorId here would silently exclude every mentee whenever the mentor
        // has zero availability slots set.
        Pageable fetchPage = PageRequest.of(0, RANKING_WINDOW);
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
        List<MenteeCandidateResponse> candidates = raw.stream()
                .filter(me -> matchesMentorPreferences(mentor, me))
                .map(MenteeCandidateResponse::from)
                .toList();

        if (pageable.getPageNumber() == 0 && !candidates.isEmpty()) {
            notificationEventPublisher.publishMatchFound(mentorId, candidates.get(0).getFirstName());
        }
        return slicePage(candidates, pageable);
    }

    private List<MentorMatchResponse> rankAvailableMentors(Long menteeId, String keyword) {
        Mentee mentee = menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));
        if (mentee.getActiveMentorId() != null) {
            throw new MatchingNotAllowedException("You already have an active mentor");
        }

        Pageable fetchPage = PageRequest.of(0, RANKING_WINDOW);
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
                menteeAvailabilitySlotRepository.findByMenteeId(menteeId);

        return raw.stream()
                .map(m -> MentorMatchResponse.from(m, mentorRanker.score(
                        m, mentee,
                        slotsByMentor.getOrDefault(m.getId(), List.of()),
                        menteeSlots)))
                .sorted(Comparator.comparingInt(MentorMatchResponse::getMatchScore).reversed())
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
        // narrowing cast safe (offset < ranked.size() ≤ RANKING_WINDOW).
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
