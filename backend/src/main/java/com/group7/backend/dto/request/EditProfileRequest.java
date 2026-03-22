package com.group7.backend.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EditProfileRequest {
    private String firstName;
    private String lastName;
    private String profilePhoto;
}
