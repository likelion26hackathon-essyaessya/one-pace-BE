package io.github.essyaessya.onepace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "문화 뉘앙스 분석 요청")
public record CultureTranslationRequest(
        @Schema(description = "분석할 원문 텍스트", example = "이 부분은 이해가 안 되네요.")
        @NotBlank String text,

        @Schema(description = "메시지를 받는 상대방의 국가 코드 (ISO 3166-1 alpha-2)", example = "JP")
        @NotBlank @Size(min = 2, max = 2, message = "counterpartCountry는 ISO 3166-1 alpha-2 코드(2자리)여야 합니다") String counterpartCountry
) {}
