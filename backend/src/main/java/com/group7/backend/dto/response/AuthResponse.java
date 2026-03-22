package com.group7.backend.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String sessionToken;
    private String role;
    private Long userId;
}
