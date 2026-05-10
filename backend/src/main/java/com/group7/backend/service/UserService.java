package com.group7.backend.service;

import com.group7.backend.dto.request.EditProfileRequest;
import com.group7.backend.dto.request.MenteeProfileRequest;
import com.group7.backend.dto.request.MentorProfileRequest;
import com.group7.backend.dto.request.SearchRole;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.dto.response.UserProfileResponse;
import com.group7.backend.dto.response.UserRatingSummary;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.TaggedTermLists;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Objects;
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

    public UserService(UserRepository userRepository, MentorRepository mentorRepository,
                       MenteeRepository menteeRepository,
                       AvailabilitySlotRepository availabilitySlotRepository,
                       MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository,
                       FollowRepository followRepository,
                       FileStorageService fileStorageService,
                       MentorRatingService mentorRatingService) {
        this.userRepository = userRepository;
        this.mentorRepository = mentorRepository;
        this.menteeRepository = menteeRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.menteeAvailabilitySlotRepository = menteeAvailabilitySlotRepository;
        this.followRepository = followRepository;
        this.fileStorageService = fileStorageService;
        this.mentorRatingService = mentorRatingService;
    }

    // ── Existing methods ────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProfileResponse> getAllUsersFiltered(Long requesterId, Pageable pageable) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requesterId));

        // Mentees can only see mentors (not other mentees)
        if (requester instanceof Mentee) {
            return mentorRepository.findAll(pageable)
                    .map(m -> (ProfileResponse) MentorResponse.from(m));
        }

        return userRepository.findAllNonAdmins(pageable)
                .map(this::mapToResponse);
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
    @Transactional(readOnly = true)
    public Page<ProfileResponse> searchUsers(SearchRole role,
                                             String keyword,
                                             List<String> interests,
                                             List<String> skills,
                                             String major,
                                             boolean hasAvailability,
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

        if (role == SearchRole.MENTOR) {
            Long requesterMenteeId = (hasAvailability && requester instanceof Mentee)
                    ? requesterId : null;
            return mentorRepository.searchByFilters(
                    normKeyword, normInterests, normSkills, normMajor,
                    /*requireCapacity*/ false, requesterMenteeId, pageable)
                    .map(m -> (ProfileResponse) MentorResponse.from(m));
        } else {
            Long requesterMentorId = (hasAvailability && requester instanceof Mentor)
                    ? requesterId : null;
            return menteeRepository.searchByFilters(
                    normKeyword, normInterests, normSkills, normMajor,
                    /*requireUnattached*/ false, requesterMentorId, pageable)
                    .map(m -> (ProfileResponse) MenteeResponse.from(m));
        }
    }

    @Transactional(readOnly = true)
    public Page<MentorResponse> getAllMentors(Pageable pageable) {
        return mentorRepository.findAll(pageable)
                .map(MentorResponse::from);
    }

    @Transactional(readOnly = true)
    public List<MentorResponse> getAllMentorsList() {
        return mentorRepository.findAll().stream()
                .map(MentorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MenteeResponse> getAllMentees(Pageable pageable) {
        return menteeRepository.findAll(pageable)
                .map(MenteeResponse::from);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        String photoUrl = user.getProfilePhoto();
        userRepository.delete(user);

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
        return wrapWithCounts(profile, userId);
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
        return wrapWithCounts(profile, targetId);
    }

    private UserProfileResponse wrapWithCounts(ProfileResponse profile, Long userId) {
        long followers = followRepository.countByIdFolloweeId(userId);
        long following = followRepository.countByIdFollowerId(userId);
        // Mentor rating summary (#237). Mentees never accumulate rows in
        // mentor_ratings, so the aggregate returns (null, 0) for them; we
        // still set the fields uniformly so the response shape is stable.
        UserRatingSummary rating = mentorRatingService.aggregateForMentor(userId);
        if (profile instanceof UserResponse base) {
            base.setAverageRating(rating.averageRating());
            base.setRatingCount(rating.ratingCount());
        }
        return new UserProfileResponse(profile, followers, following);
    }

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

        return mapToResponse(target);
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

    private ProfileResponse mapToResponse(User user) {
        if (user instanceof Mentor mentor) {
            return MentorResponse.from(mentor);
        } else if (user instanceof Mentee mentee) {
            return MenteeResponse.from(mentee);
        } else if (user instanceof Admin) {
            // Defensive: admins are filtered upstream via findAllNonAdmins and
            // rejected explicitly in getProfileById, so this path should be
            // unreachable. Throw the same 403 mapping if a future caller forgets.
            throw new ProfileNotVisibleException("Admin profile is not visible");
        }
        throw new IllegalStateException("Unknown user type: " + user.getClass().getSimpleName());
    }

    private void applyMentorFields(Mentor mentor, MentorProfileRequest request) {
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
    }

}
