package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunDecisionContext 是 NQ 发送给 DH dry-run endpoint 的只读、脱敏上下文摘要。
 *
 * @param symbol          标的符号；仅作为上下文，不是 order instruction
 * @param market          市场分类；仅作为上下文，不触发 adapter
 * @param timeframe       时间粒度；仅作为上下文，不触发采集
 * @param scenario        dry-run 场景说明；不得包含交易动作
 * @param evidenceSummary 脱敏证据摘要；不得包含 raw provider response、credential、URL 或 prompt 原文
 * @param riskSummary     脱敏风险摘要；不得包含可执行数量、价格或杠杆
 */
public record DhDryRunDecisionContext(
        String symbol,
        String market,
        String timeframe,
        String scenario,
        String evidenceSummary,
        String riskSummary) {
}
