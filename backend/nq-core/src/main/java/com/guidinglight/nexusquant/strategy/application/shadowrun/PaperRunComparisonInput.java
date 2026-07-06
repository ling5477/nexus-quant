package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;

import java.time.Instant;
import java.util.UUID;

/**
 * Paper run 侧只读比较输入。
 *
 * <p>职责：承载调用方已经准备好的 Paper 运行摘要。Why：GateR-5 不主动查询 Paper 订单表、
 * 真实交易所、账户余额或 ledger；调用方传入的 summary 是唯一比较来源，且 {@code summaryPayload}
 * 必须通过 Shadow Run sensitive guard，避免 credential、真实订单和交易授权语义进入 report。
 *
 * @param paperRunId        Paper run id；缺失时 report 应进入 NOT_COMPARABLE
 * @param paperOrderCount   Paper 侧订单数量摘要；可空表示该指标不可用
 * @param paperBlockedCount Paper 侧阻断数量摘要；可空表示该指标不可用
 * @param paperWarningCount Paper 侧告警数量摘要；可空表示该指标不可用
 * @param actualPaperSide   Paper 侧实际方向摘要；可空表示该指标不可用
 * @param symbol            Paper 侧标的
 * @param timeframe         Paper 侧周期
 * @param windowStart       Paper 侧比较窗口开始时间
 * @param windowEnd         Paper 侧比较窗口结束时间
 * @param strategyVersionId Paper 侧策略版本 id
 * @param datasetId         Paper 侧数据集 id
 * @param summaryPayload    调用方提供的脱敏 Paper 摘要 payload；只读、不可含敏感字段
 */
public record PaperRunComparisonInput(
        String paperRunId,
        Integer paperOrderCount,
        Integer paperBlockedCount,
        Integer paperWarningCount,
        String actualPaperSide,
        String symbol,
        String timeframe,
        Instant windowStart,
        Instant windowEnd,
        String strategyVersionId,
        UUID datasetId,
        JsonNode summaryPayload
) {

    public PaperRunComparisonInput {
        paperRunId = trimToNull(paperRunId);
        actualPaperSide = trimToNull(actualPaperSide);
        symbol = trimToNull(symbol);
        timeframe = trimToNull(timeframe);
        strategyVersionId = trimToNull(strategyVersionId);
        summaryPayload = summaryPayload == null ? JsonNodeFactory.instance.objectNode() : summaryPayload;
        ShadowRunSensitiveDataGuard.validateJson("paperSummaryPayload", summaryPayload);
        if (!summaryPayload.isObject()) {
            throw new IllegalArgumentException("paperSummaryPayload must be a JSON object");
        }
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
