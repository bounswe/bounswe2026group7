package com.group7.backend.dto.response;

import com.group7.backend.entity.Mentee;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Privacy-safe mentee candidate visible to mentors during matching")
public class MenteeCandidateResponse {

    @Schema(description = "Mentee user ID", example = "7")
    private Long id;

    @Schema(description = "First name only (last name hidden for privacy)", example = "Elif")
    private String firstName;

    @Schema(description = "Learning goals", example = "Improve backend development skills")
    private String goals;

    @Schema(description = "Major / field of study", example = "Computer Science")
    private String major;

    @Schema(description = "Optional ISCED-F URI for the major")
    private String majorUri;

    @Schema(description = "List of interests", example = "[\"AI\", \"Databases\"]")
    private List<String> interests;

    @Schema(description = "Canonical URIs parallel to `interests`. Entries may be ESCO, Wikidata, or null.")
    private List<String> interestUris;

    @Schema(description = "Career interest", example = "Backend Engineering")
    private String careerInterest;

    @Schema(description = "Optional ESCO skill URI for the career interest")
    private String careerInterestUri;

    @Schema(description = "List of skills", example = "[\"Java\", \"Python\"]")
    private List<String> skills;

    @Schema(description = "ESCO skill URIs parallel to `skills`.")
    private List<String> skillUris;

    @Schema(description = "Background information", example = "3rd year CS student at Bogazici")
    private String backgroundInfo;

    @Schema(description = "Preferred meeting frequency", example = "Weekly")
    private String meetingFreqPref;

    public static MenteeCandidateResponse from(Mentee mentee) {
        MenteeCandidateResponse r = new MenteeCandidateResponse();
        r.setId(mentee.getId());
        r.setFirstName(mentee.getFirstName());
        r.setGoals(mentee.getGoals());
        r.setMajor(mentee.getMajor());
        r.setMajorUri(mentee.getMajorUri());
        r.setInterests(mentee.getInterests());
        r.setInterestUris(mentee.getInterestUris());
        r.setCareerInterest(mentee.getCareerInterest());
        r.setCareerInterestUri(mentee.getCareerInterestUri());
        r.setSkills(mentee.getSkills());
        r.setSkillUris(mentee.getSkillUris());
        r.setBackgroundInfo(mentee.getBackgroundInfo());
        r.setMeetingFreqPref(mentee.getMeetingFreqPref());
        return r;
    }
}
