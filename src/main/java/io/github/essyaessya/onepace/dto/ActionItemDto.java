package io.github.essyaessya.onepace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "액션 아이템")
public record ActionItemDto(
        @Schema(description = "할 일 제목") String title,
        @Schema(description = "담당자") String assignee,
        @Schema(description = "기한", example = "2026-08-30") String dueDate
) {}
