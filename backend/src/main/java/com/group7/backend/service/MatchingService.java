package com.group7.backend.service;

import com.group7.backend.dto.response.MenteeCandidateResponse;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.MatchingNotAllowedException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MentorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class MatchingService {

    private final MenteeRepository menteeRepository;
    private final MentorRepository mentorRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;
    private final NotificationEventPublisher notificationEventPublisher;

    public MatchingService(MenteeRepository menteeRepository,
                           MentorRepository mentorRepository,
                           AvailabilitySlotRepository availabilitySlotRepository,
                           MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository,
                           NotificationEventPublisher notificationEventPublisher) {
        this.menteeRepository = menteeRepository;
        this.mentorRepository = mentorRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.menteeAvailabilitySlotRepository = menteeAvailabilitySlotRepository;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<MentorMatchResponse> getTopMentors(Long menteeId, String keyword, Pageable pageable) {
        Mentee mentee = menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));

        if (mentee.getActiveMentorId() != null) {
            throw new MatchingNotAllowedException("You already have an active mentor");
        }

        List<Mentor> mentors = mentorRepository.findAll();

        List<MentorMatchResponse> matches = mentors.stream()
                .filter(m -> m.getCurrentMenteeCount() < m.getMaxMenteeCapacity())
                .filter(m -> matchesKeyword(m, keyword))
                .map(m -> MentorMatchResponse.from(m,
                        calculateScore(m, mentee) + calculateAvailabilityScore(m.getId(), mentee.getId())))
                .sorted(Comparator.comparingInt(MentorMatchResponse::getMatchScore).reversed())
                .toList();

        if (!matches.isEmpty()) {
            notificationEventPublisher.publishMatchFound(menteeId, matches.get(0).getFirstName());
        }

        return paginateList(matches, pageable);
    }

    @Transactional(readOnly = true)
    public Page<MenteeCandidateResponse> getCandidateMentees(Long mentorId, String keyword, Pageable pageable) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));

        if (mentor.getCurrentMenteeCount() >= mentor.getMaxMenteeCapacity()) {
            throw new MatchingNotAllowedException("You have reached your maximum mentee capacity");
        }

        List<Mentee> mentees = menteeRepository.findAll();

        List<MenteeCandidateResponse> candidates = mentees.stream()
                .filter(m -> m.getActiveMentorId() == null)
                .filter(m -> matchesMentorPreferences(mentor, m))
                .filter(m -> matchesMenteeKeyword(m, keyword))
                .map(MenteeCandidateResponse::from)
                .toList();

        if (!candidates.isEmpty()) {
            notificationEventPublisher.publishMatchFound(mentorId, candidates.get(0).getFirstName());
        }

        return paginateList(candidates, pageable);
    }

    boolean matchesMentorPreferences(Mentor mentor, Mentee mentee) {
        List<String> mentorInterests = nullSafe(mentor.getInterests());
        List<String> menteeInterests = nullSafe(mentee.getInterests());
        for (String interest : menteeInterests) {
            if (containsIgnoreCase(mentorInterests, interest)) {
                return true;
            }
        }

        List<String> preferredSkills = nullSafe(mentor.getPreferredMenteeSkills());
        for (String skill : nullSafe(mentee.getSkills())) {
            if (containsIgnoreCase(preferredSkills, skill)) {
                return true;
            }
        }

        if (mentee.getMajor() != null && mentor.getPreferredMenteeMajor() != null
                && mentee.getMajor().equalsIgnoreCase(mentor.getPreferredMenteeMajor())) {
            return true;
        }

        if (mentee.getMajor() != null && mentor.getField() != null
                && mentee.getMajor().equalsIgnoreCase(mentor.getField())) {
            return true;
        }

        return false;
    }

    private boolean matchesMenteeKeyword(Mentee mentee, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String kw = keyword.toLowerCase();
        return containsSubstring(mentee.getGoals(), kw)
                || containsSubstring(mentee.getMajor(), kw)
                || containsSubstring(mentee.getCareerInterest(), kw)
                || containsSubstring(mentee.getBackgroundInfo(), kw)
                || listContainsSubstring(mentee.getInterests(), kw)
                || listContainsSubstring(mentee.getSkills(), kw);
    }

    int calculateScore(Mentor mentor, Mentee mentee) {
        int score = 0;

        // Overlapping interests: +3 each
        List<String> mentorInterests = nullSafe(mentor.getInterests());
        List<String> menteeInterests = nullSafe(mentee.getInterests());
        for (String interest : menteeInterests) {
            if (containsIgnoreCase(mentorInterests, interest)) {
                score += 3;
            }
        }

        // Each mentee skill in mentor's preferredMenteeSkills: +3 each
        List<String> preferredSkills = nullSafe(mentor.getPreferredMenteeSkills());
        for (String skill : nullSafe(mentee.getSkills())) {
            if (containsIgnoreCase(preferredSkills, skill)) {
                score += 3;
            }
        }

        // Mentee major matches preferredMenteeMajor: +5
        if (mentee.getMajor() != null && mentor.getPreferredMenteeMajor() != null
                && mentee.getMajor().equalsIgnoreCase(mentor.getPreferredMenteeMajor())) {
            score += 5;
        }

        // Mentee major matches mentor field: +3
        if (mentee.getMajor() != null && mentor.getField() != null
                && mentee.getMajor().equalsIgnoreCase(mentor.getField())) {
            score += 3;
        }

        // Mentoring goals contains mentee goals keywords: +2 per word
        if (mentee.getGoals() != null && mentor.getMentoringGoals() != null) {
            String[] goalWords = mentee.getGoals().toLowerCase().split("\\s+");
            String lowerMentoringGoals = mentor.getMentoringGoals().toLowerCase();
            for (String word : goalWords) {
                if (word.length() > 3 && lowerMentoringGoals.contains(word)) {
                    score += 2;
                }
            }
        }

        // Mentee careerInterest / skills overlap with mentor expertise: +2 per word
        if (mentor.getExpertise() != null) {
            String lowerExpertise = mentor.getExpertise().toLowerCase();
            if (mentee.getCareerInterest() != null) {
                String[] careerWords = mentee.getCareerInterest().toLowerCase().split("\\s+");
                for (String word : careerWords) {
                    if (word.length() > 3 && lowerExpertise.contains(word)) {
                        score += 2;
                    }
                }
            }
        }

        return score;
    }

    int calculateAvailabilityScore(Long mentorId, Long menteeId) {
        if (mentorId == null || menteeId == null) {
            return 0;
        }

        var mentorSlots = availabilitySlotRepository.findByMentorId(mentorId);
        var menteeSlots = menteeAvailabilitySlotRepository.findByMenteeId(menteeId);

        if (mentorSlots.isEmpty() || menteeSlots.isEmpty()) {
            return 0;
        }

        long overlapMinutes = 0;
        for (var mentorSlot : mentorSlots) {
            for (MenteeAvailabilitySlot menteeSlot : menteeSlots) {
                if (mentorSlot.getDayOfWeek() != menteeSlot.getDayOfWeek()) {
                    continue;
                }
                overlapMinutes += overlapMinutes(
                        mentorSlot.getStartTime(),
                        mentorSlot.getEndTime(),
                        menteeSlot.getStartTime(),
                        menteeSlot.getEndTime());
            }
        }

        return (int) Math.min(12, overlapMinutes / 30);
    }

    private long overlapMinutes(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        LocalTime start = start1.isAfter(start2) ? start1 : start2;
        LocalTime end = end1.isBefore(end2) ? end1 : end2;
        if (!start.isBefore(end)) {
            return 0;
        }
        return java.time.Duration.between(start, end).toMinutes();
    }

    private boolean matchesKeyword(Mentor mentor, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String kw = keyword.toLowerCase();
        return containsSubstring(mentor.getExpertise(), kw)
                || containsSubstring(mentor.getField(), kw)
                || containsSubstring(mentor.getMentoringGoals(), kw)
                || listContainsSubstring(mentor.getInterests(), kw)
                || listContainsSubstring(mentor.getPreferredMenteeSkills(), kw);
    }

    private boolean containsSubstring(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword);
    }

    private boolean listContainsSubstring(List<String> items, String keyword) {
        if (items == null) return false;
        return items.stream().anyMatch(s -> s != null && s.toLowerCase().contains(keyword));
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        if (value == null) return false;
        return list.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(value));
    }

    private List<String> nullSafe(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private <T> Page<T> paginateList(List<T> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<T> pageContent = start >= list.size()
                ? List.of()
                : list.subList(start, end);
        return new PageImpl<>(pageContent, pageable, list.size());
    }
}
