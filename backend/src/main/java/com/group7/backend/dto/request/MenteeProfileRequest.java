package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mentee profile payload")
public class MenteeProfileRequest {
    @Schema(description = "Whether the profile is visible to others")
    private Boolean profileVisibility;

    @Schema(description = "Goals")
    private String goals;

    @Schema(description = "Major")
    private String major;

    @Schema(description = "Interests")
    private List<String> interests;

    @Schema(description = "Career interest")
    private String careerInterest;

    @Schema(description = "Skills")
    private List<String> skills;

    @Schema(description = "Meeting frequency preference")
    private String meetingFreqPref;

    @Schema(description = "Background information")
    private String backgroundInfo;
}
