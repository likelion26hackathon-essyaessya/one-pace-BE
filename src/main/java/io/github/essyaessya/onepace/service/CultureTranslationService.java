package io.github.essyaessya.onepace.service;

import io.github.essyaessya.onepace.domain.CultureTranslationLog;
import io.github.essyaessya.onepace.dto.CultureTranslationRequest;
import io.github.essyaessya.onepace.dto.CultureTranslationResponse;
import io.github.essyaessya.onepace.fallback.FallbackResponses;
import io.github.essyaessya.onepace.repository.CultureTranslationLogRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
                    + "[언어 규칙 — 반드시 지켜야 함] 응답을 만들기 전에 먼저 원문 메시지의 언어를 확인해. "
                    + "nuanceExplanation은 원문 언어가 무엇이든 상관없이 항상 한국어로만 작성해. "
                    + "realtimeDetection과 suggestedText는 반드시 원문과 동일한 언어로만 작성해 — 원문이 한국어면 한국어만, "
                    + "영어면 영어만 사용하고 절대로 다른 언어를 섞거나 번역하지 마. "
                    + "판단 기준: 명령조 지시, 직설적인 긴급 요구, 무례하게 들릴 수 있는 표현만 riskDetected를 true로 판단해. "
                    + "이미 정중한 부탁 표현(예: Could you kindly, Apologies for, whenever you have a moment 등 완곡어법)이 "
                    + "포함된 문장은 riskDetected를 false로 반환하고 detectedExpression/nuanceExplanation/suggestedText는 빈 문자열로, "
                    + "realtimeDetection은 빈 배열([])로 반환해. "
                    + "riskDetected가 true인 경우 realtimeDetection은 위험하다고 판단한 표현들을 원문 메시지에 등장하는 그대로 "
                    + "(대소문자·띄어쓰기·구두점까지 한 글자도 바꾸지 말고) 부분 문자열로 추출해서 배열로 반환해. "
                    + "위험한 표현이 문장 안에서 서로 떨어져 있으면(예: \"urgent\"와 \"right now\"가 떨어져 있는 경우) 하나로 이어붙이지 말고 "
                    + "각각을 배열의 별도 원소로 나눠서 반환해. 요약하거나 설명을 덧붙이지 마 — 프론트엔드가 각 원소로 "
                    + "원문 텍스트에서 위치를 찾아 밑줄을 긋기 때문에, 원문에 정확히 존재하지 않는 문구를 반환하면 절대 안 돼. "
                    + "suggestedText는 원문의 표현을 다른 언어로 번역하지 말고 같은 언어 안에서 더 정중하고 완곡한 표현으로만 다듬어. "
                    + "같은 문장은 항상 같은 결과로 판단해서 일관성을 유지해. "
                    + "다시 한번 강조: nuanceExplanation은 반드시 한국어, realtimeDetection과 suggestedText는 반드시 원문과 동일한 언어 — "
                    + "이 언어 규칙을 어기면 안 돼.";

    private static final String SCHEMA_NAME = "culture_translation_result";

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "riskDetected", Map.of("type", "boolean"),
                    "detectedExpression", Map.of("type", "string"),
                    "realtimeDetection", Map.of("type", "array", "items", Map.of("type", "string")),
                    "nuanceExplanation", Map.of("type", "string"),
                    "suggestedText", Map.of("type", "string")
            ),
            "required", List.of("riskDetected", "detectedExpression", "realtimeDetection", "nuanceExplanation", "suggestedText"),
            "additionalProperties", false
    );

    private final OpenAiClient openAiClient;
    private final CultureTranslationLogRepository repository;

    // 동일한 (메시지, 상대 국가) 입력은 항상 같은 결과를 반환하도록 설계돼 있어(SYSTEM_PROMPT 참고),
    // 캐시로 재사용해도 정확도 손실 없이 OpenAI 호출 횟수(RPM/RPD 한도)를 줄일 수 있음
    private final Map<String, Map<String, Object>> resultCache = new ConcurrentHashMap<>();

    public CultureTranslationResponse analyze(CultureTranslationRequest request) {
        try {
            String userMessage = "메시지: " + request.text() + "\n상대 국가 코드(ISO 3166-1 alpha-2): " + request.counterpartCountry();
            String cacheKey = request.counterpartCountry() + "|" + request.text();

            Map<String, Object> result = resultCache.computeIfAbsent(cacheKey,
                    key -> openAiClient.callStructured(SYSTEM_PROMPT, userMessage, SCHEMA_NAME, SCHEMA));

            boolean riskDetected = (boolean) result.get("riskDetected");
            String detectedExpression = (String) result.get("detectedExpression");
            @SuppressWarnings("unchecked")
            List<String> realtimeDetection = (List<String>) result.get("realtimeDetection");
            String nuanceExplanation = (String) result.get("nuanceExplanation");
            String suggestedText = (String) result.get("suggestedText");

            CultureTranslationLog saved = repository.save(CultureTranslationLog.builder()
                    .originalText(request.text())
                    .counterpartCountry(request.counterpartCountry())
                    .riskDetected(riskDetected)
                    .detectedExpression(detectedExpression)
                    .realtimeDetection(String.join(" | ", realtimeDetection))
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
