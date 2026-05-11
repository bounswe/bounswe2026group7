package com.group7.backend.dto.response;

import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.ScoreResult;
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

    @Schema(description = "Human-readable factors that contributed to the match score. "
            + "Examples: 'shared-interest:Java', 'major-exact', 'availability:8h', "
            + "'nearby:23km', 'diverse-pick'. Frontend formats these into 'why "
            + "recommended?' cards (spec 1.1.2.5).",
            example = "[\"interest-match:AI\",\"major-exact\",\"availability:6h\"]")
    private List<String> factors = List.of();

    @Schema(description = "Great-circle distance (km) between mentor and mentee. "
            + "Null when either side hasn't set lat/lon. Drives the optional "
            + "?maxDistanceKm filter and the 'nearby' display on the UI card.",
            example = "23.4", nullable = true)
    private Double distanceKm;

    @Schema(description = "Optional LLM-generated one-sentence prose explanation of "
            + "why this mentor is a fit (spec 1.1.2.5). Null when the LLM layer is "
            + "disabled, missing API key, daily cap exceeded, or the call failed — "
            + "frontend falls back to formatting the deterministic factor strings.",
            example = "Ahmet's React expertise aligns with your goal of learning modern frontend.",
            nullable = true)
    private String explanation;

    public static MentorMatchResponse from(Mentor mentor, ScoreResult scoreResult) {
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
        r.setMatchScore(scoreResult.score());
        r.setFactors(scoreResult.factors());
        return r;
    }

    /**
     * Convenience overload preserving the legacy {@code (Mentor, int)} call shape
     * for callers that haven't migrated to {@link ScoreResult} yet. New callers
     * should use {@link #from(Mentor, ScoreResult)}.
     */
    public static MentorMatchResponse from(Mentor mentor, int score) {
        return from(mentor, ScoreResult.of(score));
    }
}
