package com.group7.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String profilePhoto;
    private Boolean isEmailVerified;
    private LocalDateTime createdAt;
    private String role;
}
