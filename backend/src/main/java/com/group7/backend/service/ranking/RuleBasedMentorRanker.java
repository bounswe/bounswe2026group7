package com.group7.backend.service.ranking;

import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/**
 * The legacy weighted-score matching algorithm, extracted verbatim from
 * {@code MatchingService} so a future AI ranker can replace it without
 * touching the service. Behaviour-identical to the pre-#262 implementation
 * — verified by the existing 47-case {@code MatchingServiceTest} which now
 * exercises this class through the service.
 *
 * <p>Scoring weights (preserved as-is):
 * <ul>
 *   <li>Each overlapping interest: +3</li>
 *   <li>Each mentee skill in mentor's preferred-mentee skills: +3</li>
 *   <li>Mentee major equals mentor's preferred major: +5</li>
 *   <li>Mentee major equals mentor's field: +3</li>
 *   <li>Each mentee-goal word (length > 3) appearing in mentor's mentoring goals: +2</li>
 *   <li>Each mentee-career-interest word (length > 3) appearing in mentor's expertise: +2</li>
 *   <li>Availability overlap minutes / 30, capped at 12</li>
 * </ul>
 */
@Component
public class RuleBasedMentorRanker implements MentorRanker {

    @Override
    public int score(Mentor mentor,
                     Mentee mentee,
                     List<AvailabilitySlot> mentorSlots,
                     List<MenteeAvailabilitySlot> menteeSlots) {
        return profileScore(mentor, mentee) + availabilityScore(mentorSlots, menteeSlots);
    }

    private static int profileScore(Mentor mentor, Mentee mentee) {
        int score = 0;

        List<String> mentorInterests = nullSafe(mentor.getInterests());
        List<String> menteeInterests = nullSafe(mentee.getInterests());
        for (String interest : menteeInterests) {
            if (containsIgnoreCase(mentorInterests, interest)) {
                score += 3;
            }
        }

        List<String> preferredSkills = nullSafe(mentor.getPreferredMenteeSkills());
        for (String skill : nullSafe(mentee.getSkills())) {
            if (containsIgnoreCase(preferredSkills, skill)) {
                score += 3;
            }
        }

        if (mentee.getMajor() != null && mentor.getPreferredMenteeMajor() != null
                && mentee.getMajor().equalsIgnoreCase(mentor.getPreferredMenteeMajor())) {
            score += 5;
        }

        if (mentee.getMajor() != null && mentor.getField() != null
                && mentee.getMajor().equalsIgnoreCase(mentor.getField())) {
            score += 3;
        }

        if (mentee.getGoals() != null && mentor.getMentoringGoals() != null) {
            String[] goalWords = mentee.getGoals().toLowerCase().split("\\s+");
            String lowerMentoringGoals = mentor.getMentoringGoals().toLowerCase();
            for (String word : goalWords) {
                if (word.length() > 3 && lowerMentoringGoals.contains(word)) {
                    score += 2;
                }
            }
        }

        if (mentor.getExpertise() != null && mentee.getCareerInterest() != null) {
            String lowerExpertise = mentor.getExpertise().toLowerCase();
            String[] careerWords = mentee.getCareerInterest().toLowerCase().split("\\s+");
            for (String word : careerWords) {
                if (word.length() > 3 && lowerExpertise.contains(word)) {
                    score += 2;
                }
            }
        }

        return score;
    }

    private static int availabilityScore(List<AvailabilitySlot> mentorSlots,
                                         List<MenteeAvailabilitySlot> menteeSlots) {
        if (mentorSlots.isEmpty() || menteeSlots.isEmpty()) {
            return 0;
        }

        long overlapMinutes = 0;
        for (AvailabilitySlot mentorSlot : mentorSlots) {
            for (MenteeAvailabilitySlot menteeSlot : menteeSlots) {
                if (mentorSlot.getDayOfWeek() != menteeSlot.getDayOfWeek()) {
                    continue;
                }
                overlapMinutes += overlapMinutes(
                        mentorSlot.getStartTime(), mentorSlot.getEndTime(),
                        menteeSlot.getStartTime(), menteeSlot.getEndTime());
            }
        }

        return (int) Math.min(12, overlapMinutes / 30);
    }

    private static long overlapMinutes(LocalTime aStart, LocalTime aEnd,
                                       LocalTime bStart, LocalTime bEnd) {
        LocalTime start = aStart.isAfter(bStart) ? aStart : bStart;
        LocalTime end = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        if (!start.isBefore(end)) {
            return 0;
        }
        return Duration.between(start, end).toMinutes();
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (value == null) return false;
        return list.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(value));
    }

    private static List<String> nullSafe(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
