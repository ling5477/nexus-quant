package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.BacktestDeviation;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.DeviationLevel;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.EvaluationConfidence;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.PublishEvaluation;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.RatingLabel;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.StrategyEvaluation;
import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * PaperStrategyEvaluationAssembler —— 纯函数式 Paper 策略评估聚合器（GateK Batch K3）。
 *
 * 设计要点：
 * 1) 无任何 IO / 仓储 / 外呼依赖；输入为 {@link PaperPortfolioSummary.RunRef}（组合看板同口径单 run 事实）
 *    与 publishId -&gt; {@link BacktestEvaluationView} 的 Backtest 对照映射（由 service 预先 join）。
 * 2) 评分为 0~100 的 Paper 内部启发式分（权重见 {@link #compositeScore}），<b>不是真实投资评级、不构成投资建议</b>。
 * 3) Backtest 数据缺失时偏差与 backtestDeviationScore 为 null，并在 warnings / confidence 中降级，不伪造。
 * 4) 空输入返回稳定空结构。
 */
public final class PaperStrategyEvaluationAssembler {

    private static final int RATIO_SCALE = 6;

    /** 排行清单截断上限。 */
    static final int MAX_RANKING_SIZE = 10;

    /** 高回撤风险阈值（首版规则阈值）：单 run / 组最差回撤 ≤ -20% 视为高风险信号。 */
    private static final BigDecimal HIGH_RISK_DRAWDOWN = new BigDecimal("-0.20");

    /** Backtest 偏差分级阈值（首版规则阈值，比例绝对值）。 */
    private static final BigDecimal DEVIATION_LOW = new BigDecimal("0.05");
    private static final BigDecimal DEVIATION_MEDIUM = new BigDecimal("0.15");

    private static final String STRATEGY_FALLBACK_KEY = "(未绑定策略版本)";
    private static final String PUBLISH_FALLBACK_KEY = "(未知发布)";

    private PaperStrategyEvaluationAssembler() {
    }

    public static PaperStrategyEvaluation assemble(
            List<PaperPortfolioSummary.RunRef> runRefs,
            Map<String, BacktestEvaluationView> backtestViewByPublishId
    ) {
        List<PaperPortfolioSummary.RunRef> safeRefs = runRefs != null ? runRefs : List.of();
        Map<String, BacktestEvaluationView> btByPublish = backtestViewByPublishId != null
                ? backtestViewByPublishId : Map.of();

        // 分组：strategyVersionId（含未绑定回退）与 publishId（含未知回退）。
        Map<String, List<PaperPortfolioSummary.RunRef>> byStrategy = groupBy(
                safeRefs, ref -> normalizeKey(ref.strategyVersionId(), STRATEGY_FALLBACK_KEY));
        Map<String, List<PaperPortfolioSummary.RunRef>> byPublish = groupBy(
                safeRefs, ref -> normalizeKey(ref.publishId(), PUBLISH_FALLBACK_KEY));

        List<StrategyEvaluation> strategyEvaluations = new ArrayList<>();
        for (Map.Entry<String, List<PaperPortfolioSummary.RunRef>> entry : byStrategy.entrySet()) {
            strategyEvaluations.add(buildStrategyEvaluation(entry.getKey(), entry.getValue(), btByPublish));
        }
        // 复合分降序，便于排行与展示稳定。
        strategyEvaluations.sort(Comparator.comparingInt(StrategyEvaluation::compositeScore).reversed());

        List<PublishEvaluation> publishEvaluations = new ArrayList<>();
        for (Map.Entry<String, List<PaperPortfolioSummary.RunRef>> entry : byPublish.entrySet()) {
            publishEvaluations.add(buildPublishEvaluation(entry.getKey(), entry.getValue(), btByPublish));
        }
        publishEvaluations.sort(Comparator.comparingInt(PublishEvaluation::compositeScore).reversed());

        var overview = buildOverview(strategyEvaluations);
        var rankings = buildRankings(strategyEvaluations);
        return new PaperStrategyEvaluation(overview, List.copyOf(strategyEvaluations), List.copyOf(publishEvaluations), rankings);
    }

    // ---- 分组聚合事实 ----

    /** 单分组聚合的中间事实（与组合看板 / 诊断口径一致）。 */
    private record GroupAgg(
            int runCount,
            int comparableRunCount,
            BigDecimal currentEquity,
            BigDecimal initialEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal maxDrawdown,
            int winRunCount,
            int lossRunCount,
            BigDecimal winRate,
            BigDecimal averageReturn,
            BigDecimal averageDrawdown,
            int riskBlockedCount,
            int dataInsufficientCount,
            int noOrderCount,
            int orderNoFillCount,
            int filledRunCount,
            int filledLossCount,
            int failedRunCount,
            int publishCount,
            Instant latestRunTime
    ) {}

    private static GroupAgg aggregate(List<PaperPortfolioSummary.RunRef> members) {
        int comparable = 0;
        int win = 0;
        int loss = 0;
        int riskBlocked = 0;
        int dataInsufficient = 0;
        int noOrder = 0;
        int orderNoFill = 0;
        int filled = 0;
        int filledLoss = 0;
        int failed = 0;
        BigDecimal sumCurrent = BigDecimal.ZERO;
        BigDecimal sumInitial = BigDecimal.ZERO;
        BigDecimal sumReturn = BigDecimal.ZERO;
        int returnSampleCount = 0;
        BigDecimal sumDrawdown = BigDecimal.ZERO;
        int drawdownSampleCount = 0;
        BigDecimal worstDrawdown = null;
        Instant latest = null;
        java.util.Set<String> publishes = new java.util.HashSet<>();

        for (PaperPortfolioSummary.RunRef ref : members) {
            boolean comparableRun = ref.currentEquity() != null && ref.initialEquity() != null;
            if (comparableRun) {
                comparable++;
                sumCurrent = sumCurrent.add(ref.currentEquity());
                sumInitial = sumInitial.add(ref.initialEquity());
            } else {
                dataInsufficient++;
            }
            if (ref.totalReturn() != null) {
                returnSampleCount++;
                sumReturn = sumReturn.add(ref.totalReturn());
                if (ref.totalReturn().signum() > 0) {
                    win++;
                } else if (ref.totalReturn().signum() < 0) {
                    loss++;
                }
            }
            if (ref.maxDrawdown() != null) {
                drawdownSampleCount++;
                sumDrawdown = sumDrawdown.add(ref.maxDrawdown());
                if (worstDrawdown == null || ref.maxDrawdown().compareTo(worstDrawdown) < 0) {
                    worstDrawdown = ref.maxDrawdown();
                }
            }
            if (ref.riskBlocked()) {
                riskBlocked++;
            }
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
            if ("FAILED".equals(ref.status())) {
                failed++;
            }
            if (ref.publishId() != null && !ref.publishId().isBlank()) {
                publishes.add(ref.publishId());
            }
            if (ref.lastActivityAt() != null && (latest == null || ref.lastActivityAt().isAfter(latest))) {
                latest = ref.lastActivityAt();
            }
        }

        BigDecimal totalReturn = comparable > 0 ? ratio(sumCurrent.subtract(sumInitial), sumInitial) : null;
        BigDecimal totalPnl = comparable > 0 ? sumCurrent.subtract(sumInitial) : null;
        BigDecimal averageReturn = returnSampleCount > 0
                ? sumReturn.divide(BigDecimal.valueOf(returnSampleCount), RATIO_SCALE, RoundingMode.HALF_UP) : null;
        BigDecimal averageDrawdown = drawdownSampleCount > 0
                ? sumDrawdown.divide(BigDecimal.valueOf(drawdownSampleCount), RATIO_SCALE, RoundingMode.HALF_UP) : null;
        BigDecimal winRate = returnSampleCount > 0
                ? BigDecimal.valueOf(win).divide(BigDecimal.valueOf(returnSampleCount), RATIO_SCALE, RoundingMode.HALF_UP) : null;

        return new GroupAgg(
                members.size(), comparable,
                comparable > 0 ? sumCurrent : null, comparable > 0 ? sumInitial : null,
                totalPnl, totalReturn, worstDrawdown,
                win, loss, winRate, averageReturn, averageDrawdown,
                riskBlocked, dataInsufficient, noOrder, orderNoFill, filled, filledLoss, failed,
                publishes.size(), latest);
    }

    // ---- 评分（0~100 Paper 内部启发式分，非真实投资评级）----

    /** 样本分：可比 run 越多越可信。 */
    private static int sampleScore(int comparableRunCount) {
        if (comparableRunCount >= 10) {
            return 100;
        }
        if (comparableRunCount >= 5) {
            return 70;
        }
        if (comparableRunCount >= 2) {
            return 40;
        }
        return 10;
    }

    /** 收益分：以平均收益率为中心（+0.2→100，0→50，-0.2→0）；无可比收益率时为 0。 */
    private static int returnScore(BigDecimal averageReturn) {
        if (averageReturn == null) {
            return 0;
        }
        return clamp(50 + averageReturn.multiply(BigDecimal.valueOf(250)).setScale(0, RoundingMode.HALF_UP).intValue());
    }

    /** 风险分：最差回撤、风控拦截、失败终态越多扣分越多。 */
    private static int riskScore(GroupAgg agg) {
        int drawdownPenalty = agg.maxDrawdown() != null
                ? Math.min(agg.maxDrawdown().abs().multiply(BigDecimal.valueOf(200)).setScale(0, RoundingMode.HALF_UP).intValue(), 60)
                : 0;
        int riskBlockedPenalty = Math.min(agg.riskBlockedCount() * 15, 30);
        int failedPenalty = Math.min(agg.failedRunCount() * 20, 30);
        return clamp(100 - drawdownPenalty - riskBlockedPenalty - failedPenalty);
    }

    /** 执行分：有成交占比越高越好；失败终态额外扣分。无 run 时为 0。 */
    private static int executionScore(GroupAgg agg) {
        if (agg.runCount() == 0) {
            return 0;
        }
        int base = agg.filledRunCount() * 100 / agg.runCount();
        return clamp(base - Math.min(agg.failedRunCount() * 10, 30));
    }

    /** Backtest 偏差分：偏差越小越高；无 backtest 数据时为 null（不伪造）。 */
    private static Integer backtestDeviationScore(BacktestDeviation deviation) {
        if (deviation == null || deviation.returnDeviation() == null) {
            return null;
        }
        int penalty = deviation.returnDeviation().abs()
                .multiply(BigDecimal.valueOf(200)).setScale(0, RoundingMode.HALF_UP).intValue();
        return clamp(100 - penalty);
    }

    /**
     * 复合分：returnScore 30% / riskScore 25% / executionScore 20% / sampleScore 15% / backtestDeviationScore 10%；
     * backtest 缺失时按剩余权重重新归一（不让缺失项默认拉低分数）。Paper 内部启发式分，非真实投资评级。
     */
    private static int compositeScore(int returnScore, int riskScore, int executionScore, int sampleScore, Integer btScore) {
        int weighted = returnScore * 30 + riskScore * 25 + executionScore * 20 + sampleScore * 15;
        int weightSum = 90;
        if (btScore != null) {
            weighted += btScore * 10;
            weightSum = 100;
        }
        return clamp(Math.round((float) weighted / weightSum));
    }

    // ---- 评级 / 可信度 / 警告 ----

    private static RatingLabel ratingLabel(GroupAgg agg, int riskScore, int returnScore, int compositeScore) {
        if (agg.comparableRunCount() == 0) {
            return RatingLabel.DATA_INSUFFICIENT;
        }
        if (agg.comparableRunCount() < 2) {
            return RatingLabel.SAMPLE_INSUFFICIENT;
        }
        boolean executionBroken = agg.filledRunCount() == 0
                || (agg.runCount() > 0 && (agg.noOrderCount() + agg.orderNoFillCount()) * 2 > agg.runCount());
        if (executionBroken) {
            return RatingLabel.EXECUTION_PROBLEM;
        }
        boolean highRisk = agg.failedRunCount() > 0
                || (agg.maxDrawdown() != null && agg.maxDrawdown().compareTo(HIGH_RISK_DRAWDOWN) <= 0)
                || riskScore < 40;
        if (highRisk) {
            return RatingLabel.HIGH_RISK;
        }
        if (compositeScore >= 70 && returnScore >= 60 && riskScore >= 50) {
            return RatingLabel.STRONG_PAPER_PERFORMER;
        }
        return RatingLabel.WATCHLIST;
    }

    private static EvaluationConfidence confidence(GroupAgg agg, Integer btScore) {
        if (agg.comparableRunCount() < 2 || agg.filledRunCount() == 0) {
            return EvaluationConfidence.LOW;
        }
        if (agg.comparableRunCount() >= 5 && btScore != null && agg.dataInsufficientCount() == 0) {
            return EvaluationConfidence.HIGH;
        }
        return EvaluationConfidence.MEDIUM;
    }

    private static List<String> warnings(GroupAgg agg, BacktestDeviation deviation) {
        List<String> warnings = new ArrayList<>();
        if (agg.comparableRunCount() == 0) {
            warnings.add("DATA_INSUFFICIENT");
        } else if (agg.comparableRunCount() < 2) {
            warnings.add("SAMPLE_INSUFFICIENT");
        }
        if (agg.filledRunCount() == 0) {
            warnings.add("EXECUTION_NO_FILL");
        }
        if (agg.maxDrawdown() != null && agg.maxDrawdown().compareTo(HIGH_RISK_DRAWDOWN) <= 0) {
            warnings.add("HIGH_DRAWDOWN");
        }
        if (agg.riskBlockedCount() > 0) {
            warnings.add("RISK_BLOCKED_PRESENT");
        }
        if (agg.failedRunCount() > 0) {
            warnings.add("FAILED_RUN_PRESENT");
        }
        if (deviation == null || deviation.deviationLevel() == DeviationLevel.UNAVAILABLE) {
            warnings.add("BACKTEST_DATA_UNAVAILABLE");
        } else if (deviation.deviationLevel() == DeviationLevel.HIGH) {
            warnings.add("BACKTEST_PAPER_DEVIATION_HIGH");
        }
        return warnings;
    }

    /** 主要短板：取得分最低的维度（缺失的 backtest 维度不参与，不误判为短板）。 */
    private static String primaryWeakness(int returnScore, int riskScore, int executionScore, int sampleScore, Integer btScore) {
        String weakest = "RETURN";
        int lowest = returnScore;
        if (riskScore < lowest) {
            lowest = riskScore;
            weakest = "RISK";
        }
        if (executionScore < lowest) {
            lowest = executionScore;
            weakest = "EXECUTION";
        }
        if (sampleScore < lowest) {
            lowest = sampleScore;
            weakest = "SAMPLE";
        }
        if (btScore != null && btScore < lowest) {
            weakest = "BACKTEST_DEVIATION";
        }
        return weakest;
    }

    // ---- Backtest 偏差 ----

    /** 从一组 publishId 的 backtest 视图聚合（求均值）得到 strategy 维度的偏差对照。 */
    private static BacktestDeviation strategyDeviation(
            List<PaperPortfolioSummary.RunRef> members,
            Map<String, BacktestEvaluationView> btByPublish,
            BigDecimal paperReturn, BigDecimal paperDrawdown
    ) {
        BigDecimal sumReturn = BigDecimal.ZERO;
        int returnCount = 0;
        BigDecimal sumDrawdown = BigDecimal.ZERO;
        int drawdownCount = 0;
        java.util.Set<String> seenPublishes = new java.util.HashSet<>();
        for (PaperPortfolioSummary.RunRef ref : members) {
            String publishId = ref.publishId();
            if (publishId == null || !seenPublishes.add(publishId)) {
                continue;
            }
            BacktestEvaluationView view = btByPublish.get(publishId);
            if (view == null) {
                continue;
            }
            if (view.totalReturnRate() != null) {
                sumReturn = sumReturn.add(view.totalReturnRate());
                returnCount++;
            }
            if (view.maxDrawdownRate() != null) {
                // 回撤统一规整为负比例（评估视图存正向幅度），与 paper 单 run 口径一致。
                sumDrawdown = sumDrawdown.add(view.maxDrawdownRate().abs().negate());
                drawdownCount++;
            }
        }
        BigDecimal btReturn = returnCount > 0
                ? sumReturn.divide(BigDecimal.valueOf(returnCount), RATIO_SCALE, RoundingMode.HALF_UP) : null;
        BigDecimal btDrawdown = drawdownCount > 0
                ? sumDrawdown.divide(BigDecimal.valueOf(drawdownCount), RATIO_SCALE, RoundingMode.HALF_UP) : null;
        return buildDeviation(btReturn, btDrawdown, paperReturn, paperDrawdown);
    }

    private static BacktestDeviation publishDeviation(
            BacktestEvaluationView view, BigDecimal paperReturn, BigDecimal paperDrawdown
    ) {
        if (view == null) {
            return buildDeviation(null, null, paperReturn, paperDrawdown);
        }
        BigDecimal btReturn = view.totalReturnRate();
        BigDecimal btDrawdown = view.maxDrawdownRate() != null ? view.maxDrawdownRate().abs().negate() : null;
        return buildDeviation(btReturn, btDrawdown, paperReturn, paperDrawdown);
    }

    private static BacktestDeviation buildDeviation(
            BigDecimal btReturn, BigDecimal btDrawdown, BigDecimal paperReturn, BigDecimal paperDrawdown
    ) {
        if (btReturn == null && btDrawdown == null) {
            return new BacktestDeviation(null, paperReturn, null, null, paperDrawdown, null,
                    DeviationLevel.UNAVAILABLE, "无可比 Backtest 评估数据，无法计算 Paper 与 Backtest 偏差。");
        }
        BigDecimal returnDeviation = btReturn != null && paperReturn != null ? paperReturn.subtract(btReturn) : null;
        BigDecimal drawdownDeviation = btDrawdown != null && paperDrawdown != null ? paperDrawdown.subtract(btDrawdown) : null;
        DeviationLevel level = deviationLevel(returnDeviation);
        String explanation = switch (level) {
            case LOW -> "Paper 与 Backtest 收益偏差较小（首版阈值 < 5%）。";
            case MEDIUM -> "Paper 与 Backtest 收益存在中等偏差（首版阈值 5%~15%），建议结合执行诊断复盘。";
            case HIGH -> "Paper 与 Backtest 收益偏差较大（首版阈值 > 15%），Paper 模拟与回测差异显著，需重点复盘。";
            case UNAVAILABLE -> "Backtest 收益不可比，无法量化收益偏差（仅回撤可对照或均缺失）。";
        };
        return new BacktestDeviation(btReturn, paperReturn, returnDeviation, btDrawdown, paperDrawdown,
                drawdownDeviation, level, explanation);
    }

    private static DeviationLevel deviationLevel(BigDecimal returnDeviation) {
        if (returnDeviation == null) {
            return DeviationLevel.UNAVAILABLE;
        }
        BigDecimal abs = returnDeviation.abs();
        if (abs.compareTo(DEVIATION_LOW) < 0) {
            return DeviationLevel.LOW;
        }
        if (abs.compareTo(DEVIATION_MEDIUM) < 0) {
            return DeviationLevel.MEDIUM;
        }
        return DeviationLevel.HIGH;
    }

    // ---- 组装 strategy / publish 评估 ----

    private static StrategyEvaluation buildStrategyEvaluation(
            String key, List<PaperPortfolioSummary.RunRef> members, Map<String, BacktestEvaluationView> btByPublish
    ) {
        GroupAgg agg = aggregate(members);
        BacktestDeviation deviation = strategyDeviation(members, btByPublish, agg.totalReturn(), agg.maxDrawdown());

        int sampleScore = sampleScore(agg.comparableRunCount());
        int returnScore = returnScore(agg.averageReturn());
        int riskScore = riskScore(agg);
        int executionScore = executionScore(agg);
        Integer btScore = backtestDeviationScore(deviation);
        int composite = compositeScore(returnScore, riskScore, executionScore, sampleScore, btScore);
        RatingLabel rating = ratingLabel(agg, riskScore, returnScore, composite);
        EvaluationConfidence confidence = confidence(agg, btScore);

        return new StrategyEvaluation(
                key, agg.runCount(), agg.comparableRunCount(), agg.publishCount(), agg.latestRunTime(),
                agg.currentEquity(), agg.initialEquity(), agg.totalPnl(), agg.totalReturn(), agg.maxDrawdown(),
                agg.winRunCount(), agg.lossRunCount(), agg.winRate(), agg.averageReturn(), agg.averageDrawdown(),
                agg.riskBlockedCount(), agg.dataInsufficientCount(), agg.noOrderCount(), agg.orderNoFillCount(),
                agg.filledRunCount(), agg.filledLossCount(), agg.failedRunCount(),
                sampleScore, riskScore, returnScore, executionScore, btScore, composite,
                rating, confidence, primaryWeakness(returnScore, riskScore, executionScore, sampleScore, btScore),
                List.copyOf(warnings(agg, deviation)), deviation);
    }

    private static PublishEvaluation buildPublishEvaluation(
            String key, List<PaperPortfolioSummary.RunRef> members, Map<String, BacktestEvaluationView> btByPublish
    ) {
        GroupAgg agg = aggregate(members);
        // publish 维度直接用该 publishId 的 backtest 视图；fallback key 无对应视图。
        BacktestEvaluationView view = btByPublish.get(key);
        BacktestDeviation deviation = publishDeviation(view, agg.totalReturn(), agg.maxDrawdown());

        int sampleScore = sampleScore(agg.comparableRunCount());
        int returnScore = returnScore(agg.averageReturn());
        int riskScore = riskScore(agg);
        int executionScore = executionScore(agg);
        Integer btScore = backtestDeviationScore(deviation);
        int composite = compositeScore(returnScore, riskScore, executionScore, sampleScore, btScore);
        RatingLabel rating = ratingLabel(agg, riskScore, returnScore, composite);
        EvaluationConfidence confidence = confidence(agg, btScore);

        // 取组内第一个非空 strategyVersionId 作为 publish 归属（同一 publish 通常对应单一策略版本）。
        String strategyVersionId = members.stream()
                .map(PaperPortfolioSummary.RunRef::strategyVersionId)
                .filter(v -> v != null && !v.isBlank())
                .findFirst().orElse(null);

        return new PublishEvaluation(
                key, strategyVersionId, agg.runCount(), agg.comparableRunCount(), agg.latestRunTime(),
                agg.totalPnl(), agg.totalReturn(), agg.maxDrawdown(), agg.winRate(),
                sampleScore, riskScore, returnScore, executionScore, btScore, composite,
                rating, confidence, List.copyOf(warnings(agg, deviation)), deviation);
    }

    // ---- 总览 / 排行 ----

    private static PaperStrategyEvaluation.Overview buildOverview(List<StrategyEvaluation> strategies) {
        int evaluatedRuns = strategies.stream().mapToInt(StrategyEvaluation::runCount).sum();
        int comparableRuns = strategies.stream().mapToInt(StrategyEvaluation::comparableRunCount).sum();
        int sampleInsufficient = (int) strategies.stream().filter(s -> s.ratingLabel() == RatingLabel.SAMPLE_INSUFFICIENT).count();
        int profitable = (int) strategies.stream().filter(s -> s.totalReturn() != null && s.totalReturn().signum() > 0).count();
        int loss = (int) strategies.stream().filter(s -> s.totalReturn() != null && s.totalReturn().signum() < 0).count();
        int highRisk = (int) strategies.stream().filter(s -> s.ratingLabel() == RatingLabel.HIGH_RISK).count();
        int highDeviation = (int) strategies.stream()
                .filter(s -> s.backtestDeviation() != null && s.backtestDeviation().deviationLevel() == DeviationLevel.HIGH).count();
        int publishCount = strategies.stream().mapToInt(StrategyEvaluation::publishCount).sum();
        Integer top = strategies.stream().map(StrategyEvaluation::compositeScore).max(Comparator.naturalOrder()).orElse(null);
        Integer worst = strategies.stream().map(StrategyEvaluation::compositeScore).min(Comparator.naturalOrder()).orElse(null);

        return new PaperStrategyEvaluation.Overview(
                strategies.size(), publishCount, evaluatedRuns, comparableRuns,
                sampleInsufficient, profitable, loss, highRisk, highDeviation, top, worst);
    }

    private static PaperStrategyEvaluation.Rankings buildRankings(List<StrategyEvaluation> strategies) {
        return new PaperStrategyEvaluation.Rankings(
                rankKeys(strategies, Comparator.comparingInt(StrategyEvaluation::compositeScore).reversed(), s -> true),
                rankKeys(strategies, Comparator.comparingInt(StrategyEvaluation::compositeScore), s -> true),
                rankKeys(strategies, Comparator.comparing(
                        (StrategyEvaluation s) -> s.totalReturn() == null ? BigDecimal.valueOf(Long.MIN_VALUE) : s.totalReturn()).reversed(),
                        s -> s.totalReturn() != null),
                rankKeys(strategies, Comparator.comparing(
                        (StrategyEvaluation s) -> s.maxDrawdown() == null ? BigDecimal.ZERO : s.maxDrawdown()),
                        s -> s.maxDrawdown() != null),
                filterKeys(strategies, s -> s.ratingLabel() == RatingLabel.SAMPLE_INSUFFICIENT),
                filterKeys(strategies, s -> s.backtestDeviation() != null && s.backtestDeviation().deviationLevel() == DeviationLevel.HIGH),
                filterKeys(strategies, s -> s.ratingLabel() == RatingLabel.HIGH_RISK));
    }

    private static List<String> rankKeys(
            List<StrategyEvaluation> strategies, Comparator<StrategyEvaluation> comparator,
            java.util.function.Predicate<StrategyEvaluation> include
    ) {
        return strategies.stream()
                .filter(include)
                .sorted(comparator)
                .limit(MAX_RANKING_SIZE)
                .map(StrategyEvaluation::strategyVersionId)
                .toList();
    }

    private static List<String> filterKeys(List<StrategyEvaluation> strategies, java.util.function.Predicate<StrategyEvaluation> include) {
        return strategies.stream()
                .filter(include)
                .limit(MAX_RANKING_SIZE)
                .map(StrategyEvaluation::strategyVersionId)
                .toList();
    }

    // ---- helpers ----

    private static Map<String, List<PaperPortfolioSummary.RunRef>> groupBy(
            List<PaperPortfolioSummary.RunRef> refs, Function<PaperPortfolioSummary.RunRef, String> keyFn
    ) {
        Map<String, List<PaperPortfolioSummary.RunRef>> grouped = new LinkedHashMap<>();
        for (PaperPortfolioSummary.RunRef ref : refs) {
            grouped.computeIfAbsent(keyFn.apply(ref), k -> new ArrayList<>()).add(ref);
        }
        return grouped;
    }

    private static String normalizeKey(String key, String fallback) {
        return key != null && !key.isBlank() ? key : fallback;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) {
            return null;
        }
        return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
