package com.guidinglight.nexusquant.research.application.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperExecutionDiagnostics —— Paper 执行诊断只读聚合事实源（GateK Batch K1）。
 *
 * 职责：在 {@link PaperPortfolioSummary} 已有「发生了什么」的基础上，对每个 Paper run 做规则化归因，
 * 回答「为什么无订单 / 为什么有订单无成交 / 为什么成交但亏损 / 为什么风控拦截 / 为什么数据不足」，
 * 并按 strategyVersionId / publishId 聚合定位问题集中的策略与发布版本。
 *
 * 关键约束：
 * 1) 纯派生：不写库、不触发任何状态机 / 下单 / 调度 / 回测 / 发布动作、不外呼、不读凭证。
 * 2) 仅覆盖 SIM/Paper：归因结论是 Paper 模拟执行事实的诊断，<b>不构成真实投资建议、不代表 LIVE 或真实交易表现</b>。
 * 3) 归因为规则化推断（非 AI）：每条诊断给出 {@code causeConfidence} 表达可信度；事实缺失时降级为
 *    {@code MEDIUM}/{@code LOW} 而非抛异常；无 run 时返回稳定空结构。
 * 4) 收益率 / 回撤为比例值（如 -0.1 表示回撤 10%）；数据不足时相关字段为 null，不外推、不伪造。
 */
public record PaperExecutionDiagnostics(
        Overview overview,
        List<CauseDistribution> causeDistribution,
        List<RunDiagnostics> runDiagnostics,
        List<GroupDiagnostics> strategyDiagnostics,
        List<GroupDiagnostics> publishDiagnostics
) {

    /**
     * 诊断归因枚举（规则化分类）。枚举常量声明顺序即 primaryCause 优先级：靠前者更紧急、优先作为主因。
     * 同一 run 命中多个 cause 时，主因取优先级最高者，其余进入 secondaryCauses。
     */
    public enum Cause {
        /** Run 处于 FAILED 终态，执行链路异常终止。 */
        FAILED_RUN,
        /** 缺少可用权益/初始资金，无法计算收益率与盈亏。 */
        DATA_INSUFFICIENT,
        /** 风控规则阻断执行或判定为高风险结果。 */
        RISK_BLOCKED,
        /** 已产生订单但未形成成交。 */
        ORDER_NO_FILL,
        /** 无任何订单与成交。 */
        NO_ORDER,
        /** 已成交但当前 PnL 为负。 */
        FILLED_LOSS,
        /** 单 run 最大回撤达到/超过阈值。 */
        HIGH_DRAWDOWN,
        /** 仍在运行但暂无足够订单/成交/权益事实，结果尚未形成。 */
        RUNNING_NO_RESULT,
        /** 已成交、非亏损、非高回撤、无风控拦截、数据充分。 */
        HEALTHY,
        /** 现有事实不足以归因。 */
        UNKNOWN
    }

    /** 诊断严重度。 */
    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    /**
     * 归因可信度。
     * HIGH：由明确事实直接判断（orderCount==0 / tradeCount==0 / riskBlocked / dataInsufficient / FAILED）。
     * MEDIUM：由组合事实推断（如 CREATED 尚未启动、RUNNING 结果未形成）。
     * LOW：事实缺失或语义不统一，只能粗略推断。
     */
    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * 诊断总览（按事实独立计数，桶之间可重叠，例如 filledRunCount 与 filledLossRunCount）。
     * totalRuns 为纳入诊断的 bounded run 总数。
     */
    public record Overview(
            int totalRuns,
            int noOrderRunCount,
            int orderNoFillRunCount,
            int filledRunCount,
            int filledLossRunCount,
            int riskBlockedRunCount,
            int dataInsufficientRunCount,
            int highDrawdownRunCount,
            int failedRunCount,
            int runningRunCount
    ) {}

    /**
     * 主因分布：按 run 的 primaryCause 聚合计数。
     * severity / confidence 为该 cause 的代表性取值（不含单 run 状态细分），description 为面向排查的说明。
     */
    public record CauseDistribution(
            Cause cause,
            int count,
            Severity severity,
            Confidence confidence,
            String description
    ) {}

    /**
     * 单 run 诊断记录。
     * primaryCause 为主因，secondaryCauses 为其余命中的辅助原因（按优先级排序）；
     * severity / causeConfidence 取主因口径；explanation / suggestedAction 为 Paper-only 排查说明，不构成投资建议。
     */
    public record RunDiagnostics(
            String paperRunId,
            String strategyVersionId,
            String publishId,
            String status,
            int orderCount,
            int tradeCount,
            BigDecimal currentEquity,
            BigDecimal initialEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal maxDrawdown,
            boolean riskBlocked,
            int openAlertCount,
            Cause primaryCause,
            List<Cause> secondaryCauses,
            Severity severity,
            Confidence causeConfidence,
            String explanation,
            String suggestedAction,
            Instant lastRunTime
    ) {}

    /**
     * strategyVersionId / publishId 维度聚合诊断。
     * primaryCause 取组内最紧急主因；topCauses 为组内主因按出现频次（再按优先级）排序的前若干项；
     * 各计数为组内按事实独立统计（可重叠）。
     */
    public record GroupDiagnostics(
            String key,
            int runCount,
            Cause primaryCause,
            List<Cause> topCauses,
            int noOrderCount,
            int orderNoFillCount,
            int filledLossCount,
            int riskBlockedCount,
            int dataInsufficientCount,
            int highDrawdownCount,
            Severity severity,
            Confidence causeConfidence
    ) {}
}
