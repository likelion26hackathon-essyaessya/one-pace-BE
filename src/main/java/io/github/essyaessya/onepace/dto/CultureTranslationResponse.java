package io.github.essyaessya.onepace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문화 뉘앙스 분석 결과")
public record CultureTranslationResponse(
        @Schema(description = "분석 로그 ID") Long id,
        @Schema(description = "문화적 오해 소지 감지 여부") boolean riskDetected,
        @Schema(description = "감지된 표현") String detectedExpression,
        @Schema(description = "실시간 탐지 메시지", example = "직설적인 독촉 표현 감지됨.") String realtimeDetection,
        @Schema(description = "뉘앙스 설명") String nuanceExplanation,
        @Schema(description = "제안된 대체 표현") String suggestedText
) {}
