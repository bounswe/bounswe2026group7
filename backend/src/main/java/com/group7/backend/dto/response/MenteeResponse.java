package com.group7.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenteeResponse {
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
