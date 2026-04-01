package com.group7.backend.service;

import com.group7.backend.dto.request.UpdateProfileRequest;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final MenteeRepository menteeRepository;

    public UserService(UserRepository userRepository, MentorRepository mentorRepository, MenteeRepository menteeRepository) {
        this.userRepository = userRepository;
        this.mentorRepository = mentorRepository;
        this.menteeRepository = menteeRepository;
    }

    // ── Existing methods ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProfileResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public Mentor createMentor(Mentor mentor) {
        return mentorRepository.save(mentor);
    }

    public Mentee createMentee(Mentee mentee) {
        return menteeRepository.save(mentee);
    }

    @Transactional(readOnly = true)
    public List<MentorResponse> getAllMentors() {
        return mentorRepository.findAll().stream()
                .map(MentorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenteeResponse> getAllMentees() {
        return menteeRepository.findAll().stream()
                .map(MenteeResponse::from)
                .toList();
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
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

        return mapToResponse(target);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Apply common fields (skip nulls)
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getProfilePhoto() != null) {
            user.setProfilePhoto(request.getProfilePhoto());
        }

        // Apply role-specific fields
        if (user instanceof Mentor mentor) {
            applyMentorFields(mentor, request);
        } else if (user instanceof Mentee mentee) {
            applyMenteeFields(mentee, request);
        }

        User saved = userRepository.save(user);
        return mapToResponse(saved);
    }

    // ── Private helpers ─────────────────────────────────────

    private ProfileResponse mapToResponse(User user) {
        if (user instanceof Mentor mentor) {
            return MentorResponse.from(mentor);
        } else if (user instanceof Mentee mentee) {
            return MenteeResponse.from(mentee);
        }
        throw new IllegalStateException("Unknown user type: " + user.getClass().getSimpleName());
    }

    private void applyMentorFields(Mentor mentor, UpdateProfileRequest request) {
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

    private void applyMenteeFields(Mentee mentee, UpdateProfileRequest request) {
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
