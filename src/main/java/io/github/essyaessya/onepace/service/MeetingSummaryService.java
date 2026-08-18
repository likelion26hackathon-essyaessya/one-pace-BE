package io.github.essyaessya.onepace.service;

import io.github.essyaessya.onepace.domain.ActionItem;
import io.github.essyaessya.onepace.domain.Decision;
import io.github.essyaessya.onepace.domain.MeetingSummaryLog;
import io.github.essyaessya.onepace.dto.ActionItemDto;
import io.github.essyaessya.onepace.dto.DecisionDto;
import io.github.essyaessya.onepace.dto.MeetingSummaryRequest;
import io.github.essyaessya.onepace.dto.MeetingSummaryResponse;
import io.github.essyaessya.onepace.dto.MessageDto;
import io.github.essyaessya.onepace.fallback.FallbackResponses;
import io.github.essyaessya.onepace.repository.MeetingSummaryLogRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingSummaryService {

    private static final String SYSTEM_PROMPT =
            "너는 팀 대화 내용에서 핵심을 요약하고, 결정된 사항과 아직 처리되지 않은 업무(미결 업무)를 추출하는 어시스턴트야. "
                    + "담당자와 기한이 대화에 명시되어 있으면 반드시 포함시켜. 담당자/기한이 불명확하면 빈 문자열로 반환해.";

    private static final String SCHEMA_NAME = "meeting_summary_result";

    private static final Map<String, Object> DECISION_ITEM_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "decisionText", Map.of("type", "string"),
                    "decidedBy", Map.of("type", "string")
            ),
            "required", List.of("decisionText", "decidedBy"),
            "additionalProperties", false
    );

    private static final Map<String, Object> ACTION_ITEM_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "title", Map.of("type", "string"),
                    "assignee", Map.of("type", "string"),
                    "dueDate", Map.of("type", "string")
            ),
            "required", List.of("title", "assignee", "dueDate"),
            "additionalProperties", false
    );

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "decisions", Map.of("type", "array", "items", DECISION_ITEM_SCHEMA),
                    "actionItems", Map.of("type", "array", "items", ACTION_ITEM_SCHEMA)
            ),
            "required", List.of("summary", "decisions", "actionItems"),
            "additionalProperties", false
    );

    private final OpenAiClient openAiClient;
    private final MeetingSummaryLogRepository repository;

    public MeetingSummaryResponse generate(MeetingSummaryRequest request) {
        try {
            String userMessage = toConversationText(request.messages());

            Map<String, Object> result = openAiClient.callStructured(SYSTEM_PROMPT, userMessage, SCHEMA_NAME, SCHEMA);

            String summary = (String) result.get("summary");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> decisionMaps = (List<Map<String, Object>>) result.get("decisions");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actionItemMaps = (List<Map<String, Object>>) result.get("actionItems");

            MeetingSummaryLog meetingSummaryLog = MeetingSummaryLog.builder().summaryText(summary).build();

            for (Map<String, Object> d : decisionMaps) {
                meetingSummaryLog.addDecision(Decision.builder()
                        .decisionText((String) d.get("decisionText"))
                        .decidedBy((String) d.get("decidedBy"))
                        .build());
            }

            for (Map<String, Object> a : actionItemMaps) {
                meetingSummaryLog.addActionItem(ActionItem.builder()
                        .title((String) a.get("title"))
                        .assignee((String) a.get("assignee"))
                        .dueDate((String) a.get("dueDate"))
                        .build());
            }

            MeetingSummaryLog saved = repository.save(meetingSummaryLog);

            return toResponse(saved);
        } catch (Exception e) {
            log.warn("Meeting summary generation failed, returning fallback", e);
            return FallbackResponses.meetingSummary();
        }
    }

    public List<MeetingSummaryResponse> history() {
        return repository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private String toConversationText(List<MessageDto> messages) {
        return messages.stream()
                .map(m -> "[%s] %s: %s".formatted(m.timestamp(), m.sender(), m.text()))
                .collect(Collectors.joining("\n"));
    }

    private MeetingSummaryResponse toResponse(MeetingSummaryLog entity) {
        List<DecisionDto> decisions = entity.getDecisions().stream()
                .map(d -> new DecisionDto(d.getDecisionText(), d.getDecidedBy()))
                .toList();
        List<ActionItemDto> actionItems = entity.getActionItems().stream()
                .map(a -> new ActionItemDto(a.getTitle(), a.getAssignee(), a.getDueDate()))
                .toList();
        return new MeetingSummaryResponse(entity.getId(), entity.getSummaryText(), decisions, actionItems);
    }
}
