package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Cause;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Confidence;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.GroupDiagnostics;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.RunDiagnostics;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Severity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * PaperExecutionDiagnosticsAssembler —— 纯函数式执行诊断聚合器（GateK Batch K1）。
 *
 * 设计要点：
 * 1) 无任何 IO / 仓储 / 外呼依赖，输入为 {@link PaperPortfolioSummary.RunRef}（由组合看板同口径单 run 派生而来），
 *    保证「执行进度三态 / 风控拦截 / 收益率 / 回撤」与组合看板共用一套事实，不另起归因事实分叉。
 * 2) 归因为确定性规则：每个 run 按固定优先级匹配多个 cause，主因取最高优先级者，其余作为 secondaryCauses。
 * 3) 仅 Paper-only：explanation / suggestedAction 为排查向说明，<b>不构成真实投资建议</b>。
 * 4) 事实缺失（无 equity → currentEquity/initialEquity 为 null）时降级为 DATA_INSUFFICIENT 或 MEDIUM 可信度，
 *    不抛异常；空输入返回稳定空结构。
 */
public final class PaperExecutionDiagnosticsAssembler {

    /**
     * 高回撤判定阈值（首版规则阈值，硬编码）：单 run 最大回撤 ≤ -10% 视为高回撤。
     * TODO(2026-06-26): 阈值后续随策略评估配置化 -> 完成条件：引入 Paper 诊断阈值配置项 -> 风险：改变历史诊断口径。
     */
    static final BigDecimal HIGH_DRAWDOWN_THRESHOLD = new BigDecimal("-0.10");

    /** strategy/publish 分组 topCauses 截断上限，避免响应体膨胀。 */
    static final int MAX_TOP_CAUSES = 3;

    private static final String STRATEGY_FALLBACK_KEY = "(未绑定策略版本)";
    private static final String PUBLISH_FALLBACK_KEY = "(未知发布)";

    private PaperExecutionDiagnosticsAssembler() {
    }

    public static PaperExecutionDiagnostics assemble(List<PaperPortfolioSummary.RunRef> runRefs) {
        List<PaperPortfolioSummary.RunRef> safeRefs = runRefs != null ? runRefs : List.of();

        List<RunDiagnostics> runDiagnostics = new ArrayList<>(safeRefs.size());
        for (PaperPortfolioSummary.RunRef ref : safeRefs) {
            runDiagnostics.add(diagnoseRun(ref));
        }

        var overview = buildOverview(safeRefs);
        var causeDistribution = buildCauseDistribution(runDiagnostics);
        var strategyGroups = buildGroups(runDiagnostics, RunDiagnostics::strategyVersionId, STRATEGY_FALLBACK_KEY);
        var publishGroups = buildGroups(runDiagnostics, RunDiagnostics::publishId, PUBLISH_FALLBACK_KEY);

        return new PaperExecutionDiagnostics(
                overview, causeDistribution, List.copyOf(runDiagnostics), strategyGroups, publishGroups);
    }

    // ---- 单 run 归因 ----

    private static RunDiagnostics diagnoseRun(PaperPortfolioSummary.RunRef ref) {
        String status = ref.status() != null ? ref.status() : "UNKNOWN";
        boolean dataInsufficient = ref.currentEquity() == null || ref.initialEquity() == null;
        boolean highDrawdown = ref.maxDrawdown() != null
                && ref.maxDrawdown().compareTo(HIGH_DRAWDOWN_THRESHOLD) <= 0;
        boolean filledLoss = ref.hasFill() && ref.totalPnl() != null && ref.totalPnl().signum() < 0;
        boolean runningNoResult = "RUNNING".equals(status)
                && ref.orderCount() == 0 && ref.tradeCount() == 0 && dataInsufficient;
        boolean healthy = ref.hasFill() && !ref.riskBlocked() && !dataInsufficient
                && ref.totalPnl() != null && ref.totalPnl().signum() >= 0 && !highDrawdown;

        // 按 Cause 枚举声明顺序（= 优先级）收集命中项，主因取首个、其余为 secondary。
        List<Cause> matched = new ArrayList<>();
        if ("FAILED".equals(status)) {
            matched.add(Cause.FAILED_RUN);
        }
        if (dataInsufficient) {
            matched.add(Cause.DATA_INSUFFICIENT);
        }
        if (ref.riskBlocked()) {
            matched.add(Cause.RISK_BLOCKED);
        }
        if (ref.orderNoFill()) {
            matched.add(Cause.ORDER_NO_FILL);
        }
        if (ref.noOrder()) {
            matched.add(Cause.NO_ORDER);
        }
        if (filledLoss) {
            matched.add(Cause.FILLED_LOSS);
        }
        if (highDrawdown) {
            matched.add(Cause.HIGH_DRAWDOWN);
        }
        if (runningNoResult) {
            matched.add(Cause.RUNNING_NO_RESULT);
        }
        if (healthy) {
            matched.add(Cause.HEALTHY);
        }

        Cause primary = matched.isEmpty() ? Cause.UNKNOWN : matched.get(0);
        List<Cause> secondary = matched.size() <= 1 ? List.of() : List.copyOf(matched.subList(1, matched.size()));

        return new RunDiagnostics(
                ref.paperRunId(), ref.strategyVersionId(), ref.publishId(), status,
                ref.orderCount(), ref.tradeCount(),
                ref.currentEquity(), ref.initialEquity(), ref.totalPnl(), ref.totalReturn(), ref.maxDrawdown(),
                ref.riskBlocked(), ref.openAlertCount(),
                primary, secondary,
                severityOf(primary, status), confidenceOf(primary, status),
                explanationOf(primary, status), suggestedActionOf(primary), ref.lastActivityAt());
    }

    // ---- 总览（按事实独立计数，桶可重叠）----

    private static PaperExecutionDiagnostics.Overview buildOverview(List<PaperPortfolioSummary.RunRef> refs) {
        int noOrder = 0;
        int orderNoFill = 0;
        int filled = 0;
        int filledLoss = 0;
        int riskBlocked = 0;
        int dataInsufficient = 0;
        int highDrawdown = 0;
        int failed = 0;
        int running = 0;

        for (PaperPortfolioSummary.RunRef ref : refs) {
            if (ref.noOrder()) {
                noOrder++;
            }
            if (ref.orderNoFill()) {
                orderNoFill++;
            }
            if (ref.hasFill()) {
                filled++;
                if (ref.totalPnl() != null && ref.totalPnl().signum() < 0) {
                    filledLoss++;
                }
            }
            if (ref.riskBlocked()) {
                riskBlocked++;
            }
            if (ref.currentEquity() == null || ref.initialEquity() == null) {
                dataInsufficient++;
            }
            if (ref.maxDrawdown() != null && ref.maxDrawdown().compareTo(HIGH_DRAWDOWN_THRESHOLD) <= 0) {
                highDrawdown++;
            }
            if ("FAILED".equals(ref.status())) {
                failed++;
            }
            if ("RUNNING".equals(ref.status())) {
                running++;
            }
        }

        return new PaperExecutionDiagnostics.Overview(
                refs.size(), noOrder, orderNoFill, filled, filledLoss,
                riskBlocked, dataInsufficient, highDrawdown, failed, running);
    }

    // ---- 主因分布 ----

    private static List<PaperExecutionDiagnostics.CauseDistribution> buildCauseDistribution(
            List<RunDiagnostics> runDiagnostics
    ) {
        Map<Cause, Integer> counts = new EnumMap<>(Cause.class);
        for (RunDiagnostics d : runDiagnostics) {
            counts.merge(d.primaryCause(), 1, Integer::sum);
        }
        List<PaperExecutionDiagnostics.CauseDistribution> distribution = new ArrayList<>(counts.size());
        // 按 Cause 枚举声明顺序（优先级）输出，最紧急主因在前，便于前端稳定渲染。
        for (Cause cause : Cause.values()) {
            Integer count = counts.get(cause);
            if (count != null && count > 0) {
                distribution.add(new PaperExecutionDiagnostics.CauseDistribution(
                        cause, count, representativeSeverity(cause), representativeConfidence(cause),
                        descriptionOf(cause)));
            }
        }
        return List.copyOf(distribution);
    }

    // ---- 分组聚合（strategy / publish）----

    private static List<GroupDiagnostics> buildGroups(
            List<RunDiagnostics> runDiagnostics,
            Function<RunDiagnostics, String> keyFn,
            String fallbackKey
    ) {
        Map<String, List<RunDiagnostics>> grouped = new LinkedHashMap<>();
        for (RunDiagnostics d : runDiagnostics) {
            String raw = keyFn.apply(d);
            String key = raw != null && !raw.isBlank() ? raw : fallbackKey;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        List<GroupDiagnostics> groups = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<RunDiagnostics>> entry : grouped.entrySet()) {
            List<RunDiagnostics> members = entry.getValue();
            int noOrder = 0;
            int orderNoFill = 0;
            int filledLoss = 0;
            int riskBlocked = 0;
            int dataInsufficient = 0;
            int highDrawdown = 0;
            Map<Cause, Integer> primaryCounts = new EnumMap<>(Cause.class);

            for (RunDiagnostics d : members) {
                // 组内按事实独立计数（与单 run 的 cause 命中口径一致，桶可重叠）。
                if (d.tradeCount() == 0 && d.orderCount() == 0) {
                    noOrder++;
                }
                if (d.orderCount() > 0 && d.tradeCount() == 0) {
                    orderNoFill++;
                }
                if (d.tradeCount() > 0 && d.totalPnl() != null && d.totalPnl().signum() < 0) {
                    filledLoss++;
                }
                if (d.riskBlocked()) {
                    riskBlocked++;
                }
                if (d.currentEquity() == null || d.initialEquity() == null) {
                    dataInsufficient++;
                }
                if (d.maxDrawdown() != null && d.maxDrawdown().compareTo(HIGH_DRAWDOWN_THRESHOLD) <= 0) {
                    highDrawdown++;
                }
                primaryCounts.merge(d.primaryCause(), 1, Integer::sum);
            }

            // 组主因取组内最紧急（优先级最高 = 枚举序最小）的主因。
            Cause groupPrimary = members.stream()
                    .map(RunDiagnostics::primaryCause)
                    .min(Comparator.comparingInt(Enum::ordinal))
                    .orElse(Cause.UNKNOWN);
            // topCauses：组内主因按出现频次降序、再按优先级（枚举序）升序，截断上限。
            List<Cause> topCauses = primaryCounts.entrySet().stream()
                    .sorted(Comparator
                            .comparingInt((Map.Entry<Cause, Integer> e) -> e.getValue()).reversed()
                            .thenComparingInt(e -> e.getKey().ordinal()))
                    .limit(MAX_TOP_CAUSES)
                    .map(Map.Entry::getKey)
                    .toList();

            groups.add(new GroupDiagnostics(
                    entry.getKey(), members.size(), groupPrimary, List.copyOf(topCauses),
                    noOrder, orderNoFill, filledLoss, riskBlocked, dataInsufficient, highDrawdown,
                    representativeSeverity(groupPrimary), representativeConfidence(groupPrimary)));
        }

        // 组排序：先按组主因优先级（最紧急在前），再按 run 数降序，保证问题集中的组靠前、稳定可读。
        groups.sort(Comparator
                .comparingInt((GroupDiagnostics g) -> g.primaryCause().ordinal())
                .thenComparing(Comparator.comparingInt(GroupDiagnostics::runCount).reversed()));
        return List.copyOf(groups);
    }

    // ---- 严重度 / 可信度 / 文案（Paper-only，不构成投资建议）----

    private static Severity severityOf(Cause cause, String status) {
        return switch (cause) {
            case FAILED_RUN, RISK_BLOCKED, HIGH_DRAWDOWN -> Severity.CRITICAL;
            case DATA_INSUFFICIENT, ORDER_NO_FILL, FILLED_LOSS -> Severity.WARNING;
            // 无订单：CREATED（尚未启动）为 INFO，其余（已运行仍无订单）为 WARNING。
            case NO_ORDER -> "CREATED".equals(status) ? Severity.INFO : Severity.WARNING;
            case RUNNING_NO_RESULT, HEALTHY, UNKNOWN -> Severity.INFO;
        };
    }

    private static Confidence confidenceOf(Cause cause, String status) {
        return switch (cause) {
            case FAILED_RUN, DATA_INSUFFICIENT, RISK_BLOCKED, ORDER_NO_FILL, FILLED_LOSS, HIGH_DRAWDOWN, HEALTHY ->
                    Confidence.HIGH;
            // 无订单：CREATED 可能只是尚未启动（MEDIUM）；已运行仍无订单为明确事实（HIGH）。
            case NO_ORDER -> "CREATED".equals(status) ? Confidence.MEDIUM : Confidence.HIGH;
            case RUNNING_NO_RESULT -> Confidence.MEDIUM;
            case UNKNOWN -> Confidence.LOW;
        };
    }

    /** 分布 / 分组层使用的代表性严重度（不含单 run 状态细分；NO_ORDER 取 WARNING）。 */
    private static Severity representativeSeverity(Cause cause) {
        return severityOf(cause, "");
    }

    /** 分布 / 分组层使用的代表性可信度（不含单 run 状态细分；NO_ORDER 取 HIGH）。 */
    private static Confidence representativeConfidence(Cause cause) {
        return confidenceOf(cause, "");
    }

    private static String explanationOf(Cause cause, String status) {
        return switch (cause) {
            case FAILED_RUN -> "Run 处于 FAILED 终态，执行链路异常终止。";
            case DATA_INSUFFICIENT -> "缺少可用权益/初始资金等事实，无法计算收益率与盈亏。";
            case RISK_BLOCKED -> "风控规则阻断执行或判定为高风险结果。";
            case ORDER_NO_FILL -> "已产生订单但未形成成交，可能是撮合条件、价格条件、流动性模拟或风控后置限制。";
            case NO_ORDER -> "CREATED".equals(status)
                    ? "Run 尚未启动或尚未产生执行事实（无订单、无成交）。"
                    : "已运行但未产生任何订单，可能是策略信号未触发或数据/信号条件不足。";
            case FILLED_LOSS -> "已成交但当前 PnL 为负，需结合回撤、持仓与成交价格继续分析。";
            case HIGH_DRAWDOWN -> "单 run 最大回撤已达到或超过 10%（首版规则阈值）。";
            case RUNNING_NO_RESULT -> "Run 仍在运行但暂无足够订单/成交/权益事实，结果尚未形成。";
            case HEALTHY -> "已成交、当前非亏损、无高回撤、无风控拦截、数据充分。";
            case UNKNOWN -> "现有事实不足以归因，需要进一步检查。";
        };
    }

    /** 排查向建议（仅指向「检查/复盘哪些 Paper 事实」，不含买卖方向，不构成投资建议）。 */
    private static String suggestedActionOf(Cause cause) {
        return switch (cause) {
            case FAILED_RUN -> "检查异常停机/告警/恢复事件，定位失败步骤后再决定是否重试。";
            case DATA_INSUFFICIENT -> "检查 equity 快照、日报与数据采集是否正常生成。";
            case RISK_BLOCKED -> "检查最近一次风控检查结果与触发规则，确认是否为预期风控行为。";
            case ORDER_NO_FILL -> "复盘下单价格/类型与撮合（模拟成交）条件，确认订单是否长期挂单未成交。";
            case NO_ORDER -> "确认 run 是否已启动，并复盘策略触发条件、参数与输入数据覆盖范围。";
            case FILLED_LOSS -> "结合回撤曲线与成交明细复盘亏损来源（Paper 模拟结果，不代表真实交易表现）。";
            case HIGH_DRAWDOWN -> "复盘仓位与回撤来源（Paper 模拟口径）。";
            case RUNNING_NO_RESULT -> "稍后再观察，或确认执行器心跳与数据是否正常。";
            case HEALTHY -> "无需处理；继续按 Paper 计划观察（不代表真实交易表现）。";
            case UNKNOWN -> "检查该 run 的订单/成交/风控/权益事实完整性。";
        };
    }

    private static String descriptionOf(Cause cause) {
        return switch (cause) {
            case FAILED_RUN -> "异常终态 run：执行链路失败。";
            case DATA_INSUFFICIENT -> "数据不足 run：缺权益/初始资金，无法计算收益率。";
            case RISK_BLOCKED -> "风控拦截 run：执行被风控阻断或判为高风险。";
            case ORDER_NO_FILL -> "有订单无成交 run：已下单但未撮合成交。";
            case NO_ORDER -> "无订单 run：未产生任何订单与成交。";
            case FILLED_LOSS -> "成交亏损 run：已成交但当前 PnL 为负。";
            case HIGH_DRAWDOWN -> "高回撤 run：单 run 最大回撤达到/超过 10%。";
            case RUNNING_NO_RESULT -> "运行未出结果 run：仍在运行但事实不足。";
            case HEALTHY -> "健康 run：已成交、非亏损、无高回撤、无风控拦截。";
            case UNKNOWN -> "未归因 run：事实不足以判断。";
        };
    }
}
