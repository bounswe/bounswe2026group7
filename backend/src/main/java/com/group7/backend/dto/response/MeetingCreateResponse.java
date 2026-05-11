package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Meeting creation result")
public class MeetingCreateResponse {

    @Schema(description = "Created meetings")
    private List<MeetingSummaryResponse> meetings;

    @Schema(description = "Warnings returned for scheduling")
    private List<String> warnings;
}
