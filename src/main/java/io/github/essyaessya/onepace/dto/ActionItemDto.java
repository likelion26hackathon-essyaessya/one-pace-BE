package io.github.essyaessya.onepace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "액션 아이템")
public record ActionItemDto(
        @Schema(description = "할 일 제목") String title,
        @Schema(description = "담당자") String assignee,
        @Schema(description = "기한", example = "2026-08-30") String dueDate,
        @Schema(description = "긴급도", example = "보통", allowableValues = {"낮음", "보통", "높음"}) String urgency,
        @Schema(description = "승인 상태", example = "검토 중", allowableValues = {"대기", "검토 중", "승인 완료", "반려"}) String approvalStatus,
        @Schema(description = "피드백 상태", example = "반영 완료", allowableValues = {"미반영", "반영 중", "반영 완료"}) String feedbackStatus
) {}
