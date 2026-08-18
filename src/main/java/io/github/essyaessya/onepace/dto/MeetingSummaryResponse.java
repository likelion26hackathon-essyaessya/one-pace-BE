package io.github.essyaessya.onepace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "회의록 요약 결과")
public record MeetingSummaryResponse(
        @Schema(description = "요약 로그 ID") Long id,
        @Schema(description = "회의 요약문") String summary,
        @Schema(description = "결정 사항 목록") List<DecisionDto> decisions,
        @Schema(description = "액션 아이템 목록") List<ActionItemDto> actionItems
) {}
