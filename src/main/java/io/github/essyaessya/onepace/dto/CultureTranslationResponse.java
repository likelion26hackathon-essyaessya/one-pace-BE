package io.github.essyaessya.onepace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "문화 뉘앙스 분석 결과")
public record CultureTranslationResponse(
        @Schema(description = "분석 로그 ID") Long id,
        @Schema(description = "문화적 오해 소지 감지 여부") boolean riskDetected,
        @Schema(description = "감지된 표현") String detectedExpression,
        @Schema(description = "원문에서 감지된 위험 표현들 그대로(밑줄 표시용, 각 원소가 원문과 정확히 일치하는 부분 문자열)")
        List<String> realtimeDetection,
        @Schema(description = "뉘앙스 설명") String nuanceExplanation,
        @Schema(description = "제안된 대체 표현") String suggestedText
) {}
