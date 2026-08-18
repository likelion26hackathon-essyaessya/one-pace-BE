package io.github.essyaessya.onepace.controller;

import io.github.essyaessya.onepace.dto.CultureTranslationRequest;
import io.github.essyaessya.onepace.dto.CultureTranslationResponse;
import io.github.essyaessya.onepace.service.CultureTranslationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Culture Translation", description = "문화 뉘앙스 번역/리스크 분석 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/culture-translation")
public class CultureTranslationController {

    private final CultureTranslationService cultureTranslationService;

    @Operation(summary = "문화 뉘앙스 분석", description = "입력한 텍스트를 상대 국가의 문화적 맥락에서 분석하여 오해 소지가 있는 표현과 대체 표현을 제안한다.")
    @ApiResponse(responseCode = "200", description = "분석 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 검증 실패")
    @PostMapping("/analyze")
    public CultureTranslationResponse analyze(@Valid @RequestBody CultureTranslationRequest request) {
        return cultureTranslationService.analyze(request);
    }
}
