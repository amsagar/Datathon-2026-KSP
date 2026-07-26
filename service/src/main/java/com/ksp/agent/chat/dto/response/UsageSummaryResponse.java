package com.ksp.agent.chat.dto.response;

import java.util.List;

public record UsageSummaryResponse(
        UsageTotalsDto totals,
        List<UsageDailyRowDto> daily,
        UsageTotalsDto previousTotals
) {
}
