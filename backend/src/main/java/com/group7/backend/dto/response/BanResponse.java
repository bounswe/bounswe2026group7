package com.group7.backend.dto.response;

import com.group7.backend.entity.Ban;
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
@Schema(description = "Ban record returned by admin endpoints and the 403 body when a banned user attempts a gated action.")
public class BanResponse {

    private Long id;
    private Long userId;
    private String reason;
    private int banCount;
    private OffsetDateTime expiresAt;
    private OffsetDateTime liftedAt;
    private Long liftedByAdminId;
    private OffsetDateTime createdAt;

    public static BanResponse from(Ban ban) {
        return new BanResponse(
                ban.getId(),
                ban.getUser().getId(),
                ban.getReason(),
                ban.getBanCount(),
                ban.getExpiresAt(),
                ban.getLiftedAt(),
                ban.getLiftedByAdminId(),
                ban.getCreatedAt());
    }
}
