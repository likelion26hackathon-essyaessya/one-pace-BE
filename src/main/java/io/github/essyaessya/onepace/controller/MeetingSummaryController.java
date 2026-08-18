package io.github.essyaessya.onepace.controller;

import io.github.essyaessya.onepace.dto.MeetingSummaryRequest;
import io.github.essyaessya.onepace.dto.MeetingSummaryResponse;
import io.github.essyaessya.onepace.service.MeetingSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting Summary", description = "회의록 요약 및 이력 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meeting-summary")
public class MeetingSummaryController {

    private final MeetingSummaryService meetingSummaryService;

    @Operation(summary = "회의록 요약 생성", description = "회의 메시지 목록을 입력받아 요약, 결정 사항, 액션 아이템을 생성한다.")
    @ApiResponse(responseCode = "200", description = "요약 생성 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 검증 실패")
    @PostMapping("/generate")
    public MeetingSummaryResponse generate(@Valid @RequestBody MeetingSummaryRequest request) {
        return meetingSummaryService.generate(request);
    }

    @Operation(summary = "회의 요약 이력 조회", description = "이전에 생성된 회의 요약 목록을 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/history")
    public List<MeetingSummaryResponse> history() {
        return meetingSummaryService.history();
    }
}
