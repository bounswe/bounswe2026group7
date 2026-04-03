package com.group7.backend.dto.response;

import com.group7.backend.entity.Mentor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Privacy-safe mentor match result with compatibility score")
public class MentorMatchResponse {

    @Schema(description = "Mentor user ID", example = "42")
    private Long id;

    @Schema(description = "First name only (last name hidden for privacy)", example = "Ahmet")
    private String firstName;

    @Schema(description = "Short bio", example = "Experienced software engineer with 10+ years in industry")
    private String bio;

    @Schema(description = "Mentoring field", example = "Computer Science")
    private String field;

    @Schema(description = "Expertise summary", example = "Backend Development")
    private String expertise;

    @Schema(description = "Affiliation (university/company)", example = "Bogazici University")
    private String affiliation;

    @Schema(description = "List of interests", example = "[\"AI\", \"Systems\"]")
    private List<String> interests;

    @Schema(description = "Preferred mentee skills", example = "[\"Java\", \"Python\"]")
    private List<String> preferredMenteeSkills;

    @Schema(description = "Preferred mentee major", example = "Computer Engineering")
    private String preferredMenteeMajor;

    @Schema(description = "Mentoring goals", example = "Help students with career guidance")
    private String mentoringGoals;

    @Schema(description = "Mentorship duration in months", example = "3")
    private Integer mentorshipDuration;

    @Schema(description = "Compatibility score (higher = better match)", example = "17")
    private int matchScore;

    public static MentorMatchResponse from(Mentor mentor, int score) {
        MentorMatchResponse r = new MentorMatchResponse();
        r.setId(mentor.getId());
        r.setFirstName(mentor.getFirstName());
        r.setBio(mentor.getBio());
        r.setField(mentor.getField());
        r.setExpertise(mentor.getExpertise());
        r.setAffiliation(mentor.getAffiliation());
        r.setInterests(mentor.getInterests());
        r.setPreferredMenteeSkills(mentor.getPreferredMenteeSkills());
        r.setPreferredMenteeMajor(mentor.getPreferredMenteeMajor());
        r.setMentoringGoals(mentor.getMentoringGoals());
        r.setMentorshipDuration(mentor.getMentorshipDuration());
        r.setMatchScore(score);
        return r;
    }
}
