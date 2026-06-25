package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PaperPortfolioAssembler —— 纯函数式组合看板聚合器（GateJ 后产品化 Loop-13）。
 *
 * 设计要点：
 * 1) 无任何 IO / 仓储 / 外呼依赖，全部在内存中派生，便于单测覆盖盈利 / 亏损高回撤 / 数据不足等分支。
 * 2) 单 run 派生（currentEquity / initialEquity / maxDrawdown / 收益率）与前端 Loop-12 同口径；
 *    initialEquity 缺失 baseline 时不外推，相关收益率统一为 null。
 * 3) 组合总览的总资产 / 总 PnL / 总收益率仅基于「同时具备当前权益与初始资金」的可比 run，
 *    避免对不同口径 run 求和后相减导致误导。
 * 4) 风控拦截判定复用 {@link PaperRunSummaryAssembler#isRiskBlocked} 同口径。
 */
public final class PaperPortfolioAssembler {

    /** 收益率 / 回撤比例值统一保留 6 位小数（HALF_UP），展示层再 ×100。 */
    private static final int RATIO_SCALE = 6;

    /** 排行 / 数据质量清单截断上限，避免响应体无界膨胀。 */
    static final int MAX_LIST_SIZE = 50;

    private PaperPortfolioAssembler() {
    }

    /**
     * 单个 Paper run 的聚合输入；全部为已查询出的只读事实，由 {@code PaperPortfolioService} 装配。
     * openAlertCount / tradeCount 直接传计数（看板只需计数，避免无谓传递告警与成交明细，
     * 也便于仓储以 GROUP BY 批量聚合）；hasPublishSource / hasBacktestSource 由 service 依据 publish 记录预先判定。
     */
    public record RunInput(
            PaperTradingRun run,
            List<EquityCurveSnapshot> equityCurve,
            List<PaperRunDailyReport> dailyReports,
            List<PaperRiskCheckResult> riskResults,
            int openAlertCount,
            int tradeCount,
            boolean hasPublishSource,
            boolean hasBacktestSource
    ) {}

    /** 单 run 派生后的中间结果，承载组合层聚合所需的全部口径。 */
    private record RunMetrics(
            PaperPortfolioSummary.RunRef ref,
            boolean hasEquity,
            boolean dataInsufficient,
            boolean noTrade
    ) {}

    public static PaperPortfolioSummary assemble(List<RunInput> inputs) {
        List<RunInput> safeInputs = inputs != null ? inputs : List.of();
        List<RunMetrics> metrics = new ArrayList<>(safeInputs.size());
        for (RunInput input : safeInputs) {
            metrics.add(deriveRun(input));
        }

        var overview = buildOverview(metrics);
        var strategyGroups = buildGroups(metrics, ref -> normalizeKey(ref.strategyVersionId(), "(未绑定策略版本)"));
        var publishGroups = buildGroups(metrics, ref -> normalizeKey(ref.publishId(), "(未知发布)"));
        var highlights = buildHighlights(metrics);
        var dataQuality = buildDataQuality(safeInputs, metrics);

        return new PaperPortfolioSummary(overview, strategyGroups, publishGroups, highlights, dataQuality);
    }

    // ---- 单 run 派生 ----

    private static RunMetrics deriveRun(RunInput input) {
        PaperTradingRun run = input.run();

        // 仅保留有合法时间与数值权益的快照，按时间升序，作为资产与回撤的唯一派生源（与前端 Loop-12 同口径）。
        List<EquityPoint> points = new ArrayList<>();
        for (EquityCurveSnapshot snapshot : nonNull(input.equityCurve())) {
            if (snapshot.snapshotTime() != null && snapshot.totalEquity() != null) {
                points.add(new EquityPoint(snapshot.snapshotTime(), snapshot.totalEquity(),
                        snapshot.realizedPnl(), snapshot.unrealizedPnl()));
            }
        }
        points.sort(Comparator.comparing(EquityPoint::time));

        PaperRunDailyReport latestReport = nonNull(input.dailyReports()).stream()
                .max(Comparator.comparing(PaperRunDailyReport::reportDate))
                .orElse(null);

        EquityPoint latest = points.isEmpty() ? null : points.get(points.size() - 1);
        BigDecimal currentEquity = latest != null
                ? latest.equity()
                : (latestReport != null ? latestReport.totalEquity() : null);
        BigDecimal realizedPnl = latest != null ? latest.realizedPnl() : null;
        BigDecimal unrealizedPnl = latest != null ? latest.unrealizedPnl() : null;

        // 初始资金：优先用「当前权益 - 已实现 - 未实现」回推；否则退化到最早快照权益；均不可得则数据不足。
        BigDecimal initialEquity = null;
        if (currentEquity != null && realizedPnl != null && unrealizedPnl != null) {
            BigDecimal inferred = currentEquity.subtract(realizedPnl).subtract(unrealizedPnl);
            if (inferred.signum() > 0) {
                initialEquity = inferred;
            }
        }
        if (initialEquity == null && !points.isEmpty()) {
            BigDecimal earliest = points.get(0).equity();
            if (earliest != null && earliest.signum() > 0) {
                initialEquity = earliest;
            }
        }

        BigDecimal maxDrawdown = computeMaxDrawdown(points);
        if (maxDrawdown == null && latestReport != null && latestReport.maxDrawdown() != null) {
            // 无快照时退化到日报最大回撤，统一规整为负比例（日报存放为正向幅度）。
            maxDrawdown = latestReport.maxDrawdown().abs().negate();
        }

        BigDecimal totalPnl;
        if (currentEquity != null && initialEquity != null) {
            totalPnl = currentEquity.subtract(initialEquity);
        } else {
            totalPnl = sum(realizedPnl, unrealizedPnl);
        }
        BigDecimal totalReturn = ratio(
                currentEquity != null && initialEquity != null ? currentEquity.subtract(initialEquity) : null,
                initialEquity);

        boolean riskBlocked = PaperRunSummaryAssembler.isRiskBlocked(latestRisk(input.riskResults()));
        int openAlertCount = Math.max(0, input.openAlertCount());

        var ref = new PaperPortfolioSummary.RunRef(
                run.paperRunId(),
                run.status() != null ? run.status().name() : "UNKNOWN",
                run.symbol(),
                run.strategyVersionId(),
                run.publishId(),
                currentEquity,
                initialEquity,
                totalPnl,
                totalReturn,
                maxDrawdown,
                riskBlocked,
                openAlertCount,
                input.tradeCount(),
                lastActivityAt(run));

        boolean hasEquity = !points.isEmpty();
        boolean dataInsufficient = currentEquity == null || initialEquity == null;
        boolean noTrade = input.tradeCount() == 0;
        return new RunMetrics(ref, hasEquity, dataInsufficient, noTrade);
    }

    private static BigDecimal computeMaxDrawdown(List<EquityPoint> points) {
        if (points.isEmpty()) {
            return null;
        }
        BigDecimal peak = null;
        BigDecimal worst = BigDecimal.ZERO;
        for (EquityPoint point : points) {
            BigDecimal equity = point.equity();
            if (peak == null || equity.compareTo(peak) > 0) {
                peak = equity;
            }
            if (peak.signum() > 0) {
                BigDecimal drawdown = ratio(equity.subtract(peak), peak);
                if (drawdown != null && drawdown.compareTo(worst) < 0) {
                    worst = drawdown;
                }
            }
        }
        return worst;
    }

    // ---- 组合总览 ----

    private static PaperPortfolioSummary.Overview buildOverview(List<RunMetrics> metrics) {
        int running = 0;
        int stopped = 0;
        int failed = 0;
        int cancelled = 0;
        int created = 0;
        int openAlerts = 0;
        int riskBlocked = 0;
        int noTrade = 0;
        int dataInsufficient = 0;
        int returnEligible = 0;
        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal totalInitial = BigDecimal.ZERO;
        BigDecimal worstDrawdown = null;

        for (RunMetrics m : metrics) {
            PaperPortfolioSummary.RunRef ref = m.ref();
            switch (ref.status()) {
                case "RUNNING" -> running++;
                case "STOPPED" -> stopped++;
                case "FAILED" -> failed++;
                case "CANCELLED" -> cancelled++;
                case "CREATED" -> created++;
                default -> { /* 未知状态不计入分桶，仅计入 totalRuns。 */ }
            }
            openAlerts += ref.openAlertCount();
            if (ref.riskBlocked()) {
                riskBlocked++;
            }
            if (m.noTrade()) {
                noTrade++;
            }
            if (m.dataInsufficient()) {
                dataInsufficient++;
            }
            // 仅可比 run（同时具备当前权益与初始资金）计入组合资产与收益，避免口径错配。
            if (ref.currentEquity() != null && ref.initialEquity() != null) {
                returnEligible++;
                totalCurrent = totalCurrent.add(ref.currentEquity());
                totalInitial = totalInitial.add(ref.initialEquity());
            }
            if (ref.maxDrawdown() != null && (worstDrawdown == null || ref.maxDrawdown().compareTo(worstDrawdown) < 0)) {
                worstDrawdown = ref.maxDrawdown();
            }
        }

        BigDecimal totalPnl = returnEligible > 0 ? totalCurrent.subtract(totalInitial) : null;
        BigDecimal totalReturn = returnEligible > 0 ? ratio(totalCurrent.subtract(totalInitial), totalInitial) : null;

        return new PaperPortfolioSummary.Overview(
                metrics.size(), running, stopped, failed, cancelled, created,
                returnEligible > 0 ? totalInitial : null,
                returnEligible > 0 ? totalCurrent : null,
                totalPnl, totalReturn, returnEligible,
                worstDrawdown, openAlerts, riskBlocked, noTrade, dataInsufficient);
    }

    // ---- 分组排行 ----

    private static List<PaperPortfolioSummary.Group> buildGroups(
            List<RunMetrics> metrics,
            java.util.function.Function<PaperPortfolioSummary.RunRef, String> keyFn
    ) {
        // 用 LinkedHashMap 保持首次出现顺序，便于排序前的稳定性。
        Map<String, List<RunMetrics>> grouped = new LinkedHashMap<>();
        for (RunMetrics m : metrics) {
            grouped.computeIfAbsent(keyFn.apply(m.ref()), k -> new ArrayList<>()).add(m);
        }

        List<PaperPortfolioSummary.Group> groups = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<RunMetrics>> entry : grouped.entrySet()) {
            List<RunMetrics> members = entry.getValue();
            BigDecimal current = BigDecimal.ZERO;
            BigDecimal initial = BigDecimal.ZERO;
            int eligible = 0;
            int riskBlocked = 0;
            int openAlerts = 0;
            BigDecimal worstDrawdown = null;
            Instant lastRunTime = null;

            for (RunMetrics m : members) {
                PaperPortfolioSummary.RunRef ref = m.ref();
                if (ref.currentEquity() != null && ref.initialEquity() != null) {
                    eligible++;
                    current = current.add(ref.currentEquity());
                    initial = initial.add(ref.initialEquity());
                }
                if (ref.riskBlocked()) {
                    riskBlocked++;
                }
                openAlerts += ref.openAlertCount();
                if (ref.maxDrawdown() != null
                        && (worstDrawdown == null || ref.maxDrawdown().compareTo(worstDrawdown) < 0)) {
                    worstDrawdown = ref.maxDrawdown();
                }
                if (ref.lastActivityAt() != null
                        && (lastRunTime == null || ref.lastActivityAt().isAfter(lastRunTime))) {
                    lastRunTime = ref.lastActivityAt();
                }
            }

            groups.add(new PaperPortfolioSummary.Group(
                    entry.getKey(),
                    members.size(),
                    eligible > 0 ? current : null,
                    eligible > 0 ? current.subtract(initial) : null,
                    eligible > 0 ? ratio(current.subtract(initial), initial) : null,
                    worstDrawdown,
                    riskBlocked,
                    openAlerts,
                    lastRunTime));
        }

        // 排序：先按总 PnL 降序（null 收益排末尾），再按 run 数降序，保证盈利组靠前、稳定可读。
        groups.sort(Comparator
                .comparing((PaperPortfolioSummary.Group g) -> g.totalPnl() == null ? BigDecimal.valueOf(Long.MIN_VALUE) : g.totalPnl())
                .reversed()
                .thenComparing(Comparator.comparingInt(PaperPortfolioSummary.Group::runCount).reversed()));
        return groups;
    }

    // ---- Run 排行 ----

    private static PaperPortfolioSummary.Highlights buildHighlights(List<RunMetrics> metrics) {
        PaperPortfolioSummary.RunRef topWinner = null;
        PaperPortfolioSummary.RunRef worstDrawdown = null;
        PaperPortfolioSummary.RunRef highestRisk = null;
        PaperPortfolioSummary.RunRef mostRecent = null;
        List<PaperPortfolioSummary.RunRef> noTradeRuns = new ArrayList<>();
        List<PaperPortfolioSummary.RunRef> riskBlockedRuns = new ArrayList<>();

        for (RunMetrics m : metrics) {
            PaperPortfolioSummary.RunRef ref = m.ref();

            if (ref.totalPnl() != null && (topWinner == null || ref.totalPnl().compareTo(topWinner.totalPnl()) > 0)) {
                topWinner = ref;
            }
            if (ref.maxDrawdown() != null
                    && (worstDrawdown == null || ref.maxDrawdown().compareTo(worstDrawdown.maxDrawdown()) < 0)) {
                worstDrawdown = ref;
            }
            // 风险最高：优先风控拦截 run；其次未处理告警最多；同级别按更近活动时间。
            if (isHigherRisk(ref, highestRisk)) {
                highestRisk = ref;
            }
            if (ref.lastActivityAt() != null
                    && (mostRecent == null || mostRecent.lastActivityAt() == null
                    || ref.lastActivityAt().isAfter(mostRecent.lastActivityAt()))) {
                mostRecent = ref;
            }
            if (m.noTrade()) {
                noTradeRuns.add(ref);
            }
            if (ref.riskBlocked()) {
                riskBlockedRuns.add(ref);
            }
        }

        return new PaperPortfolioSummary.Highlights(
                topWinner, worstDrawdown, highestRisk, mostRecent,
                cap(noTradeRuns), cap(riskBlockedRuns));
    }

    private static boolean isHigherRisk(PaperPortfolioSummary.RunRef candidate, PaperPortfolioSummary.RunRef current) {
        // 只把「风控拦截」或「存在未处理告警」的 run 视为风险候选。
        boolean candidateRisky = candidate.riskBlocked() || candidate.openAlertCount() > 0;
        if (!candidateRisky) {
            return false;
        }
        if (current == null) {
            return true;
        }
        if (candidate.riskBlocked() != current.riskBlocked()) {
            return candidate.riskBlocked();
        }
        if (candidate.openAlertCount() != current.openAlertCount()) {
            return candidate.openAlertCount() > current.openAlertCount();
        }
        if (candidate.lastActivityAt() == null) {
            return false;
        }
        return current.lastActivityAt() == null || candidate.lastActivityAt().isAfter(current.lastActivityAt());
    }

    // ---- 数据质量 ----

    private static PaperPortfolioSummary.DataQuality buildDataQuality(
            List<RunInput> inputs, List<RunMetrics> metrics
    ) {
        List<PaperPortfolioSummary.RunRef> missingEquity = new ArrayList<>();
        List<PaperPortfolioSummary.RunRef> dataInsufficient = new ArrayList<>();
        List<PaperPortfolioSummary.RunRef> missingBacktest = new ArrayList<>();
        List<PaperPortfolioSummary.RunRef> missingPublish = new ArrayList<>();

        for (int i = 0; i < metrics.size(); i++) {
            RunMetrics m = metrics.get(i);
            RunInput input = inputs.get(i);
            if (!m.hasEquity()) {
                missingEquity.add(m.ref());
            }
            if (m.dataInsufficient()) {
                dataInsufficient.add(m.ref());
            }
            if (!input.hasBacktestSource()) {
                missingBacktest.add(m.ref());
            }
            if (!input.hasPublishSource()) {
                missingPublish.add(m.ref());
            }
        }

        return new PaperPortfolioSummary.DataQuality(
                cap(missingEquity), cap(dataInsufficient), cap(missingBacktest), cap(missingPublish));
    }

    // ---- helpers ----

    private record EquityPoint(Instant time, BigDecimal equity, BigDecimal realizedPnl, BigDecimal unrealizedPnl) {}

    private static PaperRiskCheckResult latestRisk(List<PaperRiskCheckResult> riskResults) {
        return nonNull(riskResults).stream()
                .filter(r -> r.createdAt() != null)
                .max(Comparator.comparing(PaperRiskCheckResult::createdAt))
                .orElse(null);
    }

    private static Instant lastActivityAt(PaperTradingRun run) {
        Instant latest = null;
        for (Instant candidate : new Instant[]{run.stoppedAt(), run.startedAt(), run.updatedAt(), run.createdAt()}) {
            if (candidate != null && (latest == null || candidate.isAfter(latest))) {
                latest = candidate;
            }
        }
        return latest;
    }

    private static String normalizeKey(String key, String fallback) {
        return key != null && !key.isBlank() ? key : fallback;
    }

    private static <T> List<T> cap(List<T> list) {
        return list.size() <= MAX_LIST_SIZE ? List.copyOf(list) : List.copyOf(list.subList(0, MAX_LIST_SIZE));
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list != null ? list : List.of();
    }

    private static BigDecimal sum(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return null;
        }
        BigDecimal a = left != null ? left : BigDecimal.ZERO;
        BigDecimal b = right != null ? right : BigDecimal.ZERO;
        return a.add(b);
    }

    /** numerator/denominator 的比例值；denominator 缺失或非正时返回 null（数据不足，不外推）。 */
    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) {
            return null;
        }
        return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
