package io.github.essyaessya.onepace.fallback;

import io.github.essyaessya.onepace.dto.ActionItemDto;
import io.github.essyaessya.onepace.dto.CultureTranslationResponse;
import io.github.essyaessya.onepace.dto.DecisionDto;
import io.github.essyaessya.onepace.dto.MeetingSummaryResponse;
import java.util.List;

public final class FallbackResponses {

    private FallbackResponses() {}

    public static CultureTranslationResponse cultureTranslation() {
        return new CultureTranslationResponse(
                null,
                true,
                "urgent, right now",
                List.of("urgent", "right now"),
                "직설적인 긴급 표현은 문화권에 따라 명령조로 받아들여질 수 있습니다.",
                "Apologies for the rush, but whenever you have a quick moment, could you kindly take a look?"
        );
    }

    public static MeetingSummaryResponse meetingSummary() {
        return new MeetingSummaryResponse(
            null,
            "논의된 내용을 바탕으로 다음 일정과 담당자가 정리되었습니다.",
            "글로벌 랜딩페이지 디자인 피드백 수렴 및 최종안 확정",
            List.of(),
            List.of(new ActionItemDto(
                "글로벌 랜딩페이지 디자인 피드백 수렴 및 최종안 확정",
                "Alex (런던 팀)",
                "2026-08-15 18:00 KST",
                "보통",
                "검토 중",
                "반영 완료"
            ))
        );
    }
}
