package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaperRunDailyReportResponse(
        String reportId,
        String paperRunId,
        LocalDate reportDate,
        String status,
        BigDecimal totalEquity,
        BigDecimal dailyPnl,
        BigDecimal dailyReturn,
        BigDecimal maxDrawdown,
        int orderCount,
        int tradeCount,
        int alertCount,
        int riskRejectCount,
        String reportJson,
        Instant generatedAt,
        Instant createdAt
) {
    public static PaperRunDailyReportResponse from(PaperRunDailyReport r) {
        return new PaperRunDailyReportResponse(
                r.reportId(), r.paperRunId(), r.reportDate(), r.status().name(),
                r.totalEquity(), r.dailyPnl(), r.dailyReturn(), r.maxDrawdown(),
                r.orderCount(), r.tradeCount(), r.alertCount(), r.riskRejectCount(),
                r.reportJson(), r.generatedAt(), r.createdAt());
    }
}
