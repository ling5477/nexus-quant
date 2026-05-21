package com.guidinglight.nexusquant.research.domain.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaperRunDailyReport(
        String reportId,
        String paperRunId,
        LocalDate reportDate,
        PaperRunDailyReportStatus status,
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
) {}
