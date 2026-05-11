package com.group7.backend.dto.response;

import com.group7.backend.entity.UserDevice;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Confirmation of a registered FCM device token.")
public class UserDeviceResponse {

    private Long id;
    private String token;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastSeenAt;

    public static UserDeviceResponse from(UserDevice device) {
        return new UserDeviceResponse(
                device.getId(),
                device.getToken(),
                device.getCreatedAt(),
                device.getLastSeenAt());
    }
}
