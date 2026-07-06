package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * Paper vs Shadow 单个可比指标的差异记录。
 *
 * <p>职责：把调用方提供的只读 summary 指标转换为稳定、可序列化的 report 条目。Why：GateR-5
 * 只做本地一致性复盘，不主动读取 Paper 订单、真实订单、账户余额或 ledger；因此所有值都保留为
 * 字符串化摘要，避免把本地 comparison 误写成交易执行事实。
 *
 * @param metricName      指标名，例如 orderIntentCount、blockedCount、side
 * @param paperValue      Paper 侧只读摘要值；缺失时为 null
 * @param shadowValue     Shadow 侧只读摘要值；缺失时为 null
 * @param delta           差异摘要；count 类为 shadow-paper，文本类为 MATCH/MISMATCH/NOT_COMPARABLE
 * @param tolerance       本轮使用的阈值摘要
 * @param comparable      两侧是否具备可比较输入
 * @param withinTolerance 可比时是否在阈值内
 */
public record ConsistencyMetricDelta(
        String metricName,
        String paperValue,
        String shadowValue,
        String delta,
        String tolerance,
        boolean comparable,
        boolean withinTolerance
) {

    public ConsistencyMetricDelta {
        metricName = StrategyDecisionTrace.requireText(metricName, "metricName");
        delta = StrategyDecisionTrace.requireText(delta, "delta");
        tolerance = StrategyDecisionTrace.requireText(tolerance, "tolerance");
    }
}
