package com.guidinglight.nexusquant.strategy.domain;

import java.util.List;

/**
 * StrategyRunExecutionResult 聚合运行详情可稳定关联的执行结果摘要。
 * <p>
 * Why:
 * GateE-2.3 的查询面必须以 run 为中心返回结构化摘要，而不是让调用方自己再去拼 orders / trades / ledger。
 * 其中当前无法稳定按 run 关联的 ledger / risk / event，也要明确给出限制说明，避免误导调用方。
 */
public record StrategyRunExecutionResult(
        List<StrategyRunOrderSummary> orders,
        List<StrategyRunTradeSummary> trades,
        String ledgerSummary,
        String riskSummary,
        String eventSummary
) {
}

