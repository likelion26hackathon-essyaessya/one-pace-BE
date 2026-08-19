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
                "직설적인 독촉 표현 감지됨.",
                "직설적인 긴급 표현은 문화권에 따라 명령조로 받아들여질 수 있습니다.",
                "Apologies for the rush, but whenever you have a quick moment, could you kindly take a look?"
        );
    }

    public static MeetingSummaryResponse meetingSummary() {
        return new MeetingSummaryResponse(
                null,
                "논의된 내용을 바탕으로 다음 일정과 담당자가 정리되었습니다.",
                "논의된 업무의 산출물을 확정하고 공유한다",
                List.of(new DecisionDto("다음 주까지 산출물을 확정한다", "팀 리드")),
                List.of(new ActionItemDto("산출물 정리 및 공유", "담당자", "다음 마감일", "보통", "대기", "미반영"))
        );
    }
}
