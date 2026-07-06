package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Shadow Run 侧只读比较输入。
 *
 * <p>职责：承载 runner / decision trace 已生成的 Shadow 摘要。Why：GateR-5 不启动 runner、
 * 不执行策略、不调用真实交易所，也不读取 private endpoint；该输入只代表调用方提供的本地
 * Shadow facts 摘要，用于生成脱敏 consistency report。
 *
 * @param shadowRunId             Shadow Run id；必须与 command.shadowRunId 一致
 * @param shadowStatus            Shadow Run 当前状态摘要；终态/阻断态只作为复盘上下文
 * @param shadowOrderIntentCount  Shadow order intent preview 数量；不是真实订单数量
 * @param shadowBlockedCount      Shadow 风险阻断数量摘要；可空表示不可用
 * @param shadowWarningCount      Shadow 告警数量摘要；可空表示不可用
 * @param expectedSide            Shadow 侧预期方向；只读比较字段，不触发 BUY/SELL
 * @param symbol                  Shadow 侧标的
 * @param timeframe               Shadow 侧周期
 * @param windowStart             Shadow 侧比较窗口开始时间
 * @param windowEnd               Shadow 侧比较窗口结束时间
 * @param strategyVersionId       Shadow 侧策略版本 id
 * @param datasetId               Shadow 侧数据集 id
 * @param summaryPayload          调用方提供的脱敏 Shadow 摘要 payload；只读、不可含敏感字段
 */
public record ShadowRunComparisonInput(
        UUID shadowRunId,
        ShadowRunStatus shadowStatus,
        Integer shadowOrderIntentCount,
        Integer shadowBlockedCount,
        Integer shadowWarningCount,
        String expectedSide,
        String symbol,
        String timeframe,
        Instant windowStart,
        Instant windowEnd,
        String strategyVersionId,
        UUID datasetId,
        JsonNode summaryPayload
) {

    public ShadowRunComparisonInput {
        expectedSide = PaperRunComparisonInput.trimToNull(expectedSide);
        symbol = PaperRunComparisonInput.trimToNull(symbol);
        timeframe = PaperRunComparisonInput.trimToNull(timeframe);
        strategyVersionId = PaperRunComparisonInput.trimToNull(strategyVersionId);
        summaryPayload = summaryPayload == null ? JsonNodeFactory.instance.objectNode() : summaryPayload;
        ShadowRunSensitiveDataGuard.validateJson("shadowSummaryPayload", summaryPayload);
        if (!summaryPayload.isObject()) {
            throw new IllegalArgumentException("shadowSummaryPayload must be a JSON object");
        }
    }
}
