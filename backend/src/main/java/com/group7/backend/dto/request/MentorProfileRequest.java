package com.group7.backend.dto.request;

import com.group7.backend.entity.TaggedTermLists;
import com.group7.backend.validation.ValidEscoOrWikidataUri;
import com.group7.backend.validation.ValidEscoUri;
import com.group7.backend.validation.ValidIscedFUri;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mentor profile update payload (includes common fields)")
public class MentorProfileRequest extends EditProfileRequest {

    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    @Schema(description = "Short bio", example = "Experienced software engineer with 10+ years in industry")
    private String bio;

    @Size(max = 100, message = "Field must not exceed 100 characters")
    @Schema(description = "Mentoring field", example = "Computer Science")
    private String field;

    @ValidIscedFUri
    @Size(max = 255)
    @Schema(description = "Optional ISCED-F URI for the mentoring field",
            example = "http://data.europa.eu/esco/isced-f/0613")
    private String fieldUri;

    @Size(max = 200, message = "Expertise must not exceed 200 characters")
    @Schema(description = "Expertise summary", example = "Backend Development")
    private String expertise;

    @ValidEscoUri
    @Size(max = 255)
    @Schema(description = "Optional ESCO skill URI for the expertise",
            example = "http://data.europa.eu/esco/skill/ccd0a1d9-afda-43d9-b901-96344886e14d")
    private String expertiseUri;

    @Size(max = 200, message = "Affiliation must not exceed 200 characters")
    @Schema(description = "Affiliation", example = "Bogazici University")
    private String affiliation;

    @Size(max = 20, message = "Cannot have more than 20 interests")
    @Schema(description = "Interests", example = "[\"AI\", \"Systems\"]")
    private List<@Size(max = 100, message = "Each interest must not exceed 100 characters") String> interests;

    @Size(max = 20, message = "interestUris must align with interests")
    @Schema(description = "Optional canonical URIs for each interest, parallel to `interests`. "
            + "Each entry may be an ESCO skill URI, a Wikidata entity URI, or null.")
    private List<@ValidEscoOrWikidataUri @Size(max = 255) String> interestUris;

    @Min(value = 0, message = "Max mentee capacity must be at least 0")
    @Schema(description = "Maximum number of mentees. 0 = temporarily not accepting.", example = "3")
    private Integer maxMenteeCapacity;

    @Size(max = 20, message = "Cannot have more than 20 preferred skills")
    @Schema(description = "Preferred mentee skills", example = "[\"Java\", \"Python\"]")
    private List<@Size(max = 100, message = "Each skill must not exceed 100 characters") String> preferredMenteeSkills;

    @Size(max = 20, message = "preferredMenteeSkillUris must align with preferredMenteeSkills")
    @Schema(description = "Optional ESCO skill URIs for each preferred mentee skill, parallel to "
            + "`preferredMenteeSkills`.")
    private List<@ValidEscoUri @Size(max = 255) String> preferredMenteeSkillUris;

    @Size(max = 100, message = "Preferred mentee major must not exceed 100 characters")
    @Schema(description = "Preferred mentee major", example = "Computer Engineering")
    private String preferredMenteeMajor;

    @ValidIscedFUri
    @Size(max = 255)
    @Schema(description = "Optional ISCED-F URI for the preferred mentee major",
            example = "http://data.europa.eu/esco/isced-f/0613")
    private String preferredMenteeMajorUri;

    @Size(max = 500, message = "Mentoring goals must not exceed 500 characters")
    @Schema(description = "Mentoring goals", example = "Help students with career guidance")
    private String mentoringGoals;

    @Min(value = 1, message = "Mentorship duration must be at least 1")
    @Schema(description = "Mentorship duration in months", example = "3")
    private Integer mentorshipDuration;

    /**
     * Each parallel URI list must align with its label list when supplied.
     * A null URI list means "no URIs for any entry"; otherwise lengths must
     * match so the label and URI for each entry come from the same index.
     */
    @AssertTrue(message = "URI lists must have the same length as their corresponding label lists")
    public boolean isUriListsAligned() {
        return TaggedTermLists.aligned(interests, interestUris)
                && TaggedTermLists.aligned(preferredMenteeSkills, preferredMenteeSkillUris);
    }
}
