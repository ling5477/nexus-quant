package com.guidinglight.nexusquant.research.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

/** 顺序身份不得进入浮点金额路径，也不能通过改写真实时间区分同一时点的事件。 */
class ResearchEventOrderResponseTest {
    private final ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Instant TIME = Instant.parse("2026-01-01T00:03:59Z");

    @Test
    void executionSequenceRemainsExactStringBeyondJavascriptSafeIntegerRange() {
        var response = SimTradeResponse.from(trade().withExecutionSequence(Long.MAX_VALUE));
        var json = mapper.valueToTree(response);
        assertTrue(json.path("executionSequence").isTextual());
        assertEquals("9223372036854775807", json.path("executionSequence").asText());
        assertEquals(TIME.toString(), json.path("tradedAt").asText());
    }

    @Test
    void valuationSequenceRemainsExactStringWithoutChangingVisibleTime() {
        var response =
                SimPnlSnapshotResponse.from(snapshot().withValuationSequence(Long.MAX_VALUE));
        var json = mapper.valueToTree(response);
        assertTrue(json.path("valuationSequence").isTextual());
        assertEquals("9223372036854775807", json.path("valuationSequence").asText());
        assertEquals(TIME.toString(), json.path("snapshotTime").asText());
    }

    @Test
    void historicalMissingSequencesRemainExplicitlyMissing() {
        assertTrue(
                mapper.valueToTree(SimTradeResponse.from(trade()))
                        .path("executionSequence")
                        .isNull());
        assertTrue(
                mapper.valueToTree(SimPnlSnapshotResponse.from(snapshot()))
                        .path("valuationSequence")
                        .isNull());
    }

    private static SimTrade trade() {
        return new SimTrade(
                "st",
                "so",
                "brn",
                "BTC-USDT",
                "BUY",
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TIME,
                TIME,
                TIME);
    }

    private static SimPnlSnapshot snapshot() {
        return new SimPnlSnapshot(
                "pnl",
                "brn",
                TIME,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                TIME);
    }
}
