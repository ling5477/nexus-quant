package com.guidinglight.nexusquant.research.domain.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.backtest.ResearchRunFacts;
import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** 按已落库 bar/权益事实分段，不调参、不重新取行情、不在边界重置持仓。 */
public final class ResearchValidityCalculator {
    // JSONB 读回与中间 JSON 树必须保留 Decimal，避免经过 double 后丢失低位。
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private ResearchValidityCalculator() { }

    public static ResearchRunFacts facts(BacktestRun run) {
        try {
            var summary = MAPPER.readTree(run.summaryJson() == null ? "{}" : run.summaryJson());
            if (!summary.has("researchFacts")) return null;
            var facts = MAPPER.treeToValue(summary.get("researchFacts"), ResearchRunFacts.class);
            if (!run.backtestRunId().equals(facts.backtestRunId())
                    || !run.strategyVersionId().equals(facts.strategyVersionId())
                    || !MAPPER.readTree(run.strategyVersionSnapshotJson()).path("checksum").asText()
                            .equals(facts.strategyChecksum())
                    || !summary.path("datasetId").asText().equals(facts.datasetId())
                    || !summary.path("symbol").asText().equals(facts.symbol())
                    || !summary.path("interval").asText().equals(facts.interval())
                    || !summary.path("barContentSha256").asText().equals(facts.barContentSha256())
                    || !MAPPER.readTree(run.datasetSnapshotJson()).equals(MAPPER.readTree(facts.datasetSnapshotJson()))
                    || !MAPPER.readTree(run.configSnapshotJson()).path("executionSpec")
                            .equals(MAPPER.readTree(facts.assumptions().executionSpecJson()))
                    || new BigDecimal(MAPPER.readTree(run.configSnapshotJson()).path("initialCapital").asText())
                            .compareTo(facts.assumptions().requestedInitialCapital()) != 0
                    || ResearchRunFacts.decimal(facts.assumptions().requestedInitialCapital())
                            .compareTo(facts.assumptions().initialCapital()) != 0) {
                throw new IllegalStateException("RESEARCH_FACT_IDENTITY_MISMATCH");
            }
            var spec = MAPPER.readTree(facts.assumptions().executionSpecJson());
            if (facts.assumptions().feeRate().compareTo(new BigDecimal(spec.path("feeRate").asText())) != 0
                    || facts.assumptions().slippageBps().compareTo(new BigDecimal(spec.path("slippageBps").asText())) != 0
                    || !"NEXT_OPEN_STRICTLY_AFTER_AVAILABLE_AT".equals(facts.assumptions().executionTimingPolicy())
                    || !"MARK_TO_MARKET".equals(facts.assumptions().finalEquityPolicy())) {
                throw new IllegalStateException("RESEARCH_ASSUMPTION_MISMATCH");
            }
            return facts;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("RESEARCH_FACT_JSON_INVALID", ex);
        }
    }

    public static ResearchValidity calculate(ResearchRunFacts facts, List<SimPnlSnapshot> input,
                                             List<SimTrade> trades, Instant evaluatedAt) {
        if (facts == null) return ResearchValidity.unavailable("LEGACY_RUN_WITHOUT_RESEARCH_FACTS");
        List<SimPnlSnapshot> snapshots = input.stream().sorted(Comparator.comparing(SimPnlSnapshot::snapshotTime)).toList();
        int count = facts.bars().size();
        if (count == 0 || count != snapshots.size()) throw new IllegalStateException("RESEARCH_BAR_FACT_COUNT_MISMATCH");
        for (int i = 0; i < count; i++) {
            var bar = facts.bars().get(i);
            if (!facts.backtestRunId().equals(snapshots.get(i).backtestRunId())
                    || !Instant.parse(bar.snapshotTime()).equals(snapshots.get(i).snapshotTime())
                    || !Instant.parse(bar.startTime()).isBefore(Instant.parse(bar.endTime()))
                    || Instant.parse(bar.snapshotTime()).isBefore(Instant.parse(bar.endTime()))
                    || (i > 0 && (!Instant.parse(facts.bars().get(i - 1).endTime()).isBefore(Instant.parse(bar.startTime()))
                            || !snapshots.get(i - 1).snapshotTime().isBefore(snapshots.get(i).snapshotTime())))) {
                throw new IllegalStateException("RESEARCH_BAR_FACT_TIME_MISMATCH");
            }
        }
        if (trades.stream().anyMatch(trade -> !facts.backtestRunId().equals(trade.backtestRunId()))) {
            throw new IllegalStateException("RESEARCH_TRADE_IDENTITY_MISMATCH");
        }
        var identity = new ResearchValidity.Identity(facts.backtestRunId(), facts.datasetId(), facts.barContentSha256(),
                facts.strategyVersionId(), facts.strategyChecksum(), facts.symbol(), facts.interval(),
                facts.datasetSnapshotJson(), evaluatedAt.toString());
        var full = segment(facts, snapshots, trades, 0, count);
        int split = count * 70 / 100;
        boolean sufficient = split > 0 && split < count;
        var benchmark = facts.benchmark();
        return new ResearchValidity(sufficient ? "AVAILABLE" : "INSUFFICIENT_DATA",
                sufficient ? null : "TWO_NONEMPTY_WINDOWS_REQUIRED", ResearchValidity.SPLIT_POLICY,
                "CONTINUOUS_RUN_PREVIOUS_BAR_EQUITY", identity, facts.assumptions(), full,
                sufficient ? segment(facts, snapshots, trades, 0, split) : null,
                sufficient ? segment(facts, snapshots, trades, split, count) : null, benchmark,
                full.strategyReturn() != null && benchmark != null && benchmark.benchmarkReturn() != null
                        ? full.strategyReturn().subtract(benchmark.benchmarkReturn()) : null);
    }

    private static ResearchValidity.Segment segment(ResearchRunFacts facts, List<SimPnlSnapshot> snapshots,
                                                     List<SimTrade> trades, int start, int end) {
        BigDecimal baseline = start == 0 ? facts.assumptions().initialCapital() : snapshots.get(start - 1).equity();
        BigDecimal feeBaseline = start == 0 ? BigDecimal.ZERO : snapshots.get(start - 1).totalFee();
        BigDecimal slipBaseline = start == 0 ? BigDecimal.ZERO : snapshots.get(start - 1).totalSlippage();
        BigDecimal peak = baseline;
        BigDecimal drawdown = BigDecimal.ZERO;
        BigDecimal drawdownRate = BigDecimal.ZERO;
        for (int i = start; i < end; i++) {
            BigDecimal equity = snapshots.get(i).equity();
            peak = peak.max(equity);
            BigDecimal decline = peak.subtract(equity);
            drawdown = drawdown.max(decline);
            if (peak.signum() > 0) drawdownRate = drawdownRate.max(decline.divide(peak, 18, RoundingMode.HALF_UP));
        }
        var first = facts.bars().get(start);
        var last = facts.bars().get(end - 1);
        var finalSnapshot = snapshots.get(end - 1);
        BigDecimal pnl = finalSnapshot.equity().subtract(baseline);
        int tradeCount = (int) trades.stream().filter(trade -> !trade.tradedAt().isBefore(Instant.parse(first.startTime()))
                && !trade.tradedAt().isAfter(Instant.parse(last.endTime()))).count();
        return new ResearchValidity.Segment(baseline.signum() > 0 ? "AVAILABLE" : "NOT_AVAILABLE",
                first.startTime(), last.endTime(), end - start, baseline, finalSnapshot.equity(),
                baseline.signum() > 0 ? pnl.divide(baseline, 18, RoundingMode.HALF_UP) : null,
                pnl, drawdown.setScale(18, RoundingMode.HALF_UP), drawdownRate, tradeCount,
                finalSnapshot.totalFee().subtract(feeBaseline), finalSnapshot.totalSlippage().subtract(slipBaseline));
    }

    public static String append(String reportJson, ResearchValidity validity) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(reportJson);
            node.set("researchValidity", MAPPER.valueToTree(validity));
            return node.toString();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("RESEARCH_REPORT_JSON_INVALID", ex);
        }
    }

    public static ResearchValidity read(String reportJson) {
        try {
            var node = MAPPER.readTree(reportJson == null || reportJson.isBlank() ? "{}" : reportJson);
            if (!node.hasNonNull("researchValidity")) return ResearchValidity.unavailable("LEGACY_REPORT_WITHOUT_RESEARCH_VALIDITY");
            return MAPPER.treeToValue(node.get("researchValidity"), ResearchValidity.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("RESEARCH_REPORT_JSON_INVALID", ex);
        }
    }
}
