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
            "너는 팀 대화 내용에서 핵심을 요약하고, 회의의 목표, 결정된 사항, 아직 처리되지 않은 업무(미결 업무)를 추출하는 어시스턴트야. "
                    + "goal에는 이 회의/대화가 달성하려는 핵심 목표를 한 문장으로 정리해. "
                    + "담당자와 기한이 대화에 명시되어 있으면 반드시 포함시켜. 담당자/기한이 불명확하면 빈 문자열로 반환해. "
                    + "각 actionItem마다 urgency(긴급도: 낮음/보통/높음), approvalStatus(승인 상태: 대기/검토 중/승인 완료/반려), "
                    + "feedbackStatus(피드백 상태: 미반영/반영 중/반영 완료)를 대화 맥락에서 판단해서 채워. "
                    + "명시적 근거가 없으면 urgency는 '보통', approvalStatus는 '대기', feedbackStatus는 '미반영'으로 기본값을 사용해."
                    + "dueDate를 계산할 때는 순서대로 생각해. 먼저 기준이 되는 메시지의 timestamp 날짜가 무슨 요일인지 정확히 계산해. "
                    + "그다음 '이번 주 O요일'이면 그 요일이 기준일과 같은 주 중 언제인지, '다음 주 O요일'이면 기준일이 속한 주가 끝난 바로 다음 주의 그 요일이 며칠인지 하나씩 세어서 계산해. "
                    + "이렇게 계산한 정확한 날짜만 YYYY-MM-DD 형식으로 dueDate에 채우고, 절대 대략적으로 추측하지 마.";

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
            "properties", Map.ofEntries(
                    Map.entry("title", Map.of("type", "string")),
                    Map.entry("assignee", Map.of("type", "string")),
                    Map.entry("dueDate", Map.of("type", "string")),
                    Map.entry("urgency", Map.of("type", "string", "enum", List.of("낮음", "보통", "높음"))),
                    Map.entry("approvalStatus", Map.of("type", "string", "enum", List.of("대기", "검토 중", "승인 완료", "반려"))),
                    Map.entry("feedbackStatus", Map.of("type", "string", "enum", List.of("미반영", "반영 중", "반영 완료")))
            ),
            "required", List.of("title", "assignee", "dueDate", "urgency", "approvalStatus", "feedbackStatus"),
            "additionalProperties", false
    );

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "goal", Map.of("type", "string"),
                    "decisions", Map.of("type", "array", "items", DECISION_ITEM_SCHEMA),
                    "actionItems", Map.of("type", "array", "items", ACTION_ITEM_SCHEMA)
            ),
            "required", List.of("summary", "goal", "decisions", "actionItems"),
            "additionalProperties", false
    );

    private final OpenAiClient openAiClient;
    private final MeetingSummaryLogRepository repository;

    public MeetingSummaryResponse generate(MeetingSummaryRequest request) {
        try {
            String userMessage = toConversationText(request.messages());

            Map<String, Object> result = openAiClient.callStructured(SYSTEM_PROMPT, userMessage, SCHEMA_NAME, SCHEMA);

            String summary = (String) result.get("summary");
            String goal = (String) result.get("goal");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> decisionMaps = (List<Map<String, Object>>) result.get("decisions");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actionItemMaps = (List<Map<String, Object>>) result.get("actionItems");

            MeetingSummaryLog meetingSummaryLog = MeetingSummaryLog.builder()
                    .summaryText(summary)
                    .goal(goal)
                    .build();

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
                        .urgency((String) a.get("urgency"))
                        .approvalStatus((String) a.get("approvalStatus"))
                        .feedbackStatus((String) a.get("feedbackStatus"))
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
                .map(a -> new ActionItemDto(a.getTitle(), a.getAssignee(), a.getDueDate(),
                        a.getUrgency(), a.getApprovalStatus(), a.getFeedbackStatus()))
                .toList();
        return new MeetingSummaryResponse(entity.getId(), entity.getSummaryText(), entity.getGoal(), decisions, actionItems);
    }
}
