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
public class MentorMatchResponse implements MatchSummary {

    @Schema(description = "Mentor user ID", example = "42")
    private Long id;

    @Schema(description = "First name only (last name hidden for privacy)", example = "Ahmet")
    private String firstName;

    @Schema(description = "Short bio", example = "Experienced software engineer with 10+ years in industry")
    private String bio;

    @Schema(description = "Mentoring field", example = "Computer Science")
    private String field;

    @Schema(description = "Optional ISCED-F URI for the mentoring field")
    private String fieldUri;

    @Schema(description = "Expertise summary", example = "Backend Development")
    private String expertise;

    @Schema(description = "Optional ESCO skill URI for the expertise")
    private String expertiseUri;

    @Schema(description = "Affiliation (university/company)", example = "Bogazici University")
    private String affiliation;

    @Schema(description = "List of interests", example = "[\"AI\", \"Systems\"]")
    private List<String> interests;

    @Schema(description = "Canonical URIs parallel to `interests`. Entries may be ESCO, Wikidata, or null.")
    private List<String> interestUris;

    @Schema(description = "Preferred mentee skills", example = "[\"Java\", \"Python\"]")
    private List<String> preferredMenteeSkills;

    @Schema(description = "ESCO skill URIs parallel to `preferredMenteeSkills`.")
    private List<String> preferredMenteeSkillUris;

    @Schema(description = "Preferred mentee major", example = "Computer Engineering")
    private String preferredMenteeMajor;

    @Schema(description = "Optional ISCED-F URI for the preferred mentee major")
    private String preferredMenteeMajorUri;

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
        r.setFieldUri(mentor.getFieldUri());
        r.setExpertise(mentor.getExpertise());
        r.setExpertiseUri(mentor.getExpertiseUri());
        r.setAffiliation(mentor.getAffiliation());
        r.setInterests(mentor.getInterests());
        r.setInterestUris(mentor.getInterestUris());
        r.setPreferredMenteeSkills(mentor.getPreferredMenteeSkills());
        r.setPreferredMenteeSkillUris(mentor.getPreferredMenteeSkillUris());
        r.setPreferredMenteeMajor(mentor.getPreferredMenteeMajor());
        r.setPreferredMenteeMajorUri(mentor.getPreferredMenteeMajorUri());
        r.setMentoringGoals(mentor.getMentoringGoals());
        r.setMentorshipDuration(mentor.getMentorshipDuration());
        r.setMatchScore(score);
        return r;
    }
}
