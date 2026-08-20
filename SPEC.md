# ONE PACE 백엔드 구현 지시서

Claude Code가 이 문서를 읽고 바로 구현할 수 있도록 작성된 스펙입니다. 대화나 설명이 아니라 **구현 지시**로 취급하세요.

---

## 1. 프로젝트 목표

해커톤(개발 기간 1일, 백엔드 2명) 데모용 백엔드. 다음 두 가지 핵심 기능만 구현한다.

1. **AI 문화번역기**: 작성 중인 메시지가 상대 문화권에서 오해를 살 수 있는지 실시간 분석
2. **AI 회의 요약**: 대화 내용을 요약하고 결정사항/미결업무를 추출

Slack 실 연동, OAuth, 회원가입/로그인은 **구현하지 않는다.** 프론트엔드는 슬랙 UI를 컴포넌트로 재현하되 미구현 버튼은 인터랙션만 없앤 상태이며, 이는 프론트 영역이므로 백엔드가 신경 쓸 필요 없다.

---

## 2. 확정된 기술 스택

| 항목 | 값 |
|---|---|
| 언어/프레임워크 | Java 21, Spring Boot 4.1.x |
| 빌드 도구 | Gradle (Groovy DSL), `build.gradle` |
| group / artifact / package | `io.github.essyaessya` / `onepace` / `io.github.essyaessya.onepace` |
| DB | MySQL (가비아 서버에서 직접 운영) — 로컬 개발은 H2로 대체 가능하도록 프로파일 분리 |
| ORM | Spring Data JPA / Hibernate |
| LLM | OpenAI API (`gpt-4o-mini` 기본값, `response_format: json_schema`로 구조화 출력 강제) |
| HTTP 클라이언트 | Spring WebFlux `WebClient` |
| 배포 대상 | 가비아 서버 (상시 구동 Linux VM, PaaS 아님 — 콜드스타트 이슈 없음) |

> **버전 근거**: Spring Boot 3.5가 2026-06-30부로 OSS 지원 종료(EOL)되어 신규 프로젝트는 4.x 기준으로 시작하는 게 맞다. 4.1.0이 현재 최신 안정 버전(2026-06-10 릴리즈)이며 신규 프로젝트에 권장되는 버전이다. Java는 21(LTS)을 사용 — Spring Framework 7(Boot 4 기반)의 최소 요구사항인 17보다 상위 LTS라 별도 문제 없음. Gradle은 9.x 사용 가능(8.14+도 지원되나 9.x 권장).

---

## 3. 환경 변수

```
OPENAI_API_KEY=              # 필수
OPENAI_MODEL=gpt-4o-mini     # 기본값, 필요시 gpt-4o로 교체
DB_URL=jdbc:mysql://localhost:3306/onepace
DB_USERNAME=
DB_PASSWORD=
FRONTEND_URL=                # CORS 허용 도메인 (콤마로 여러 개 구분)
SPRING_PROFILES_ACTIVE=prod  # 로컬 개발 시 local
```

---

## 4. 데이터베이스 설계

### 4.1 엔티티

**CultureTranslationLog**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| original_text | TEXT | |
| counterpart_country | VARCHAR(2) | ISO 3166-1 alpha-2 코드로 저장 (예: `GB`, `US`, `JP`). "UK"처럼 정식 코드가 아닌 값이 들어오지 않도록 프론트에서 통일해서 보낸다는 전제 |
| risk_detected | BOOLEAN | |
| detected_expression | TEXT | |
| nuance_explanation | TEXT | |
| suggested_text | TEXT | |
| created_at | DATETIME | |

**MeetingSummaryLog**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| summary_text | TEXT | |
| created_at | DATETIME | |

**Decision**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| meeting_summary_log_id | FK → MeetingSummaryLog | |
| decision_text | TEXT | |
| decided_by | VARCHAR(100) | |

**ActionItem**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| meeting_summary_log_id | FK → MeetingSummaryLog | |
| title | VARCHAR(255) | |
| assignee | VARCHAR(100) | nullable |
| due_date | VARCHAR(50) | nullable, 자유 텍스트로 저장 (LLM이 상대적 표현을 줄 수 있음) |
| status | VARCHAR(20) | 기본값 `OPEN` |

### 4.2 JPA 엔티티 구현 규칙

- `@Entity` + `@Table(name = "...")` 스네이크케이스 테이블명 사용
- `@CreationTimestamp`(Hibernate)로 `created_at` 자동 채움
- `MeetingSummaryLog` ↔ `Decision`/`ActionItem`은 `@OneToMany(mappedBy = ..., cascade = CascadeType.ALL)` — 요약 생성 시 자식 엔티티까지 한 번에 저장
- Repository는 `JpaRepository<Entity, Long>` 상속만으로 충분 (커스텀 쿼리 불필요)

### 4.3 프로파일 분리

- `application.yml`: 공통 설정
- `application-local.yml`: H2 인메모리 (`jdbc:h2:mem:onepace`), `ddl-auto: create-drop`
- `application-prod.yml`: MySQL (가비아 서버), `ddl-auto: update`

---

## 5. API 계약

### 5.1 `POST /api/culture-translation/analyze`

Request
```json
{ "text": "string", "counterpartCountry": "GB" }
```
`counterpartCountry`는 ISO 3166-1 alpha-2 코드(`GB`, `US`, `JP` 등)로 받는다. "UK" 같은 비표준 값에 대한 별도 검증/변환 로직은 만들지 않는다 — 프론트가 표준 코드로 보낸다는 전제.

Response `200`
```json
{
  "id": 1,
  "riskDetected": true,
  "detectedExpression": "string",
  "nuanceExplanation": "string",
  "suggestedText": "string"
}
```

동작: OpenAI 호출 → 성공 시 `CultureTranslationLog`에 저장 후 응답. **실패해도 예외를 던지지 말고 6절의 fallback을 저장 없이 즉시 반환**(`id`는 `null`).

**호출 시점**: 이 API는 메시지를 **전송하기 전, 사용자가 입력창에 타이핑하는 도중** 호출된다(전송 후가 아님). 프론트에서 debounce(예: 입력 멈춘 후 800ms~1s) 후 호출하는 것을 전제로 하며, 백엔드는 이 호출 빈도를 별도로 제한하는 로직(rate limiting)을 구현하지 않는다 — debounce는 프론트 책임, 백엔드는 매 호출을 독립적인 분석 요청으로 처리한다.

**상대방 국가/시간대 배지(예: "UK Business Culture", "런던 20:09(퇴근 후)")는 이 API의 응답 범위가 아니다.** 국가코드→타임존 매핑과 현재 시각 계산은 순수 클라이언트 로직(프론트)으로 처리되며 백엔드가 관여하지 않는다.

### 5.2 `POST /api/meeting-summary/generate`

Request
```json
{
  "messages": [
    { "sender": "string", "text": "string", "timestamp": "string" }
  ]
}
```

Response `200`
```json
{
  "id": 1,
  "summary": "string",
  "decisions": [ { "decisionText": "string", "decidedBy": "string" } ],
  "actionItems": [ { "title": "string", "assignee": "string", "dueDate": "string" } ]
}
```

동작: OpenAI 호출 → 성공 시 `MeetingSummaryLog` + 자식 `Decision`/`ActionItem` 저장 후 응답. 실패 시 fallback을 저장 없이 즉시 반환.

### 5.3 `GET /api/meeting-summary/history` (선택 구현, 시간 남으면)

최근 저장된 `MeetingSummaryLog` 목록을 최신순으로 반환. 데모 중 새로고침해도 이전 요약이 남아있는 걸 보여주기 위함.

### 5.4 `GET /api/health`

`{ "status": "ok" }` 고정 반환. 인증/DB 조회 없음.

## 5.5 DTO 정확한 시그니처

백엔드는 텍스트 길이 등 비즈니스 검증을 하지 않는다 — 프론트가 debounce/최소길이(5자) 조건을 이미 걸고 호출한다는 전제. `@NotBlank`(null/빈 문자열 방지) 정도만 건다.

```java
// request
public record CultureTranslationRequest(
    @NotBlank String text,
    @NotBlank String counterpartCountry // ISO 3166-1 alpha-2
) {}

public record MeetingSummaryRequest(
    @NotEmpty List<MessageDto> messages
) {}

public record MessageDto(
    @NotBlank String sender,
    @NotBlank String text,
    String timestamp // ISO-8601 문자열, 검증하지 않고 그대로 저장/전달
) {}

// response
public record CultureTranslationResponse(
    Long id, // fallback 시 null
    boolean riskDetected,
    String detectedExpression,
    String nuanceExplanation,
    String suggestedText
) {}

public record MeetingSummaryResponse(
    Long id, // fallback 시 null
    String summary,
    List<DecisionDto> decisions,
    List<ActionItemDto> actionItems
) {}

public record DecisionDto(String decisionText, String decidedBy) {}

public record ActionItemDto(String title, String assignee, String dueDate) {}
```

`GET /api/meeting-summary/history` 응답: `List<MeetingSummaryResponse>` (최신순, 최대 20건).

---

OpenAI 호출이 예외를 던지거나 5초 이상 걸리면 **절대 5xx를 반환하지 말고**, 아래 고정값을 담아 `200`으로 응답한다.

```java
// CultureTranslationLog 관련
riskDetected = true
detectedExpression = "urgent, right now"
nuanceExplanation = "직설적인 긴급 표현은 문화권에 따라 명령조로 받아들여질 수 있습니다."
suggestedText = "Apologies for the rush, but whenever you have a quick moment, could you kindly take a look?"

// MeetingSummaryLog 관련
summary = "논의된 내용을 바탕으로 다음 일정과 담당자가 정리되었습니다."
decisions = [{ decisionText: "다음 주까지 산출물을 확정한다", decidedBy: "팀 리드" }]
actionItems = [{ title: "산출물 정리 및 공유", assignee: "담당자", dueDate: "다음 마감일" }]
```

Service 계층에서 `try { ... } catch (Exception e) { return fallback; }` 형태로 처리. Controller/GlobalExceptionHandler까지 예외가 올라가면 안 된다(최후 방어선으로만 둔다).

---

## 7. OpenAI 연동 규격

- Endpoint: `https://api.openai.com/v1/chat/completions`
- Header: `Authorization: Bearer {OPENAI_API_KEY}`
- Body에 `response_format: { "type": "json_schema", "json_schema": { "name": "...", "schema": {...}, "strict": true } }` 사용해 JSON 스키마를 강제할 것
- WebClient에 `.timeout(Duration.ofSeconds(5))` 반드시 설정
- 공통 클라이언트 클래스(`OpenAiClient`)를 하나만 만들어 두 서비스가 공유 — 프롬프트/스키마/유저 메시지만 인자로 받고 파싱된 `Map<String,Object>`를 반환하는 형태로 설계

### 7.1 문화번역기용 `json_schema`

```json
{
  "type": "json_schema",
  "json_schema": {
    "name": "culture_translation_result",
    "strict": true,
    "schema": {
      "type": "object",
      "properties": {
        "riskDetected": { "type": "boolean" },
        "detectedExpression": { "type": "string" },
        "nuanceExplanation": { "type": "string" },
        "suggestedText": { "type": "string" }
      },
      "required": ["riskDetected", "detectedExpression", "nuanceExplanation", "suggestedText"],
      "additionalProperties": false
    }
  }
}
```

System prompt: "너는 글로벌 협업 상황에서 문화적 오해를 예방하는 어시스턴트야. 주어진 업무 메시지가 상대 국가/문화권에서 오해를 살 수 있는지 판단하고, 감지된 표현·예상 뉘앙스·대체 표현을 제시해. 문제 없으면 riskDetected를 false로 반환해."

### 7.2 회의 요약용 `json_schema`

```json
{
  "type": "json_schema",
  "json_schema": {
    "name": "meeting_summary_result",
    "strict": true,
    "schema": {
      "type": "object",
      "properties": {
        "summary": { "type": "string" },
        "decisions": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "decisionText": { "type": "string" },
              "decidedBy": { "type": "string" }
            },
            "required": ["decisionText", "decidedBy"],
            "additionalProperties": false
          }
        },
        "actionItems": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "title": { "type": "string" },
              "assignee": { "type": "string" },
              "dueDate": { "type": "string" }
            },
            "required": ["title", "assignee", "dueDate"],
            "additionalProperties": false
          }
        }
      },
      "required": ["summary", "decisions", "actionItems"],
      "additionalProperties": false
    }
  }
}
```

System prompt: "너는 팀 대화 내용에서 핵심을 요약하고, 결정된 사항과 아직 처리되지 않은 업무(미결 업무)를 추출하는 어시스턴트야. 담당자와 기한이 대화에 명시되어 있으면 반드시 포함시켜. 담당자/기한이 불명확하면 빈 문자열로 반환해."

---

## 8. 패키지 구조

```
io.github.essyaessya.onepace
 ├─ config/ (WebClientConfig, CorsConfig)
 ├─ controller/ (CultureTranslationController, MeetingSummaryController, HealthController)
 ├─ service/ (CultureTranslationService, MeetingSummaryService, OpenAiClient)
 ├─ domain/ (CultureTranslationLog, MeetingSummaryLog, Decision, ActionItem — 엔티티)
 ├─ repository/ (각 엔티티별 JpaRepository)
 ├─ dto/ (request/response 레코드)
 ├─ exception/ (GlobalExceptionHandler)
 └─ fallback/ (FallbackResponses)
```

---

## 9. 구현 순서 (이 순서대로 진행할 것)

1. Spring Boot 프로젝트 초기화 (Web, JPA, MySQL Driver, H2, Lombok, Validation)
2. `application.yml` + `application-local.yml` + `application-prod.yml` 작성
3. 도메인 엔티티 4종 + Repository 작성 → 로컬(H2) 프로파일로 애플리케이션 기동 확인
4. `OpenAiClient` 공통 클라이언트 구현 (구조화 출력)
5. `CultureTranslationService`/`Controller` 구현 + fallback 적용
6. `MeetingSummaryService`/`Controller` 구현 (자식 엔티티 cascade 저장 포함) + fallback 적용
7. `GlobalExceptionHandler`, `HealthController`, `CorsConfig` 구현
8. 두 엔드포인트 모두 정상 케이스 + `OPENAI_API_KEY`를 일부러 잘못된 값으로 바꿔 fallback 동작 테스트
9. `application-prod.yml` 기준으로 가비아 서버에 배포, MySQL 스키마 생성 확인, `/api/health` 응답 확인

로컬 실행: `./gradlew bootRun --args='--spring.profiles.active=local'`
빌드: `./gradlew build` → `build/libs/*.jar`
가비아 서버 실행 예시: `java -jar app.jar --spring.profiles.active=prod`

---

## 10. 명시적 제외 범위

다음은 이번 구현에 **포함하지 않는다.**
- 회원가입/로그인/JWT 인증
- Slack OAuth, Slack Events API 연동
- 위험 탐지(RiskAnalysis) 단독 API — 필요 시 문화번역기 응답에 병합해서 처리
- Rate limiting, 재시도 로직 (fallback으로 충분히 커버)
- 상대 국가 현지시각/업무시간 판단(예: "UK Business Culture" 배지) — 순수 프론트 계산 영역

---

## 11. 완료 기준 (Definition of Done)

- [ ] `POST /api/culture-translation/analyze`가 정상 응답 및 DB 저장 확인
- [ ] `POST /api/meeting-summary/generate`가 정상 응답 및 Decision/ActionItem cascade 저장 확인
- [ ] `OPENAI_API_KEY`를 무효화한 상태에서도 두 API 모두 200 + fallback 응답 반환
- [ ] `/api/health`가 인증 없이 200 반환
- [ ] `application-prod` 프로파일로 가비아 서버에서 MySQL 연결 성공
- [ ] 프론트 배포 도메인 기준 CORS 정상 동작 확인