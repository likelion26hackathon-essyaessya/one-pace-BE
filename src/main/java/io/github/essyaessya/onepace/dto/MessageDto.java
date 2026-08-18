package io.github.essyaessya.onepace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회의 메시지 한 건")
public record MessageDto(
        @Schema(description = "발화자", example = "홍길동")
        @NotBlank String sender,

        @Schema(description = "메시지 내용", example = "다음 스프린트까지 API 연동을 마치겠습니다.")
        @NotBlank String text,

        @Schema(description = "메시지 시각", example = "2026-08-19T10:00:00")
        String timestamp
) {}
