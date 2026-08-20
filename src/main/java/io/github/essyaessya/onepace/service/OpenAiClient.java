package io.github.essyaessya.onepace.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class OpenAiClient {

    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final boolean forceFallback;

    public OpenAiClient(WebClient openAiWebClient, ObjectMapper objectMapper,
                         @Value("${openai.model}") String model,
                         @Value("${app.force-fallback:false}") boolean forceFallback) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.forceFallback = forceFallback;
    }

    public Map<String, Object> callStructured(String systemPrompt, String userMessage,
                                                String schemaName, Map<String, Object> schema) {
        if (forceFallback) {
            log.warn("강제 fallback 모드(app.force-fallback=true) - OpenAI 호출 스킵: schema={}", schemaName);
            throw new IllegalStateException("Force fallback mode is enabled");
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", schemaName,
                                "strict", true,
                                "schema", schema
                        )
                )
        );

        log.info("OpenAI 요청 시작: model={}, schema={}", model, schemaName);
        long startedAt = System.currentTimeMillis();
        String rawResponse;
        try {
            rawResponse = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("OpenAI 요청 실패: model={}, schema={}, elapsedMs={}, status={}, responseBody={}",
                    model, schemaName, System.currentTimeMillis() - startedAt,
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (WebClientRequestException e) {
            log.error("OpenAI 요청 실패(네트워크 오류): model={}, schema={}, elapsedMs={}, cause={}",
                    model, schemaName, System.currentTimeMillis() - startedAt, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("OpenAI 요청 실패(타임아웃/기타): model={}, schema={}, elapsedMs={}",
                    model, schemaName, System.currentTimeMillis() - startedAt, e);
            throw e;
        }

        log.info("OpenAI 요청 성공: model={}, schema={}, elapsedMs={}",
                model, schemaName, System.currentTimeMillis() - startedAt);

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root.at("/choices/0/message/content").asString();
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("OpenAI 응답 파싱 실패: model={}, schema={}, rawResponse={}", model, schemaName, rawResponse, e);
            throw new IllegalStateException("Failed to parse OpenAI response", e);
        }
    }
}
