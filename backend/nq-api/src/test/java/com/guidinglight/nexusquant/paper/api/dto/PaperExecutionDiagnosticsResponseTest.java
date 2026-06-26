package com.guidinglight.nexusquant.paper.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Cause;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Confidence;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Severity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PaperExecutionDiagnosticsResponse.from() 映射单测：验证字段全量映射、枚举名字符串序列化、
 * Paper-only safety 声明，以及空结构稳定映射。
 */
class PaperExecutionDiagnosticsResponseTest {

    @Test
    void fromShouldMapAllFieldsAndSerializeEnumsAsNames() {
        var run = new PaperExecutionDiagnostics.RunDiagnostics(
                "run-1", "sv-1", "pub-1", "STOPPED", 4, 2,
                new BigDecimal("94000"), new BigDecimal("100000"), new BigDecimal("-6000"),
                new BigDecimal("-0.06"), new BigDecimal("-0.06"), true, 1,
                Cause.RISK_BLOCKED, List.of(Cause.FILLED_LOSS, Cause.HIGH_DRAWDOWN),
                Severity.CRITICAL, Confidence.HIGH, "explain", "check risk", Instant.parse("2026-06-02T00:00:00Z"));
        var group = new PaperExecutionDiagnostics.GroupDiagnostics(
                "sv-1", 2, Cause.RISK_BLOCKED, List.of(Cause.RISK_BLOCKED, Cause.FILLED_LOSS),
                0, 0, 1, 1, 0, 1, Severity.CRITICAL, Confidence.HIGH);
        var distribution = new PaperExecutionDiagnostics.CauseDistribution(
                Cause.RISK_BLOCKED, 1, Severity.CRITICAL, Confidence.HIGH, "风控拦截 run");
        var diagnostics = new PaperExecutionDiagnostics(
                new PaperExecutionDiagnostics.Overview(2, 0, 0, 1, 1, 1, 0, 1, 0, 1),
                List.of(distribution), List.of(run), List.of(group), List.of(group));

        var response = PaperExecutionDiagnosticsResponse.from(diagnostics);

        assertEquals(2, response.overview().totalRuns());
        assertEquals(1, response.overview().filledLossRunCount());

        var distResp = response.causeDistribution().get(0);
        assertEquals("RISK_BLOCKED", distResp.cause());
        assertEquals("CRITICAL", distResp.severity());
        assertEquals("HIGH", distResp.confidence());

        var runResp = response.runDiagnostics().get(0);
        assertEquals("run-1", runResp.paperRunId());
        assertEquals("RISK_BLOCKED", runResp.primaryCause());
        assertEquals(List.of("FILLED_LOSS", "HIGH_DRAWDOWN"), runResp.secondaryCauses());
        assertEquals("CRITICAL", runResp.severity());
        assertEquals("HIGH", runResp.causeConfidence());
        assertEquals(0, new BigDecimal("-6000").compareTo(runResp.totalPnl()));

        var groupResp = response.strategyDiagnostics().get(0);
        assertEquals("sv-1", groupResp.key());
        assertEquals("RISK_BLOCKED", groupResp.primaryCause());
        assertEquals(List.of("RISK_BLOCKED", "FILLED_LOSS"), groupResp.topCauses());
        assertEquals(1, response.publishDiagnostics().size());

        // Paper-only safety：SIM/PAPER、LIVE 未开启、未触达真实交易所，文案声明不构成投资建议。
        assertEquals("SIM/PAPER", response.safety().environment());
        assertFalse(response.safety().liveEnabled());
        assertFalse(response.safety().realExchangeTouched());
        assertTrue(response.safety().message().contains("不构成真实投资建议"));
    }

    @Test
    void fromShouldMapEmptyStructureStably() {
        var empty = new PaperExecutionDiagnostics(
                new PaperExecutionDiagnostics.Overview(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(), List.of(), List.of(), List.of());

        var response = PaperExecutionDiagnosticsResponse.from(empty);

        assertEquals(0, response.overview().totalRuns());
        assertTrue(response.causeDistribution().isEmpty());
        assertTrue(response.runDiagnostics().isEmpty());
        assertTrue(response.strategyDiagnostics().isEmpty());
        assertTrue(response.publishDiagnostics().isEmpty());
        assertEquals("SIM/PAPER", response.safety().environment());
    }
}
