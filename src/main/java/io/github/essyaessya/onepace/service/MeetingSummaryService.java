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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
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
                    + "대화에서 일정이나 결정이 조정된 이유가 개인적 사정, 문화적·종교적 배경, 업무 관행의 차이(예: 마감 기한에 대한 인식 차이) 때문이라면, "
                    + "그 이유를 얼버무리지 말고 summary나 goal에 구체적으로 드러내. 예를 들어 담당자가 종교 행사나 개인 사정으로 일정 변경을 요청했다면, "
                    + "단순히 '일정을 조정했다'라고만 쓰지 말고 '~한 사정으로 일정을 조정했다'처럼 이유까지 포함해서 써. "
                    + "decisions는 회의에서 확정된 합의나 결론을 뜻해. 무엇을 하기로 결정했는지뿐 아니라, 안건을 보류하거나 다음으로 미루기로 한 것도 반드시 하나의 decision으로 기록해. "
                    + "actionItems는 그 decision을 실행하기 위해 특정 담당자가 해야 할 구체적인 업무야. "
                    + "담당자와 기한이 대화에 명시되어 있으면 반드시 포함시켜. 담당자/기한이 불명확하면 빈 문자열로 반환해. "
                    + "각 actionItem마다 urgency(긴급도: 낮음/보통/높음), approvalStatus(승인 상태: 대기/검토 중/승인 완료/반려), "
                    + "feedbackStatus(피드백 상태: 미반영/반영 중/반영 완료)를 대화 맥락에서 판단해서 채워. "
                    + "feedbackStatus는 산출물에 대한 피드백이 실제로 반영됐는지를 뜻해: 아직 피드백을 요청/수령하지 못했거나 승인을 기다리는 중이면 '미반영', 받은 피드백을 현재 수정 중이면 '반영 중', 모든 피드백을 반영해서 마무리했으면 '반영 완료'로 판단해. "
                    + "명시적 근거가 없으면 urgency는 '보통', approvalStatus는 '대기', feedbackStatus는 '미반영'으로 기본값을 사용해. "
                    + "사용자 메시지 맨 위에는 [참고용 날짜 표]가 함께 제공돼. dueDate를 정할 때는 절대 스스로 날짜를 계산하지 말고, "
                    + "대화에서 언급된 '이번 주 O요일', '다음 주 O요일', '오늘', '어제' 같은 표현에 해당하는 날짜를 이 표에서 그대로 찾아서 옮겨 적어. "
                    + "표에 정확히 일치하는 표현이 없으면(예: '이번 달 말', '조만간' 등 모호한 표현) 빈 문자열로 반환해. "
                    + "actionItem이 여러 개일 때도 각 항목마다 표에서 다시 독립적으로 찾아서 채워 — 다른 항목에서 찾은 날짜에 영향받지 마. "
                    + "이렇게 표에서 찾은 정확한 날짜만 YYYY-MM-DD 형식으로 dueDate에 채워.";

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

    private static final String[] DAY_NAMES = {"월", "화", "수", "목", "금", "토", "일"};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OpenAiClient openAiClient;
    private final MeetingSummaryLogRepository repository;

    public MeetingSummaryResponse generate(MeetingSummaryRequest request) {
        try {
            String userMessage = buildDateReferenceTable(request.messages())
                    + "\n[대화 내용]\n"
                    + toConversationText(request.messages());

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

    /**
     * 대화 메시지의 timestamp를 기준으로 이번 주/다음 주 월~일 실제 날짜를 미리 계산해
     * LLM에게 "표"로 제공한다. LLM이 날짜를 직접 계산하지 않고 표에서 찾아 쓰게 하기 위함.
     */
    private String buildDateReferenceTable(List<MessageDto> messages) {
        LocalDate baseDate = messages.stream()
                .map(MessageDto::timestamp)
                .filter(ts -> ts != null && !ts.isBlank())
                .findFirst()
                .map(this::parseToLocalDate)
                .orElse(LocalDate.now());

        LocalDate thisMonday = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate nextMonday = thisMonday.plusWeeks(1);
        LocalDate yesterday = baseDate.minusDays(1);

        StringBuilder sb = new StringBuilder();
        sb.append("[참고용 날짜 표]\n");
        sb.append("기준일(오늘): ").append(baseDate.format(DATE_FORMATTER))
                .append(" (").append(DAY_NAMES[baseDate.getDayOfWeek().getValue() - 1]).append("요일)\n");
        sb.append("어제: ").append(yesterday.format(DATE_FORMATTER)).append("\n");

        sb.append("이번 주: ");
        appendWeek(sb, thisMonday);
        sb.append("\n");

        sb.append("다음 주: ");
        appendWeek(sb, nextMonday);
        sb.append("\n");

        return sb.toString();
    }

    /**
     * 프론트가 UTC(Z) 오프셋이 붙은 ISO-8601과 오프셋 없는 순수 로컬 문자열을 섞어 보낼 수 있어
     * 오프셋이 있으면 KST로 변환하고, 없으면 로컬 시간 그대로 파싱한다.
     */
    private LocalDate parseToLocalDate(String ts) {
        try {
            return OffsetDateTime.parse(ts).atZoneSameInstant(KST).toLocalDate();
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(ts).toLocalDate();
        }
    }

    private void appendWeek(StringBuilder sb, LocalDate monday) {
        for (int i = 0; i < 7; i++) {
            sb.append(DAY_NAMES[i]).append(" ").append(monday.plusDays(i).format(DATE_FORMATTER));
            if (i < 6) {
                sb.append(", ");
            }
        }
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
