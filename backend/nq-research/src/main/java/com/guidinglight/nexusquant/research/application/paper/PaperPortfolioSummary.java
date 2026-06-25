package com.guidinglight.nexusquant.research.application.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperPortfolioSummary —— Paper 组合看板只读聚合事实源（GateJ 后产品化 Loop-13）。
 *
 * 职责：把多个 Paper run 的已有事实（run / equity 快照 / 日报 / 风控 / 告警 / 成交计数 / 来源）
 * 归纳为组合级总览、策略/发布维度排行、Run 排行与数据质量提示，供前端组合看板直接消费。
 * 关键约束：
 * 1) 纯派生，不写库、不触发任何状态机 / 下单 / 调度 / 回测 / 发布动作。
 * 2) 只覆盖 SIM/Paper，不读取真实交易所账户余额，不代表 LIVE 或真实交易表现。
 * 3) 所有收益率 / 回撤为比例值（如 0.1 表示 +10%，-0.25 表示回撤 25%）；
 *    数据不足时相关字段为 null，不外推、不伪造收益率。
 */
public record PaperPortfolioSummary(
        Overview overview,
        List<Group> strategyGroups,
        List<Group> publishGroups,
        Highlights highlights,
        DataQuality dataQuality,
        PortfolioCurve portfolioCurve
) {

    /**
     * 组合总览。
     * totalInitialEquity / totalCurrentEquity / totalPnl / totalReturn 仅基于「同时具备
     * 当前权益与初始资金」的可比 run（returnEligibleRunCount），避免对不同口径的 run 求和后相减导致误导。
     * worstRunDrawdown 为「按单 run 最大回撤统计」的最差值（最负的比例），无可计算回撤时为 null。
     */
    public record Overview(
            int totalRuns,
            int runningCount,
            int stoppedCount,
            int failedCount,
            int cancelledCount,
            int createdCount,
            BigDecimal totalInitialEquity,
            BigDecimal totalCurrentEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            int returnEligibleRunCount,
            BigDecimal worstRunDrawdown,
            int openAlertCount,
            int riskBlockedRunCount,
            int noTradeRunCount,
            int dataInsufficientRunCount
    ) {}

    /**
     * 策略版本 / 发布维度分组。
     * currentEquity / totalPnl / totalReturn 仅基于组内可比 run；worstDrawdown 为组内最差单 run 回撤。
     * lastRunTime 为组内最近活动时间（startedAt / stoppedAt / updatedAt / createdAt 取最大）。
     *
     * Loop-17 向后兼容新增组内精确计数（均基于「完整 bounded runs」聚合，不依赖 highlights/dataQuality 截断列表）：
     * noTradeCount（tradeCount==0 的 run 数，no-fill/no-trade 简化口径）、dataInsufficientCount（无法计算
     * currentEquity/initialEquity 的 run 数，与 overview/dataQuality 同口径）、comparableRunCount（可计算收益率的
     * 可比 run 数）、runningCount/stoppedCount/failedCount/cancelledCount/createdCount（按 run.status 聚合）。
     */
    public record Group(
            String key,
            int runCount,
            BigDecimal currentEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal worstDrawdown,
            int riskBlockedCount,
            int openAlertCount,
            Instant lastRunTime,
            int noTradeCount,
            int dataInsufficientCount,
            int comparableRunCount,
            int runningCount,
            int stoppedCount,
            int failedCount,
            int cancelledCount,
            int createdCount
    ) {}

    /**
     * Run 维度精简引用，用于排行榜与数据质量清单。
     * 金额 / 收益率 / 回撤字段可为 null（数据不足）。
     */
    public record RunRef(
            String paperRunId,
            String status,
            String symbol,
            String strategyVersionId,
            String publishId,
            BigDecimal currentEquity,
            BigDecimal initialEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal maxDrawdown,
            boolean riskBlocked,
            int openAlertCount,
            int tradeCount,
            Instant lastActivityAt
    ) {}

    /**
     * Run 排行：收益最高 / 回撤最大 / 风险最高 / 最近活跃为单个 run（可为 null）；
     * 无交易 / 风控拦截为 run 清单（已截断到上限）。
     */
    public record Highlights(
            RunRef topWinner,
            RunRef worstDrawdown,
            RunRef highestRisk,
            RunRef mostRecent,
            List<RunRef> noTradeRuns,
            List<RunRef> riskBlockedRuns
    ) {}

    /** 数据质量提示：缺 equity / 数据不足 / 缺 backtest 来源 / 缺 publish 来源的 run 清单（已截断到上限）。 */
    public record DataQuality(
            List<RunRef> missingEquityRuns,
            List<RunRef> dataInsufficientRuns,
            List<RunRef> missingBacktestSourceRuns,
            List<RunRef> missingPublishSourceRuns
    ) {}

    /**
     * 组合级 equity / drawdown 时间序列（GateJ 后产品化 Loop-15，向后兼容新增）。
     *
     * 口径（简化版组合曲线，非时间加权收益率）：
     * 1) 仅纳入「可比 run」（有 equity 快照且可回推初始资金，与单 run 同口径）。
     * 2) 时间轴取所有可比 run 快照时间的并集；每个时间点对每个可比 run 取「该点及之前最近一笔快照」的权益（阶梯保持）。
     * 3) totalEquity / totalInitialEquity 只对该点「已可用」run 求和，二者口径一致，保证 totalPnl/totalReturn 可解释；
     *    尚未产生首笔快照的可比 run 计入该点 missingRunCount，不计入权益与初始资金（不伪造）。
     * 4) running peak = max(totalEquity)；drawdown = (totalEquity - peak)/peak；
     *    maxDrawdown = min(drawdown)，currentDrawdown = 最后一个点的 drawdown。
     * 5) 数据不足（无可比 run / 无有效点 / 初始资金合计 <=0）时 points 为空、指标为 null，不外推、不伪造回撤。
     *
     * 注意：run 在不同时间起跑会使 totalEquity 在组合「成员变化」时阶跃，因此本曲线是组合资金合计曲线，
     * 不等同真实时间加权组合收益；展示层须标注「Paper 模拟、简化组合曲线」。
     */
    public record PortfolioCurve(
            List<CurvePoint> points,
            BigDecimal latestEquity,
            BigDecimal peakEquity,
            BigDecimal currentDrawdown,
            BigDecimal maxDrawdown,
            int pointCount,
            Coverage coverage
    ) {

        /**
         * 单个时间点的组合聚合。
         * peakEquity 为「截至该点」的运行峰值（完整曲线口径，即使 points 被截断也基于完整历史）。
         * sourceRunCount 为该点已贡献权益的可比 run 数；missingRunCount 为尚未产生快照的可比 run 数。
         */
        public record CurvePoint(
                Instant timestamp,
                BigDecimal totalEquity,
                BigDecimal totalInitialEquity,
                BigDecimal totalPnl,
                BigDecimal totalReturn,
                BigDecimal peakEquity,
                BigDecimal drawdown,
                int sourceRunCount,
                int missingRunCount
        ) {}

        /**
         * 覆盖度提示：
         * comparableRunCount 为纳入曲线的可比 run 数；
         * missingEquityRunCount 为无 equity 快照、无法纳入曲线的 run 数；
         * incompletePointCount 为存在缺失 run（missingRunCount &gt; 0）的时间点数。
         */
        public record Coverage(
                int comparableRunCount,
                int missingEquityRunCount,
                int incompletePointCount
        ) {}
    }
}
