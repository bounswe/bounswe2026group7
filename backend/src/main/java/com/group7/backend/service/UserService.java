package com.group7.backend.service;

import com.group7.backend.dto.request.EditProfileRequest;
import com.group7.backend.dto.request.MenteeProfileRequest;
import com.group7.backend.dto.request.MentorProfileRequest;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
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
    private final FileStorageService fileStorageService;

    public UserService(UserRepository userRepository, MentorRepository mentorRepository,
                       MenteeRepository menteeRepository, FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.mentorRepository = mentorRepository;
        this.menteeRepository = menteeRepository;
        this.fileStorageService = fileStorageService;
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
        if (request.getExpertise() != null) {
            mentor.setExpertise(request.getExpertise());
        }
        if (request.getAffiliation() != null) {
            mentor.setAffiliation(request.getAffiliation());
        }
        if (request.getInterests() != null) {
            mentor.setInterests(request.getInterests());
        }
        if (request.getMaxMenteeCapacity() != null) {
            mentor.setMaxMenteeCapacity(request.getMaxMenteeCapacity());
        }
        if (request.getPreferredMenteeSkills() != null) {
            mentor.setPreferredMenteeSkills(request.getPreferredMenteeSkills());
        }
        if (request.getPreferredMenteeMajor() != null) {
            mentor.setPreferredMenteeMajor(request.getPreferredMenteeMajor());
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
        if (request.getInterests() != null) {
            mentee.setInterests(request.getInterests());
        }
        if (request.getCareerInterest() != null) {
            mentee.setCareerInterest(request.getCareerInterest());
        }
        if (request.getSkills() != null) {
            mentee.setSkills(request.getSkills());
        }
        if (request.getMeetingFreqPref() != null) {
            mentee.setMeetingFreqPref(request.getMeetingFreqPref());
        }
        if (request.getBackgroundInfo() != null) {
            mentee.setBackgroundInfo(request.getBackgroundInfo());
        }
    }
}
