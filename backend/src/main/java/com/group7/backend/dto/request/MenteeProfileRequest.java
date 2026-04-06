package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Size(max = 20, message = "Cannot have more than 20 interests")
    @Schema(description = "Interests", example = "[\"AI\", \"Web Dev\"]")
    private List<@Size(max = 100, message = "Each interest must not exceed 100 characters") String> interests;

    @Size(max = 200, message = "Career interest must not exceed 200 characters")
    @Schema(description = "Career interest", example = "Data Science")
    private String careerInterest;

    @Size(max = 20, message = "Cannot have more than 20 skills")
    @Schema(description = "Skills", example = "[\"Python\", \"SQL\"]")
    private List<@Size(max = 100, message = "Each skill must not exceed 100 characters") String> skills;

    @Size(max = 50, message = "Meeting frequency must not exceed 50 characters")
    @Schema(description = "Meeting frequency preference", example = "Weekly")
    private String meetingFreqPref;

    @Size(max = 500, message = "Background info must not exceed 500 characters")
    @Schema(description = "Background information", example = "2nd year student interested in AI")
    private String backgroundInfo;
}
