package com.group7.backend.service;

import com.group7.backend.dto.request.EditProfileRequest;
import com.group7.backend.dto.request.MenteeProfileRequest;
import com.group7.backend.dto.request.MentorProfileRequest;
import com.group7.backend.dto.request.SearchRole;
import com.group7.backend.dto.response.AdminResponse;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.dto.response.UserProfileResponse;
import com.group7.backend.dto.response.UserRatingSummary;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.FollowId;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.TaggedTermLists;
import com.group7.backend.entity.User;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final MenteeRepository menteeRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;
    private final FollowRepository followRepository;
    private final FileStorageService fileStorageService;
    private final MentorRatingService mentorRatingService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Feature flag for the 1.1.2.5 surname/photo masking layer (#570). Defaults
     * to {@code true} so the masking ships hot; flipping the property to
     * {@code false} (e.g. via {@code APP_PROFILE_MASK_MENTEE_FOR_MENTOR_ENABLED=false}
     * in production) reverts {@link #maskIfMentorViewingMentee} to a no-op
     * without redeploying. The gate fires only when {@code target.profileVisibility=false}
     * is false — currently-visible profiles are unaffected by the toggle.
     */
    private final boolean menteeMaskEnabled;

    public UserService(UserRepository userRepository, MentorRepository mentorRepository,
                       MenteeRepository menteeRepository,
                       AvailabilitySlotRepository availabilitySlotRepository,
                       MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository,
                       FollowRepository followRepository,
                       FileStorageService fileStorageService,
                       MentorRatingService mentorRatingService,
                       ApplicationEventPublisher eventPublisher,
                       @Value("${app.profile.mask-mentee-for-mentor.enabled:true}")
                       boolean menteeMaskEnabled) {
        this.userRepository = userRepository;
        this.mentorRepository = mentorRepository;
        this.menteeRepository = menteeRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.menteeAvailabilitySlotRepository = menteeAvailabilitySlotRepository;
        this.followRepository = followRepository;
        this.fileStorageService = fileStorageService;
        this.mentorRatingService = mentorRatingService;
        this.eventPublisher = eventPublisher;
        this.menteeMaskEnabled = menteeMaskEnabled;
    }

    // ── Existing methods ────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProfileResponse> getAllUsersFiltered(Long requesterId, Pageable pageable) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requesterId));

        boolean bypassVisibility = requester instanceof Admin;

        // Mentees can only see mentors (not other mentees). Private mentors
        // are filtered at SQL level; admins bypass via bypassVisibility=true.
        if (requester instanceof Mentee) {
            return mentorRepository.searchByFilters(
                    null, null, null, null,
                    /*requireCapacity*/ false, bypassVisibility,
                    /*requesterMenteeId*/ null,
                    /*availabilityDays*/ null, /*mentorshipDuration*/ null,
                    pageable)
                    .map(m -> (ProfileResponse) MentorResponse.from(m));
        }

        // Mentor / admin viewers see mentors + mentees. Private profiles are
        // filtered at SQL via findAllNonAdminsVisible; mentor viewers also
        // get mentee surname/photo masking (1.1.2.5).
        final User viewer = requester;
        return userRepository.findAllNonAdminsVisible(bypassVisibility, pageable)
                .map(u -> {
                    ProfileResponse resp = mapToResponse(u);
                    maskIfMentorViewingMentee(resp, viewer, u);
                    return resp;
                });
    }

    /**
     * General-purpose user search with composable filters (#262). Distinct
     * from the matching path: this surface is exploratory ("find me people
     * with X"), not score-driven. Filtering happens at the SQL layer via
     * {@code MentorRepository.searchByFilters} / {@code MenteeRepository.searchByFilters}.
     *
     * <h3>Role gate</h3>
     * <ul>
     *   <li>Admin → may search any {@link SearchRole}, but
     *       {@code hasAvailability=true} is rejected (admins have no slots).</li>
     *   <li>Mentee → may search {@code MENTOR} only; same-role search yields 403.
     *       {@code hasAvailability=true} requires the mentee to have at least
     *       one availability slot, else 400.</li>
     *   <li>Mentor → mirror of mentee (may search {@code MENTEE} only;
     *       slot-presence requirement applies).</li>
     * </ul>
     */
    /**
     * Backwards-compatible delegate preserved for callers that predate the
     * advanced mentor filters in #571. New code should use the 10-arg form
     * with {@code availabilityDays} / {@code mentorshipDuration}; passing
     * {@code null} for both reproduces the pre-#571 behaviour.
     */
    @Transactional(readOnly = true)
    public Page<ProfileResponse> searchUsers(SearchRole role,
                                             String keyword,
                                             List<String> interests,
                                             List<String> skills,
                                             String major,
                                             boolean hasAvailability,
                                             Long requesterId,
                                             Pageable pageable) {
        return searchUsers(role, keyword, interests, skills, major,
                hasAvailability, null, null, requesterId, pageable);
    }

    /**
     * Advanced overload (#571) accepting the day-of-week and mentorship-
     * duration filters introduced for the mentor search surface. The
     * {@link MenteeRepository#searchByFilters} side ignores both — the issue
     * scopes them to mentor results only.
     */
    @Transactional(readOnly = true)
    public Page<ProfileResponse> searchUsers(SearchRole role,
                                             String keyword,
                                             List<String> interests,
                                             List<String> skills,
                                             String major,
                                             boolean hasAvailability,
                                             Set<DayOfWeek> availabilityDays,
                                             Set<Integer> mentorshipDuration,
                                             Long requesterId,
                                             Pageable pageable) {
        Objects.requireNonNull(role, "role");
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requesterId));

        // Role gate: same-role search and admin-with-hasAvailability are
        // rejected upfront before the repo query.
        if (requester instanceof Admin) {
            if (hasAvailability) {
                throw new IllegalArgumentException(
                        "hasAvailability filter is not applicable for admin searches");
            }
        } else if (requester instanceof Mentee && role == SearchRole.MENTEE) {
            throw new ProfileNotVisibleException("Mentees cannot search for other mentees");
        } else if (requester instanceof Mentor && role == SearchRole.MENTOR) {
            throw new ProfileNotVisibleException("Mentors cannot search for other mentors");
        }

        // Slot-presence guard: hasAvailability=true requires the requester
        // to have at least one slot of their own to compare against. Without
        // this, the SQL filter silently returns empty, which surprises users.
        // existsBy* emits SELECT 1 ... LIMIT 1, no entity hydration.
        if (hasAvailability && requester instanceof Mentee
                && !menteeAvailabilitySlotRepository.existsByMenteeId(requesterId)) {
            throw new IllegalArgumentException(
                    "Set your availability before filtering by overlap");
        }
        if (hasAvailability && requester instanceof Mentor
                && !availabilitySlotRepository.existsByMentorId(requesterId)) {
            throw new IllegalArgumentException(
                    "Set your availability before filtering by overlap");
        }

        String normKeyword = SearchNormaliser.keyword(keyword);
        List<String> normInterests = SearchNormaliser.list(interests);
        List<String> normSkills = SearchNormaliser.list(skills);
        String normMajor = SearchNormaliser.scalar(major);
        // Empty Set<DayOfWeek>/Set<Integer> must be coerced to null so the
        // JPQL `:param IS NULL` gate short-circuits — Postgres rejects
        // `IN ()` exactly as it does for the list-of-string filters.
        Set<DayOfWeek> normAvailabilityDays = SearchNormaliser.nullIfEmpty(availabilityDays);
        Set<Integer> normMentorshipDuration = SearchNormaliser.nullIfEmpty(mentorshipDuration);

        boolean bypassVisibility = requester instanceof Admin;
        if (role == SearchRole.MENTOR) {
            Long requesterMenteeId = (hasAvailability && requester instanceof Mentee)
                    ? requesterId : null;
            return mentorRepository.searchByFilters(
                    normKeyword, normInterests, normSkills, normMajor,
                    /*requireCapacity*/ false, bypassVisibility, requesterMenteeId,
                    normAvailabilityDays, normMentorshipDuration, pageable)
                    .map(m -> (ProfileResponse) MentorResponse.from(m));
        } else {
            Long requesterMentorId = (hasAvailability && requester instanceof Mentor)
                    ? requesterId : null;
            // #570 1.1.2.5 — mentors viewing mentees in search results see masked
            // lastName/profilePhoto. Admins bypass via the early-return path inside
            // maskIfMentorViewingMentee (admin viewer does not match `instanceof Mentor`).
            User viewer = requester;
            // MenteeRepository is intentionally not extended with the
            // mentor-only filters from #571 — they're silently ignored on this branch.
            return menteeRepository.searchByFilters(
                    normKeyword, normInterests, normSkills, normMajor,
                    /*requireUnattached*/ false, bypassVisibility, requesterMentorId, pageable)
                    .map(m -> {
                        MenteeResponse mr = MenteeResponse.from(m);
                        maskIfMentorViewingMentee(mr, viewer, m);
                        return (ProfileResponse) mr;
                    });
        }
    }

    @Transactional(readOnly = true)
    public Page<MentorResponse> getAllMentors(Long requesterId, Pageable pageable) {
        boolean bypassVisibility = isAdminRequester(requesterId);
        return mentorRepository.searchByFilters(
                null, null, null, null,
                /*requireCapacity*/ false, bypassVisibility,
                /*requesterMenteeId*/ null,
                /*availabilityDays*/ null, /*mentorshipDuration*/ null,
                pageable)
                .map(MentorResponse::from);
    }

    @Transactional(readOnly = true)
    public List<MentorResponse> getAllMentorsList(Long requesterId) {
        boolean bypassVisibility = isAdminRequester(requesterId);
        // Unpaginated variant — reuses searchByFilters with a single large page
        // so the visibility filter is applied at SQL level. Page size is
        // intentionally large enough to drain the table; production rollouts
        // with thousands of mentors should already prefer the paginated
        // /api/users/mentors endpoint.
        return mentorRepository.searchByFilters(
                null, null, null, null,
                /*requireCapacity*/ false, bypassVisibility,
                /*requesterMenteeId*/ null,
                /*availabilityDays*/ null, /*mentorshipDuration*/ null,
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE))
                .getContent().stream()
                .map(MentorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MenteeResponse> getAllMentees(Long requesterId, Pageable pageable) {
        User requester = requesterId == null ? null
                : userRepository.findById(requesterId).orElse(null);
        boolean bypassVisibility = requester instanceof Admin;
        final User viewer = requester;
        return menteeRepository.searchByFilters(
                null, null, null, null,
                /*requireUnattached*/ false, bypassVisibility,
                /*requesterMentorId*/ null, pageable)
                .map(m -> {
                    MenteeResponse mr = MenteeResponse.from(m);
                    maskIfMentorViewingMentee(mr, viewer, m);
                    return mr;
                });
    }

    private boolean isAdminRequester(Long requesterId) {
        if (requesterId == null) {
            return false;
        }
        return userRepository.findById(requesterId)
                .map(u -> u instanceof Admin)
                .orElse(false);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        String photoUrl = user.getProfilePhoto();
        userRepository.delete(user);

        // The Postgres-side ON DELETE CASCADE on follows reaps follow rows
        // silently — no per-row JPA event fires. Publish an explicit event so
        // the Neo4j follow-graph mirror can DETACH DELETE the matching :User
        // node and its incident edges. Listener runs AFTER_COMMIT so a Neo4j
        // outage cannot abort this transaction.
        eventPublisher.publishEvent(FollowChangedEvent.userDeleted(id));

        // Delete file AFTER transaction commits
        if (photoUrl != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileStorageService.deleteFile(photoUrl);
                }
            });
        }
    }

    // ── Profile CRUD methods ────────────────────────────────

    @Transactional(readOnly = true)
    public ProfileResponse getOwnProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return mapToResponse(user);
    }

    /**
     * Own-profile variant that wraps {@link #getOwnProfile(Long)} with the
     * follower / following counts introduced by #343. Used by
     * {@code GET /api/users/me}. Carries no privacy gate (you are always
     * allowed to view your own profile, including admins viewing
     * {@code /me}).
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getOwnUserProfile(Long userId) {
        ProfileResponse profile = getOwnProfile(userId);
        return wrapWithCounts(profile, userId, userId);
    }

    /**
     * Privacy-checked variant that wraps {@link #getProfileById(Long, Long)}
     * with the follower / following counts. Used by
     * {@code GET /api/users/{id:\\d+}}. Inherits the same privacy gates as
     * the underlying call: 403 for mentee→mentee and for any access to an
     * admin's profile, 404 when either id doesn't resolve.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long targetId, Long requesterId) {
        ProfileResponse profile = getProfileById(targetId, requesterId);
        return wrapWithCounts(profile, targetId, requesterId);
    }

    private UserProfileResponse wrapWithCounts(ProfileResponse profile, Long profileUserId, Long viewerId) {
        long followers = followRepository.countByIdFolloweeId(profileUserId);
        long following = followRepository.countByIdFollowerId(profileUserId);
        // Mentor rating summary (#237). Mentees never accumulate rows in
        // mentor_ratings, so the aggregate returns (null, 0) for them; we
        // still set the fields uniformly so the response shape is stable.
        UserRatingSummary rating = mentorRatingService.aggregateForMentor(profileUserId);
        if (profile instanceof UserResponse base) {
            base.setAverageRating(rating.averageRating());
            base.setRatingCount(rating.ratingCount());
        }
        // Self-view and anonymous reads never render Follow/Unfollow, so they
        // skip the existsById probe; clients render Follow only when the
        // boolean is true AND the viewer is somebody else.
        boolean isFollowing = viewerId != null
                && !viewerId.equals(profileUserId)
                && followRepository.existsById(new FollowId(viewerId, profileUserId));
        return new UserProfileResponse(profile, followers, following, isFollowing);
    }

    /**
     * Privacy-gated profile lookup. Implements the full #570 redaction matrix:
     * <ul>
     *   <li>Owner (self) — always 200, no redaction, regardless of role/visibility.</li>
     *   <li>Admin viewer — always 200 for non-admin targets, no redaction.</li>
     *   <li>Admin target (third party) — always 403 (admin opacity, pre-#570).</li>
     *   <li>Mentee → Mentee (other) — 403 (req 1.1.2.7, pre-#570).</li>
     *   <li>{@code target.profileVisibility=false} (Mentor OR Mentee), non-owner, non-admin
     *       — 403 via {@link ProfileNotVisibleException}.</li>
     *   <li>Mentor → visible Mentee — 200, but {@code lastName} and {@code profilePhoto}
     *       are masked via {@link #maskIfMentorViewingMentee} (1.1.2.5).</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public ProfileResponse getProfileById(Long targetId, Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requesterId));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + targetId));

        // Mentees cannot view other mentee profiles (req 1.1.2.7)
        if (requester instanceof Mentee && target instanceof Mentee && !targetId.equals(requesterId)) {
            throw new ProfileNotVisibleException("Mentees cannot view other mentee profiles");
        }

        // Admins are not exposed via the user-profile graph
        if (target instanceof Admin) {
            throw new ProfileNotVisibleException("Admin profile is not visible");
        }

        // #570 server-side visibility gate. Self-view and admin viewers bypass.
        boolean self = targetId.equals(requesterId);
        boolean adminViewer = requester instanceof Admin;
        if (!self && !adminViewer) {
            if (target instanceof Mentee tm && Boolean.FALSE.equals(tm.getProfileVisibility())) {
                throw new ProfileNotVisibleException("Profile is private");
            }
            if (target instanceof Mentor tn && Boolean.FALSE.equals(tn.getProfileVisibility())) {
                throw new ProfileNotVisibleException("Profile is private");
            }
        }

        ProfileResponse out = mapToResponse(target);
        maskIfMentorViewingMentee(out, requester, target);
        return out;
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, EditProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Apply common fields (skip nulls)
        // Note: profilePhoto is intentionally NOT set here — use POST /api/users/me/photo instead
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        applyLocationFields(user, request);

        // Apply role-specific fields
        if (user instanceof Mentor mentor && request instanceof MentorProfileRequest mentorReq) {
            applyMentorFields(mentor, mentorReq);
        } else if (user instanceof Mentee mentee && request instanceof MenteeProfileRequest menteeReq) {
            applyMenteeFields(mentee, menteeReq);
        }

        User saved = userRepository.save(user);
        return mapToResponse(saved);
    }

    @Transactional
    public ProfileResponse uploadProfilePhoto(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Store new file FIRST (before touching DB or deleting old file)
        String newPhotoUrl = fileStorageService.storeFile(file);
        String oldPhotoUrl = user.getProfilePhoto();

        // Update DB
        user.setProfilePhoto(newPhotoUrl);
        User saved = userRepository.save(user);

        // Delete old file AFTER transaction commits (so rollback doesn't lose old photo)
        if (oldPhotoUrl != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileStorageService.deleteFile(oldPhotoUrl);
                }
            });
        }

        return mapToResponse(saved);
    }

    @Transactional
    public ProfileResponse deleteProfilePhoto(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String oldPhotoUrl = user.getProfilePhoto();
        if (oldPhotoUrl != null) {
            user.setProfilePhoto(null);
            userRepository.save(user);

            // Delete file AFTER transaction commits
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileStorageService.deleteFile(oldPhotoUrl);
                }
            });
        }

        return mapToResponse(user);
    }

    // ── Private helpers ─────────────────────────────────────

    /**
     * 1.1.2.5 — mask a mentee's {@code lastName} and {@code profilePhoto} when
     * the viewer is a mentor. Idempotent and side-effect-free on every other
     * viewer/target combination.
     *
     * <p>Gated by {@code app.profile.mask-mentee-for-mentor.enabled} (default
     * {@code true}). When the property is {@code false}, this helper is a
     * no-op so the masking layer can be toggled off in production without a
     * redeploy. The gate does NOT change visibility — only the field-level
     * masking is suppressed; profiles already returned (status-200) keep
     * their full payload. Profiles hidden via {@code target.profileVisibility=false}
     * are blocked upstream by {@link #getProfileById} before this helper runs.
     *
     * <p>Owner self-view and admin viewers never reach this code path with a
     * mentor-viewer / mentee-target pairing — admins are an instance of
     * {@code Admin}, not {@code Mentor}, and self-view masks only when viewer
     * and target share the mentor-vs-mentee class split. The pattern-match
     * therefore both selects the right pairing and excludes all bypass cases
     * by construction.
     */
    private void maskIfMentorViewingMentee(ProfileResponse resp, User viewer, User target) {
        if (!menteeMaskEnabled) {
            return;
        }
        if (viewer instanceof Mentor && target instanceof Mentee && resp instanceof MenteeResponse mr) {
            mr.setLastName(null);
            mr.setProfilePhoto(null);
        }
    }

    /**
     * Applies optional location fields from the request (#282 / req 1.1.2.3).
     *
     * <p>Skip-nulls semantics: a null field means "no change", matching the rest
     * of {@code updateProfile}. To clear all location fields, the client would
     * need to submit empty strings for {@code city} and explicit zeros for the
     * coords (or call a dedicated clear endpoint, which is a future follow-up).
     *
     * <p>The hybrid auto-fill UX on the clients (#461 web, #462 mobile) always
     * submits all three together, so partial-update edge cases are unlikely in
     * normal use. Bean Validation on {@link EditProfileRequest} keeps individual
     * coordinate ranges in bounds; the DB-level pair-completeness CHECK
     * constraint (V34 migration) is the final safety net.
     */
    private static void applyLocationFields(User user, EditProfileRequest request) {
        if (request.getCity() != null) {
            user.setCity(request.getCity());
        }
        if (request.getLatitude() != null) {
            user.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            user.setLongitude(request.getLongitude());
        }
    }

    /**
     * Maps a {@link User} entity to its role-specific {@link ProfileResponse}.
     *
     * <p>Admins map to {@link AdminResponse} so the self-view path
     * {@code GET /api/users/me} returns 200 for authenticated admins.
     * This is the only call path that legitimately reaches the admin
     * branch:
     * <ul>
     *   <li>{@link #getProfileById(Long, Long)} short-circuits with 403
     *       for admin targets before invoking this helper, preserving
     *       admin opacity to third parties.</li>
     *   <li>{@link #getAllUsersFiltered(Long, Pageable)} filters admins
     *       at the repository layer via {@code findAllNonAdmins}.</li>
     *   <li>{@link #searchUsers(Long, String, Pageable, SearchRole)}
     *       dispatches to mentor / mentee repositories that never load
     *       admins.</li>
     *   <li>Profile-edit / photo paths are scoped to {@code /me}, so
     *       admins reaching them is privacy-equivalent to viewing
     *       {@code /me} itself.</li>
     * </ul>
     */
    private ProfileResponse mapToResponse(User user) {
        if (user instanceof Mentor mentor) {
            return MentorResponse.from(mentor);
        } else if (user instanceof Mentee mentee) {
            return MenteeResponse.from(mentee);
        } else if (user instanceof Admin admin) {
            return AdminResponse.from(admin);
        }
        throw new IllegalStateException("Unknown user type: " + user.getClass().getSimpleName());
    }

    private void applyMentorFields(Mentor mentor, MentorProfileRequest request) {
        if (request.getProfileVisibility() != null) {
            mentor.setProfileVisibility(request.getProfileVisibility());
        }
        if (request.getBio() != null) {
            mentor.setBio(request.getBio());
        }
        if (request.getField() != null) {
            mentor.setField(request.getField());
        }
        if (request.getFieldUri() != null) {
            mentor.setFieldUri(request.getFieldUri());
        }
        if (request.getExpertise() != null) {
            mentor.setExpertise(request.getExpertise());
        }
        if (request.getExpertiseUri() != null) {
            mentor.setExpertiseUri(request.getExpertiseUri());
        }
        if (request.getAffiliation() != null) {
            mentor.setAffiliation(request.getAffiliation());
        }
        if (request.getInterests() != null) {
            mentor.setInterestEntries(TaggedTermLists.combine(request.getInterests(), request.getInterestUris()));
        }
        if (request.getMaxMenteeCapacity() != null) {
            mentor.setMaxMenteeCapacity(request.getMaxMenteeCapacity());
        }
        if (request.getPreferredMenteeSkills() != null) {
            mentor.setPreferredMenteeSkillEntries(TaggedTermLists.combine(
                    request.getPreferredMenteeSkills(),
                    request.getPreferredMenteeSkillUris()));
        }
        if (request.getPreferredMenteeMajor() != null) {
            mentor.setPreferredMenteeMajor(request.getPreferredMenteeMajor());
        }
        if (request.getPreferredMenteeMajorUri() != null) {
            mentor.setPreferredMenteeMajorUri(request.getPreferredMenteeMajorUri());
        }
        if (request.getMentoringGoals() != null) {
            mentor.setMentoringGoals(request.getMentoringGoals());
        }
        if (request.getMentorshipDuration() != null) {
            mentor.setMentorshipDuration(request.getMentorshipDuration());
        }
    }

    private void applyMenteeFields(Mentee mentee, MenteeProfileRequest request) {
        if (request.getProfileVisibility() != null) {
            mentee.setProfileVisibility(request.getProfileVisibility());
        }
        if (request.getGoals() != null) {
            mentee.setGoals(request.getGoals());
        }
        if (request.getMajor() != null) {
            mentee.setMajor(request.getMajor());
        }
        if (request.getMajorUri() != null) {
            mentee.setMajorUri(request.getMajorUri());
        }
        if (request.getInterests() != null) {
            mentee.setInterestEntries(TaggedTermLists.combine(request.getInterests(), request.getInterestUris()));
        }
        if (request.getCareerInterest() != null) {
            mentee.setCareerInterest(request.getCareerInterest());
        }
        if (request.getCareerInterestUri() != null) {
            mentee.setCareerInterestUri(request.getCareerInterestUri());
        }
        if (request.getSkills() != null) {
            mentee.setSkillEntries(TaggedTermLists.combine(
                    request.getSkills(), request.getSkillUris()));
        }
        if (request.getMeetingFreqPref() != null) {
            mentee.setMeetingFreqPref(request.getMeetingFreqPref());
        }
        if (request.getBackgroundInfo() != null) {
            mentee.setBackgroundInfo(request.getBackgroundInfo());
        }
        if (request.getAffiliation() != null) {
            mentee.setAffiliation(request.getAffiliation());
        }
    }

}
