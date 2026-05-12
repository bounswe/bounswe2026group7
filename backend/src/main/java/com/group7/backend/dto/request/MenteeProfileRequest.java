package com.group7.backend.dto.request;

import com.group7.backend.entity.TaggedTermLists;
import com.group7.backend.validation.ValidEscoOrWikidataUri;
import com.group7.backend.validation.ValidEscoUri;
import com.group7.backend.validation.ValidIscedFUri;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mentee profile update payload (includes common fields)")
public class MenteeProfileRequest extends EditProfileRequest {

    @Schema(description = "Profile visibility. false = private.", example = "true")
    private Boolean profileVisibility;

    @Size(max = 500, message = "Goals must not exceed 500 characters")
    @Schema(description = "Learning goals", example = "Learn backend development")
    private String goals;

    @Size(max = 100, message = "Major must not exceed 100 characters")
    @Schema(description = "Major / field of study", example = "Computer Engineering")
    private String major;

    @ValidIscedFUri
    @Size(max = 255)
    @Schema(description = "Optional ISCED-F URI for the major",
            example = "http://data.europa.eu/esco/isced-f/0613")
    private String majorUri;

    @Size(max = 20, message = "Cannot have more than 20 interests")
    @Schema(description = "Interests", example = "[\"AI\", \"Web Dev\"]")
    private List<@Size(max = 100, message = "Each interest must not exceed 100 characters") String> interests;

    @Size(max = 20, message = "interestUris must align with interests")
    @Schema(description = "Optional canonical URIs for each interest, parallel to `interests`. "
            + "Each entry may be an ESCO skill URI, a Wikidata entity URI, or null.")
    private List<@ValidEscoOrWikidataUri @Size(max = 255) String> interestUris;

    @Size(max = 200, message = "Career interest must not exceed 200 characters")
    @Schema(description = "Career interest", example = "Data Science")
    private String careerInterest;

    @ValidEscoUri
    @Size(max = 255)
    @Schema(description = "Optional ESCO skill URI for the career interest",
            example = "http://data.europa.eu/esco/skill/ccd0a1d9-afda-43d9-b901-96344886e14d")
    private String careerInterestUri;

    @Size(max = 20, message = "Cannot have more than 20 skills")
    @Schema(description = "Skills", example = "[\"Python\", \"SQL\"]")
    private List<@Size(max = 100, message = "Each skill must not exceed 100 characters") String> skills;

    @Size(max = 20, message = "skillUris must align with skills")
    @Schema(description = "Optional ESCO skill URIs for each skill, parallel to `skills`.")
    private List<@ValidEscoUri @Size(max = 255) String> skillUris;

    @Size(max = 50, message = "Meeting frequency must not exceed 50 characters")
    @Schema(description = "Meeting frequency preference", example = "Weekly")
    private String meetingFreqPref;

    @Size(max = 500, message = "Background info must not exceed 500 characters")
    @Schema(description = "Background information", example = "2nd year student interested in AI")
    private String backgroundInfo;

    @Size(max = 200, message = "Affiliation must not exceed 200 characters")
    @Schema(description = "Affiliation (university/company)", example = "Bogazici University")
    private String affiliation;

    /**
     * Each parallel URI list must align with its label list when supplied.
     * A null URI list means "no URIs for any entry"; otherwise lengths must
     * match so the label and URI for each entry come from the same index.
     */
    @AssertTrue(message = "URI lists must have the same length as their corresponding label lists")
    public boolean isUriListsAligned() {
        return TaggedTermLists.aligned(interests, interestUris)
                && TaggedTermLists.aligned(skills, skillUris);
    }
}
