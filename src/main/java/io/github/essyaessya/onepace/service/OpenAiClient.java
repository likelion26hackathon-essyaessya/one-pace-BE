package io.github.essyaessya.onepace.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OpenAiClient {

    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiClient(WebClient openAiWebClient, ObjectMapper objectMapper,
                         @Value("${openai.model}") String model) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public Map<String, Object> callStructured(String systemPrompt, String userMessage,
                                                String schemaName, Map<String, Object> schema) {
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

        String rawResponse = openAiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root.at("/choices/0/message/content").asString();
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OpenAI response", e);
        }
    }
}
