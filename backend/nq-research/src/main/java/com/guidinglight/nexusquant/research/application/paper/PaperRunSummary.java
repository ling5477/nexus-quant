package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryEvent;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperRunSummary —— Paper run 只读聚合事实源（GateJ 后产品化 Loop-8）。
 *
 * 职责：把单个 Paper run 的 run / orders / trades / positions / risk / equity / dailyReport /
 * alerts / recovery 等已有事实，归纳为前端详情区可直接消费的复盘、诊断、时间线与关键计数。
 * 关键约束：
 * 1) 纯派生，不写库、不触发任何状态机 / 下单 / 监控 / 调度 / 恢复动作。
 * 2) 不代表真实交易能力；environment 固定 SIM/Paper，LIVE 未开启。
 * 3) latest.* 直接携带领域对象，由 nq-api DTO 层做响应映射，避免重复查询。
 */
public record PaperRunSummary(
        PaperTradingRun run,
        Counts counts,
        Latest latest,
        ResultReview resultReview,
        List<Diagnosis> diagnoses,
        List<TimelineEntry> timeline
) {

    /** 关键计数；tradeCount 与 fillCount 当前同口径（一笔成交即一次 fill）。 */
    public record Counts(
            int orderCount,
            int tradeCount,
            int fillCount,
            int positionCount,
            int openAlertCount,
            int recoveryEventCount
    ) {}

    /** 各事实的最新一条；任一字段可为 null（无该类事实）。 */
    public record Latest(
            PaperTradingOrder order,
            PaperTradingTrade trade,
            PaperTradingPosition position,
            EquityCurveSnapshot equitySnapshot,
            PaperRunDailyReport dailyReport,
            PaperRiskCheckResult riskResult,
            PaperRunAlert alert,
            PaperRunRecoveryEvent recoveryEvent
    ) {}

    /**
     * 运行结果复盘，与前端 Loop-6 口径一致。
     * netPnl 可为 null（无任何 PnL 事实）；conclusionLevel 取值 info / warning / danger。
     */
    public record ResultReview(
            String finalStatus,
            String runtimeDurationText,
            BigDecimal netPnl,
            String riskResult,
            String conclusion,
            String conclusionLevel
    ) {}

    /**
     * 异常原因诊断条目，与前端 Loop-7 口径一致。
     * severity 取值 INFO / WARNING / BLOCKING；type 为稳定枚举字符串。
     */
    public record Diagnosis(
            String type,
            String severity,
            String title,
            String description,
            String checkTarget
    ) {}

    /** 运行事件时间线条目，与前端 Loop-5 口径一致。 */
    public record TimelineEntry(
            String type,
            String status,
            Instant occurredAt,
            String title,
            String description
    ) {}
}
