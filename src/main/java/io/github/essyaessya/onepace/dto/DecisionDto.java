package io.github.essyaessya.onepace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회의 결정 사항")
public record DecisionDto(
        @Schema(description = "결정 내용") String decisionText,
        @Schema(description = "결정자") String decidedBy
) {}
