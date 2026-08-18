package io.github.essyaessya.onepace.service;

import io.github.essyaessya.onepace.domain.CultureTranslationLog;
import io.github.essyaessya.onepace.dto.CultureTranslationRequest;
import io.github.essyaessya.onepace.dto.CultureTranslationResponse;
import io.github.essyaessya.onepace.fallback.FallbackResponses;
import io.github.essyaessya.onepace.repository.CultureTranslationLogRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CultureTranslationService {

    private static final String SYSTEM_PROMPT =
            "너는 글로벌 협업 상황에서 문화적 오해를 예방하는 어시스턴트야. "
                    + "주어진 업무 메시지가 상대 국가/문화권에서 오해를 살 수 있는지 판단하고, "
                    + "감지된 표현·실시간 탐지 메시지·예상 뉘앙스·대체 표현을 제시해. "
                    + "판단 기준: 명령조 지시, 직설적인 긴급 요구, 무례하게 들릴 수 있는 표현만 riskDetected를 true로 판단해. "
                    + "이미 정중한 부탁 표현(예: Could you kindly, Apologies for, whenever you have a moment 등 완곡어법)이 "
                    + "포함된 문장은 riskDetected를 false로 반환하고 detectedExpression/realtimeDetection/nuanceExplanation/suggestedText는 빈 문자열로 반환해. "
                    + "riskDetected가 true인 경우 realtimeDetection은 감지된 위험을 요약한 한 문장만 작성해 "
                    + "(예: \"직설적인 독촉 표현 감지됨.\"). \"실시간 탐지:\" 같은 접두어는 붙이지 마. "
                    + "suggestedText는 반드시 원문(메시지)과 동일한 언어로 작성해. 원문이 한국어면 한국어로, 영어면 영어로 제안해. "
                    + "다른 언어로 번역하지 말고 같은 언어 안에서 더 정중하고 완곡한 표현으로만 다듬어. "
                    + "같은 문장은 항상 같은 결과로 판단해서 일관성을 유지해.";

    private static final String SCHEMA_NAME = "culture_translation_result";

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "riskDetected", Map.of("type", "boolean"),
                    "detectedExpression", Map.of("type", "string"),
                    "realtimeDetection", Map.of("type", "string"),
                    "nuanceExplanation", Map.of("type", "string"),
                    "suggestedText", Map.of("type", "string")
            ),
            "required", List.of("riskDetected", "detectedExpression", "realtimeDetection", "nuanceExplanation", "suggestedText"),
            "additionalProperties", false
    );

    private final OpenAiClient openAiClient;
    private final CultureTranslationLogRepository repository;

    public CultureTranslationResponse analyze(CultureTranslationRequest request) {
        try {
            String userMessage = "메시지: " + request.text() + "\n상대 국가 코드(ISO 3166-1 alpha-2): " + request.counterpartCountry();

            Map<String, Object> result = openAiClient.callStructured(SYSTEM_PROMPT, userMessage, SCHEMA_NAME, SCHEMA);

            boolean riskDetected = (boolean) result.get("riskDetected");
            String detectedExpression = (String) result.get("detectedExpression");
            String realtimeDetection = (String) result.get("realtimeDetection");
            String nuanceExplanation = (String) result.get("nuanceExplanation");
            String suggestedText = (String) result.get("suggestedText");

            CultureTranslationLog saved = repository.save(CultureTranslationLog.builder()
                    .originalText(request.text())
                    .counterpartCountry(request.counterpartCountry())
                    .riskDetected(riskDetected)
                    .detectedExpression(detectedExpression)
                    .realtimeDetection(realtimeDetection)
                    .nuanceExplanation(nuanceExplanation)
                    .suggestedText(suggestedText)
                    .build());

            return new CultureTranslationResponse(
                    saved.getId(), riskDetected, detectedExpression, realtimeDetection, nuanceExplanation, suggestedText);
        } catch (Exception e) {
            log.warn("Culture translation analysis failed, returning fallback", e);
            return FallbackResponses.cultureTranslation();
        }
    }
}
