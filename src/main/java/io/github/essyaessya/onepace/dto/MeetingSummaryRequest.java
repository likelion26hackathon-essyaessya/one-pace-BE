package io.github.essyaessya.onepace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "회의록 요약 생성 요청")
public record MeetingSummaryRequest(
        @Schema(description = "요약할 회의 메시지 목록")
        @NotEmpty @Valid List<MessageDto> messages
) {}
