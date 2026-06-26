package com.guidinglight.nexusquant.paper.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.BacktestDeviation;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.DeviationLevel;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.EvaluationConfidence;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.RatingLabel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PaperStrategyEvaluationResponse.from() 映射单测：字段全量映射、枚举名字符串序列化、Paper-only safety，
 * 以及空结构稳定映射与 null backtestDeviation 处理。
 */
class PaperStrategyEvaluationResponseTest {

    @Test
    void fromShouldMapAllFieldsAndSerializeEnumsAsNames() {
        var deviation = new BacktestDeviation(
                new BigDecimal("0.5"), new BigDecimal("0.1"), new BigDecimal("-0.4"),
                new BigDecimal("-0.05"), new BigDecimal("-0.03"), new BigDecimal("0.02"),
                DeviationLevel.HIGH, "偏差较大");
        var strategy = new PaperStrategyEvaluation.StrategyEvaluation(
                "sv-1", 3, 3, 1, Instant.parse("2026-06-02T00:00:00Z"),
                new BigDecimal("330000"), new BigDecimal("300000"), new BigDecimal("30000"),
                new BigDecimal("0.1"), new BigDecimal("-0.03"),
                3, 0, new BigDecimal("1"), new BigDecimal("0.1"), new BigDecimal("-0.03"),
                0, 0, 0, 0, 3, 0, 0,
                40, 90, 75, 100, 20, 80,
                RatingLabel.HIGH_RISK, EvaluationConfidence.MEDIUM, "BACKTEST_DEVIATION",
                List.of("BACKTEST_PAPER_DEVIATION_HIGH"), deviation);
        var publish = new PaperStrategyEvaluation.PublishEvaluation(
                "pub-1", "sv-1", 3, 3, Instant.parse("2026-06-02T00:00:00Z"),
                new BigDecimal("30000"), new BigDecimal("0.1"), new BigDecimal("-0.03"), new BigDecimal("1"),
                40, 90, 75, 100, 20, 80, RatingLabel.HIGH_RISK, EvaluationConfidence.MEDIUM,
                List.of("BACKTEST_PAPER_DEVIATION_HIGH"), deviation);
        var evaluation = new PaperStrategyEvaluation(
                new PaperStrategyEvaluation.Overview(1, 1, 3, 3, 0, 1, 0, 1, 1, 80, 80),
                List.of(strategy), List.of(publish),
                new PaperStrategyEvaluation.Rankings(
                        List.of("sv-1"), List.of("sv-1"), List.of("sv-1"), List.of("sv-1"),
                        List.of(), List.of("sv-1"), List.of("sv-1")));

        var response = PaperStrategyEvaluationResponse.from(evaluation);

        assertEquals(1, response.overview().strategyCount());
        assertEquals(80, response.overview().topCompositeScore());

        var s = response.strategyEvaluations().get(0);
        assertEquals("sv-1", s.strategyVersionId());
        assertEquals("HIGH_RISK", s.ratingLabel());
        assertEquals("MEDIUM", s.evaluationConfidence());
        assertEquals(20, s.backtestDeviationScore());
        assertEquals("HIGH", s.backtestDeviation().deviationLevel());
        assertEquals(0, new BigDecimal("-0.4").compareTo(s.backtestDeviation().returnDeviation()));
        assertTrue(s.warnings().contains("BACKTEST_PAPER_DEVIATION_HIGH"));

        var p = response.publishEvaluations().get(0);
        assertEquals("pub-1", p.publishId());
        assertEquals("sv-1", p.strategyVersionId());
        assertEquals("HIGH_RISK", p.ratingLabel());

        assertEquals(List.of("sv-1"), response.rankings().highRiskStrategies());

        // Paper-only safety：非真实投资评级 / 不构成投资建议。
        assertEquals("SIM/PAPER", response.safety().environment());
        assertFalse(response.safety().liveEnabled());
        assertFalse(response.safety().realExchangeTouched());
        assertTrue(response.safety().message().contains("不构成投资建议"));
        assertTrue(response.safety().message().contains("不是真实投资评级"));
    }

    @Test
    void fromShouldMapEmptyAndNullDeviationStably() {
        var strategy = new PaperStrategyEvaluation.StrategyEvaluation(
                "sv-empty", 1, 0, 0, null, null, null, null, null, null,
                0, 0, null, null, null, 0, 1, 1, 0, 0, 0, 0,
                10, 100, 0, 0, null, 51,
                RatingLabel.DATA_INSUFFICIENT, EvaluationConfidence.LOW, "RETURN",
                List.of("DATA_INSUFFICIENT", "BACKTEST_DATA_UNAVAILABLE"), null);
        var evaluation = new PaperStrategyEvaluation(
                new PaperStrategyEvaluation.Overview(1, 0, 1, 0, 0, 0, 0, 0, 0, 51, 51),
                List.of(strategy), List.of(),
                new PaperStrategyEvaluation.Rankings(List.of("sv-empty"), List.of("sv-empty"),
                        List.of(), List.of(), List.of(), List.of(), List.of()));

        var response = PaperStrategyEvaluationResponse.from(evaluation);

        var s = response.strategyEvaluations().get(0);
        assertEquals("DATA_INSUFFICIENT", s.ratingLabel());
        assertNull(s.backtestDeviationScore());
        assertNull(s.backtestDeviation()); // null 偏差稳定映射为 null
        assertTrue(response.publishEvaluations().isEmpty());
        assertEquals("SIM/PAPER", response.safety().environment());
    }
}
