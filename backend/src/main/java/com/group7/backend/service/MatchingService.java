package com.group7.backend.service;

import com.group7.backend.dto.response.MenteeCandidateResponse;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.MatchingNotAllowedException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class MatchingService {

    private final MenteeRepository menteeRepository;
    private final MentorRepository mentorRepository;
    private final NotificationEventPublisher notificationEventPublisher;

    public MatchingService(MenteeRepository menteeRepository,
                           MentorRepository mentorRepository,
                           NotificationEventPublisher notificationEventPublisher) {
        this.menteeRepository = menteeRepository;
        this.mentorRepository = mentorRepository;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<MentorMatchResponse> getTopMentors(Long menteeId, String keyword) {
        Mentee mentee = menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));

        if (mentee.getActiveMentorId() != null) {
            throw new MatchingNotAllowedException("You already have an active mentor");
        }

        List<Mentor> mentors = mentorRepository.findAll();

        List<MentorMatchResponse> matches = mentors.stream()
                .filter(m -> m.getCurrentMenteeCount() < m.getMaxMenteeCapacity())
                .filter(m -> matchesKeyword(m, keyword))
                .map(m -> MentorMatchResponse.from(m, calculateScore(m, mentee)))
                .sorted(Comparator.comparingInt(MentorMatchResponse::getMatchScore).reversed())
                .limit(5)
                .toList();

        if (!matches.isEmpty()) {
            notificationEventPublisher.publishMatchFound(menteeId, matches.get(0).getFirstName());
        }

        return matches;
    }

    @Transactional(readOnly = true)
    public List<MenteeCandidateResponse> getCandidateMentees(Long mentorId, String keyword) {
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

        return candidates;
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
}
