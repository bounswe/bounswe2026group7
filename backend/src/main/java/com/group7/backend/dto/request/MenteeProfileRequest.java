package com.group7.backend.dto.request;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenteeProfileRequest {
    private Boolean profileVisibility;
    private String goals;
    private String major;
    private List<String> interests;
    private String careerInterest;
    private List<String> skills;
    private String meetingFreqPref;
    private String backgroundInfo;
}
