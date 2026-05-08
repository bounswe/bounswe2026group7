package com.group7.backend.dto.response;

import com.group7.backend.entity.MilestoneStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class MilestoneSummaryResponse {
    private Long id;
    private String title;
    private OffsetDateTime targetDate;
    private MilestoneStatus status;
    private Integer orderIndex;
}
