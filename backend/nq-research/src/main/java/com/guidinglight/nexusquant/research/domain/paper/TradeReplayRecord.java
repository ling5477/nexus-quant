package com.guidinglight.nexusquant.research.domain.paper;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeReplayRecord(
        String replayRecordId,
        String paperRunId,
        String paperOrderId,
        String paperTradeId,
        Instant replayTime,
        String eventType,
        String symbol,
        String side,
        BigDecimal price,
        BigDecimal quantity,
        String reason,
        String decisionSnapshotJson,
        String riskSnapshotJson,
        String marketSnapshotJson,
        Instant createdAt
) {}
