package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mentee profile response")
public class MenteeResponse {
    @Schema(description = "Mentee id", example = "1")
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String profilePhoto;
    private Boolean isEmailVerified;
    private LocalDateTime createdAt;
    private Boolean profileVisibility;
    private String goals;
    private String major;
    private List<String> interests;
    private String careerInterest;
    private List<String> skills;
    private String meetingFreqPref;
    private String backgroundInfo;
    private Integer cancelCount;
}
