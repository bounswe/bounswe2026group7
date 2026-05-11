package com.group7.backend.dto.response;

import com.group7.backend.entity.Mentee;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.beans.BeanUtils;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Mentee profile response")
public final class MenteeResponse extends UserResponse implements ProfileResponse {

    @Schema(description = "Whether the profile is visible to other users", example = "true")
    private Boolean profileVisibility;

    @Schema(description = "Learning goals", example = "Learn backend development and system design")
    private String goals;

    @Schema(description = "Major / field of study", example = "Computer Engineering")
    private String major;

    @Schema(description = "Optional ISCED-F URI for the major",
            example = "http://data.europa.eu/esco/isced-f/0613")
    private String majorUri;

    @Schema(description = "List of interests", example = "[\"AI\", \"Web Development\"]")
    private List<String> interests;

    @Schema(description = "Canonical URIs for each interest, parallel to `interests`. "
            + "Entries may be ESCO, Wikidata, or null.")
    private List<String> interestUris;

    @Schema(description = "Career interest", example = "Data Science")
    private String careerInterest;

    @Schema(description = "Optional ESCO skill URI for the career interest",
            example = "http://data.europa.eu/esco/skill/ccd0a1d9-afda-43d9-b901-96344886e14d")
    private String careerInterestUri;

    @Schema(description = "List of skills", example = "[\"Python\", \"SQL\"]")
    private List<String> skills;

    @Schema(description = "ESCO skill URIs parallel to `skills`.")
    private List<String> skillUris;

    @Schema(description = "Meeting frequency preference", example = "Weekly")
    private String meetingFreqPref;

    @Schema(description = "Background information", example = "2nd year student interested in AI research")
    private String backgroundInfo;

    @Schema(description = "Number of cancelled mentorships", example = "0")
    private Integer cancelCount;

    public static MenteeResponse from(Mentee mentee) {
        MenteeResponse response = new MenteeResponse();
        BeanUtils.copyProperties(mentee, response);
        response.setRole("MENTEE");
        return response;
    }
}
