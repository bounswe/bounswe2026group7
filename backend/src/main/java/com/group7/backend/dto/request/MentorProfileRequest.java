package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Size(max = 200, message = "Expertise must not exceed 200 characters")
    @Schema(description = "Expertise summary", example = "Backend Development")
    private String expertise;

    @Size(max = 200, message = "Affiliation must not exceed 200 characters")
    @Schema(description = "Affiliation", example = "Bogazici University")
    private String affiliation;

    @Size(max = 20, message = "Cannot have more than 20 interests")
    @Schema(description = "Interests", example = "[\"AI\", \"Systems\"]")
    private List<@Size(max = 100, message = "Each interest must not exceed 100 characters") String> interests;

    @Min(value = 0, message = "Max mentee capacity must be at least 0")
    @Schema(description = "Maximum number of mentees. 0 = temporarily not accepting.", example = "3")
    private Integer maxMenteeCapacity;

    @Size(max = 20, message = "Cannot have more than 20 preferred skills")
    @Schema(description = "Preferred mentee skills", example = "[\"Java\", \"Python\"]")
    private List<@Size(max = 100, message = "Each skill must not exceed 100 characters") String> preferredMenteeSkills;

    @Size(max = 100, message = "Preferred mentee major must not exceed 100 characters")
    @Schema(description = "Preferred mentee major", example = "Computer Engineering")
    private String preferredMenteeMajor;

    @Size(max = 500, message = "Mentoring goals must not exceed 500 characters")
    @Schema(description = "Mentoring goals", example = "Help students with career guidance")
    private String mentoringGoals;

    @Min(value = 1, message = "Mentorship duration must be at least 1")
    @Schema(description = "Mentorship duration in months", example = "3")
    private Integer mentorshipDuration;
}
