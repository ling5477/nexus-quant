package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryEvent;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckSeverity;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * PaperRunSummaryAssembler —— 纯函数式聚合器，把已查询出的 Paper run 事实归纳为 {@link PaperRunSummary}。
 *
 * 设计要点：
 * 1) 无任何 IO / 仓储 / 外呼依赖，便于单测覆盖空数据、风控拦截、告警、恢复等分支。
 * 2) 复盘 / 诊断 / 时间线文案与前端 Loop-5/6/7 保持同口径，便于前端直接消费、移除重复派生。
 * 3) 后端 run 状态机不含 CANCELLED，故不产出 RUN_CANCELLED 分支（对应分支在前端兜底）。
 */
public final class PaperRunSummaryAssembler {

    /** 终态集合：用于运行时长结束点与"终态仍无订单"判定。 */
    private static final Set<PaperTradingRunStatus> TERMINAL_STATUSES =
            Set.of(PaperTradingRunStatus.STOPPED, PaperTradingRunStatus.FAILED);

    private PaperRunSummaryAssembler() {
    }

    public static PaperRunSummary assemble(
            PaperTradingRun run,
            List<PaperTradingOrder> orders,
            List<PaperTradingTrade> trades,
            List<PaperTradingPosition> positions,
            List<PaperRiskCheckResult> riskResults,
            List<EquityCurveSnapshot> equityCurve,
            List<PaperRunDailyReport> dailyReports,
            List<PaperRunAlert> alerts,
            List<PaperRunRecoveryEvent> recoveryEvents,
            Instant now
    ) {
        List<PaperTradingOrder> orderList = orders != null ? orders : List.of();
        List<PaperTradingTrade> tradeList = trades != null ? trades : List.of();
        List<PaperTradingPosition> positionList = positions != null ? positions : List.of();
        List<PaperRiskCheckResult> riskList = riskResults != null ? riskResults : List.of();
        List<EquityCurveSnapshot> equityList = equityCurve != null ? equityCurve : List.of();
        List<PaperRunDailyReport> reportList = dailyReports != null ? dailyReports : List.of();
        List<PaperRunAlert> alertList = alerts != null ? alerts : List.of();
        List<PaperRunRecoveryEvent> recoveryList = recoveryEvents != null ? recoveryEvents : List.of();

        // ---- latest ----
        PaperTradingOrder latestOrder = latest(orderList, PaperTradingOrder::updatedAt);
        PaperTradingTrade latestTrade = latest(tradeList, PaperTradingTrade::tradedAt);
        PaperTradingPosition latestPosition = latest(positionList, PaperTradingPosition::updatedAt);
        EquityCurveSnapshot latestEquity = latest(equityList, EquityCurveSnapshot::snapshotTime);
        PaperRiskCheckResult latestRisk = latest(riskList, PaperRiskCheckResult::createdAt);
        PaperRunAlert latestAlert = latest(alertList, PaperRunAlert::createdAt);
        PaperRunRecoveryEvent latestRecovery = latest(recoveryList, PaperRunRecoveryEvent::createdAt);
        PaperRunDailyReport latestReport = reportList.stream()
                .max(Comparator.comparing(PaperRunDailyReport::reportDate))
                .orElse(null);

        // ---- counts ----
        int orderCount = orderList.size();
        int tradeCount = tradeList.size();
        int positionCount = positionList.size();
        int openAlertCount = (int) alertList.stream()
                .filter(a -> a.status() == PaperRunAlertStatus.OPEN)
                .count();
        int recoveryEventCount = recoveryList.size();
        var counts = new PaperRunSummary.Counts(
                orderCount, tradeCount, tradeCount, positionCount, openAlertCount, recoveryEventCount);

        // ---- net PnL（优先权益快照，其次持仓汇总，最后日报）----
        BigDecimal netPnl;
        if (latestEquity != null) {
            netPnl = sum(latestEquity.realizedPnl(), latestEquity.unrealizedPnl());
        } else if (latestPosition != null) {
            netPnl = sum(latestPosition.realizedPnl(), latestPosition.unrealizedPnl());
        } else if (latestReport != null) {
            netPnl = latestReport.dailyPnl();
        } else {
            netPnl = null;
        }

        // ---- 风控判定（与运行结果复盘同口径）----
        boolean riskBlocked = isRiskBlocked(latestRisk);
        String riskResultText = classifyRiskResult(latestRisk, riskBlocked);

        // ---- result review ----
        var resultReview = buildResultReview(run, orderCount, tradeCount, netPnl, riskResultText, riskBlocked, now);

        // ---- diagnoses ----
        List<PaperRunSummary.Diagnosis> diagnoses = buildDiagnoses(
                run.status(), orderCount, tradeCount, netPnl, riskBlocked,
                latestRisk != null, openAlertCount, recoveryEventCount);

        // ---- timeline ----
        List<PaperRunSummary.TimelineEntry> timeline = buildTimeline(
                run, latestOrder, latestTrade, latestPosition, latestEquity, latestRisk, netPnl);

        var latestBlock = new PaperRunSummary.Latest(
                latestOrder, latestTrade, latestPosition, latestEquity,
                latestReport, latestRisk, latestAlert, latestRecovery);

        return new PaperRunSummary(run, counts, latestBlock, resultReview, diagnoses, timeline);
    }

    private static PaperRunSummary.ResultReview buildResultReview(
            PaperTradingRun run, int orderCount, int fillCount, BigDecimal netPnl,
            String riskResultText, boolean riskBlocked, Instant now
    ) {
        PaperTradingRunStatus status = run.status();
        boolean hasExecution = orderCount > 0 || fillCount > 0;

        String conclusion;
        String level;
        if (riskBlocked) {
            conclusion = "风控拦截";
            level = "danger";
        } else if (status == PaperTradingRunStatus.FAILED) {
            conclusion = "异常停止";
            level = "danger";
        } else if (status == PaperTradingRunStatus.RUNNING) {
            conclusion = "仍在运行";
            level = "info";
        } else if (status == PaperTradingRunStatus.CREATED && orderCount == 0) {
            conclusion = "尚未启动";
            level = "info";
        } else if (status == PaperTradingRunStatus.STOPPED && orderCount == 0) {
            conclusion = "无交易";
            level = "warning";
        } else if (status == PaperTradingRunStatus.STOPPED && hasExecution && "通过".equals(riskResultText)) {
            conclusion = "正常完成";
            level = "info";
        } else {
            conclusion = "数据不足，待观察";
            level = "info";
        }

        // 运行时长结束点：优先 stoppedAt；RUNNING 用 now；其它用 updatedAt。
        Instant end = run.stoppedAt() != null
                ? run.stoppedAt()
                : (status == PaperTradingRunStatus.RUNNING ? now : run.updatedAt());

        return new PaperRunSummary.ResultReview(
                status.name(),
                formatRuntimeDuration(run.startedAt(), end),
                netPnl,
                riskResultText,
                conclusion,
                level
        );
    }

    private static List<PaperRunSummary.Diagnosis> buildDiagnoses(
            PaperTradingRunStatus status, int orderCount, int fillCount, BigDecimal netPnl,
            boolean riskBlocked, boolean hasRiskData, int openAlertCount, int recoveryCount
    ) {
        boolean terminal = TERMINAL_STATUSES.contains(status);
        List<PaperRunSummary.Diagnosis> diagnoses = new ArrayList<>();

        // 风控拦截优先解释，并抑制 NO_ORDER / NO_FILL 噪声。
        if (riskBlocked) {
            diagnoses.add(new PaperRunSummary.Diagnosis(
                    "RISK_BLOCKED", "BLOCKING", "风控拦截",
                    "风控拦截：订单可能未进入交易执行链路，请优先查看风控检查结果。",
                    "风控状态卡片、风控结果 Tab"));
        }
        if (status == PaperTradingRunStatus.FAILED) {
            diagnoses.add(new PaperRunSummary.Diagnosis(
                    "RUN_FAILED", "BLOCKING", "运行异常结束",
                    "运行异常结束：请检查告警、恢复事件和运行状态。",
                    "告警面板、恢复事件、心跳与运行状态"));
        }

        boolean blockedOrFailed = riskBlocked || status == PaperTradingRunStatus.FAILED;
        if (!blockedOrFailed && orderCount == 0) {
            diagnoses.add(new PaperRunSummary.Diagnosis(
                    "NO_ORDER", terminal ? "WARNING" : "INFO", "未发现订单",
                    terminal
                            ? "未发现订单：本次 Paper run 结束时仍无任何订单事实。"
                            : "未发现订单：本次 Paper run 可能尚未触发策略信号，或仍处于未启动状态。",
                    "运行状态、调度触发、策略信号、订单事实表"));
        } else if (!blockedOrFailed && orderCount > 0 && fillCount == 0) {
            diagnoses.add(new PaperRunSummary.Diagnosis(
                    "NO_FILL", "WARNING", "有订单但暂无成交",
                    "存在订单但暂无成交：请检查订单状态、价格条件与撮合结果。",
                    "订单状态、成交事实表、价格条件"));
        }

        // 数据不足：无成交、无净 PnL、无风控数据，不足以形成复盘结论。
        if (fillCount == 0 && netPnl == null && !hasRiskData) {
            diagnoses.add(new PaperRunSummary.Diagnosis(
                    "DATA_INSUFFICIENT", "INFO", "数据不足",
                    "数据不足：当前执行事实不足以形成完整复盘结论。",
                    "订单、成交、净 PnL、风控结果"));
        }

        if (netPnl != null && netPnl.signum() < 0) {
            diagnoses.add(new PaperRunSummary.Diagnosis(
                    "PNL_NEGATIVE", "WARNING", "净 PnL 为负",
                    "净 PnL 为负：请复盘成交明细与持仓盈亏。",
                    "成交事实、持仓事实、资金曲线"));
        }

        if (openAlertCount > 0) {
            diagnoses.add(new PaperRunSummary.Diagnosis(
                    "ALERT_PRESENT", "WARNING", "存在未处理告警",
                    "存在 " + openAlertCount + " 条未处理告警：请查看告警面板并确认处理。",
                    "告警面板"));
        }

        if (recoveryCount > 0) {
            diagnoses.add(new PaperRunSummary.Diagnosis(
                    "RECOVERY_PRESENT", "INFO", "存在恢复事件",
                    "存在恢复事件：本次 run 曾触发恢复或重试，请关注是否稳定。",
                    "恢复事件面板"));
        }

        // 兜底：无任何阻断 / 警告级别原因时给出健康结论。
        boolean hasNotable = diagnoses.stream().anyMatch(d -> !"INFO".equals(d.severity()));
        if (!hasNotable) {
            diagnoses.add(new PaperRunSummary.Diagnosis(
                    "HEALTHY", "INFO", "暂无明显异常",
                    "暂无明显异常：可继续查看时间线、订单、成交、持仓和 PnL。",
                    "运行事件时间线、订单、成交、持仓、净 PnL"));
        }

        diagnoses.sort(Comparator.comparingInt(d -> severityRank(d.severity())));
        return diagnoses;
    }

    private static List<PaperRunSummary.TimelineEntry> buildTimeline(
            PaperTradingRun run, PaperTradingOrder latestOrder, PaperTradingTrade latestTrade,
            PaperTradingPosition latestPosition, EquityCurveSnapshot latestEquity,
            PaperRiskCheckResult latestRisk, BigDecimal netPnl
    ) {
        List<PaperRunSummary.TimelineEntry> events = new ArrayList<>();
        PaperTradingRunStatus status = run.status();
        Instant terminalTime = run.stoppedAt() != null
                ? run.stoppedAt()
                : (TERMINAL_STATUSES.contains(status) ? run.updatedAt() : null);

        if (run.createdAt() != null) {
            events.add(new PaperRunSummary.TimelineEntry(
                    "RUN_CREATED", "CREATED", run.createdAt(), "Paper run created",
                    "创建于 " + run.tradeEnv() + "/Paper 环境，发布 ID " + run.publishId() + "。"));
        }
        if (run.startedAt() != null) {
            events.add(new PaperRunSummary.TimelineEntry(
                    "RUN_STARTED", "RUNNING", run.startedAt(), "Paper run started",
                    "运行已进入 SIM/Paper 生命周期，不触发 LIVE 或真实交易所。"));
        }
        if (terminalTime != null) {
            events.add(new PaperRunSummary.TimelineEntry(
                    "RUN_TERMINAL", status.name(), terminalTime,
                    "Paper run " + status.name().toLowerCase(),
                    "当前 run 已进入终态；历史订单、成交、持仓和风控事实仍可追溯。"));
        }
        if (latestOrder != null && latestOrder.updatedAt() != null) {
            events.add(new PaperRunSummary.TimelineEntry(
                    "LATEST_ORDER", latestOrder.status().name(), latestOrder.updatedAt(),
                    "最新订单状态事件",
                    latestOrder.side() + " " + latestOrder.orderType() + " " + latestOrder.symbol()
                            + "，数量 " + amount(latestOrder.quantity()) + "，价格 " + amount(latestOrder.price()) + "。"));
        }
        if (latestTrade != null && latestTrade.tradedAt() != null) {
            events.add(new PaperRunSummary.TimelineEntry(
                    "LATEST_TRADE", "FILLED", latestTrade.tradedAt(), "最新成交事件",
                    latestTrade.side() + " " + latestTrade.symbol()
                            + "，成交数量 " + amount(latestTrade.quantity()) + "，成交价 " + amount(latestTrade.price()) + "。"));
        }
        if (latestPosition != null && latestPosition.updatedAt() != null) {
            events.add(new PaperRunSummary.TimelineEntry(
                    "LATEST_POSITION", "POSITION_UPDATED", latestPosition.updatedAt(), "最新持仓更新时间",
                    latestPosition.symbol() + " 持仓 " + amount(latestPosition.quantity())
                            + "，未实现盈亏 " + amount(latestPosition.unrealizedPnl()) + "。"));
        }
        if (latestEquity != null && latestEquity.snapshotTime() != null) {
            String pnlStatus = netPnl == null ? "SNAPSHOT" : (netPnl.signum() >= 0 ? "PNL_UP" : "PNL_DOWN");
            events.add(new PaperRunSummary.TimelineEntry(
                    "LATEST_EQUITY", pnlStatus, latestEquity.snapshotTime(), "最新净 PnL / equity snapshot",
                    "总权益 " + amount(latestEquity.totalEquity()) + "，净 PnL " + amount(netPnl)
                            + "，来源 " + latestEquity.source() + "。"));
        }
        if (latestRisk != null && latestRisk.createdAt() != null) {
            events.add(new PaperRunSummary.TimelineEntry(
                    "LATEST_RISK", latestRisk.status().name(), latestRisk.createdAt(), "最新风控检查结果",
                    latestRisk.checkType() + " · " + latestRisk.severity().name()
                            + (latestRisk.message() != null ? "：" + latestRisk.message() : "")));
        }
        return events;
    }

    private static boolean isRiskBlocked(PaperRiskCheckResult latestRisk) {
        if (latestRisk == null) {
            return false;
        }
        return latestRisk.status() == RiskCheckStatus.REJECTED
                || latestRisk.severity() == RiskCheckSeverity.HIGH
                || latestRisk.severity() == RiskCheckSeverity.CRITICAL;
    }

    private static String classifyRiskResult(PaperRiskCheckResult latestRisk, boolean riskBlocked) {
        if (latestRisk == null) {
            return "无数据";
        }
        if (riskBlocked) {
            return "拦截";
        }
        if (latestRisk.status() == RiskCheckStatus.PASSED) {
            return "通过";
        }
        return "警告";
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "BLOCKING" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    private static <T> T latest(List<T> items, Function<T, Instant> timeFn) {
        return items.stream()
                .filter(item -> timeFn.apply(item) != null)
                .max(Comparator.comparing(timeFn))
                .orElse(null);
    }

    private static BigDecimal sum(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return null;
        }
        BigDecimal a = left != null ? left : BigDecimal.ZERO;
        BigDecimal b = right != null ? right : BigDecimal.ZERO;
        return a.add(b);
    }

    private static String amount(BigDecimal value) {
        return value != null ? value.toPlainString() : "-";
    }

    /** 运行时长文案，与前端 formatRuntimeDuration 同口径（小时/分钟/秒）。 */
    static String formatRuntimeDuration(Instant start, Instant end) {
        if (start == null || end == null || end.isBefore(start)) {
            return "-";
        }
        long totalSeconds = Duration.between(start, end).getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + " 小时 " + minutes + " 分钟";
        }
        if (minutes > 0) {
            return minutes + " 分钟 " + seconds + " 秒";
        }
        return seconds + " 秒";
    }
}
