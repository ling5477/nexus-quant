package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.TradeReplayRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeReplayRecordResponse(
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
) {
    public static TradeReplayRecordResponse from(TradeReplayRecord r) {
        return new TradeReplayRecordResponse(
                r.replayRecordId(), r.paperRunId(), r.paperOrderId(), r.paperTradeId(),
                r.replayTime(), r.eventType(), r.symbol(), r.side(),
                r.price(), r.quantity(), r.reason(),
                r.decisionSnapshotJson(), r.riskSnapshotJson(), r.marketSnapshotJson(),
                r.createdAt());
    }
}
