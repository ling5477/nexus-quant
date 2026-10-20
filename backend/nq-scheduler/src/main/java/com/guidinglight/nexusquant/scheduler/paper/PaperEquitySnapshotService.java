package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.common.currency.ValuationMetadata;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 将已有canonical统计冻结成旧Paper可读投影；不生成价格、成交或第二套资金事实。 */
@Service
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class PaperEquitySnapshotService {
  private static final String METHOD = "CANONICAL_SIM_LEDGER_AND_FROZEN_MARK_V1";
  private static final DateTimeFormatter MICRO_TIME =
      new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
  private final StrategySimAnalysisQuery analysis;
  private final EquityCurveSnapshotRepository snapshots;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  public PaperEquitySnapshotService(
      StrategySimAnalysisQuery analysis, EquityCurveSnapshotRepository snapshots) {
    this.analysis = analysis;
    this.snapshots = snapshots;
  }

  /** 只接受事件身份，拒绝客户端金额；先按当前归属核对，再读取原快照或一致性统计。 */
  public Result generate(long owner, String runId, String decisionId, Instant snapshotTime) {
    if (decisionId == null
        || decisionId.isBlank()
        || decisionId.length() > 64
        || snapshotTime == null
        || !snapshotTime.equals(snapshotTime.truncatedTo(ChronoUnit.MICROS)))
      throw new IllegalArgumentException("INVALID_EQUITY_EVENT_IDENTITY");
    var owned =
        analysis
            .readIdentity(owner, runId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SIM_RUN_NOT_FOUND"));
    String id =
        "eqp-"
            + UUID.nameUUIDFromBytes(
                (runId + "/" + snapshotTime + "/" + METHOD).getBytes(StandardCharsets.UTF_8));
    Optional<EquityCurveSnapshot> original = snapshots.findById(id);
    if (original.isPresent()) return restored(original.get(), runId, decisionId);
    // 估值序列由现有查询在REPEATABLE_READ事务中读取，账户/版本/一次性预算为不可变身份。
    var view =
        analysis
            .readPerformance(owner, runId, null, null)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SIM_RUN_NOT_FOUND"));
    if (!"USDT".equals(view.valuationCurrency())
        || view.samples().isEmpty()
        || !("AVAILABLE".equals(view.status()) || "INSUFFICIENT_SAMPLES".equals(view.status()))
        || view.fundingBudget() == null) return new Result("UNVALUABLE", view.status(), null);
    // 原响应尚未确认时，后续事件不能让原身份永久失效。沿用原始历史起点，只截取原事件以前的事实。
    // 现有查询在计算时仅消费trade.eventTime<=mark.eventTime；后续成交只计入tradesAfterCoverage，不进入原快照。
    if (!view.samples().getLast().eventAt().equals(snapshotTime)) {
      boolean known =
          view.samples().stream()
              .anyMatch(
                  value ->
                      value.eventAt().equals(snapshotTime)
                          && value.decisionId().equals(decisionId));
      if (!known) return new Result("UNVALUABLE", "ORIGINAL_EQUITY_EVENT_UNAVAILABLE", null);
      view =
          analysis
              .readPerformance(
                  owner, runId, view.coverageStart().minus(1, ChronoUnit.MICROS), snapshotTime)
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SIM_RUN_NOT_FOUND"));
      if (view.samples().isEmpty()
          || view.fundingBudget() == null
          || !("AVAILABLE".equals(view.status()) || "INSUFFICIENT_SAMPLES".equals(view.status())))
        return new Result("UNVALUABLE", view.status(), null);
    }
    var sample = view.samples().getLast();
    if (!sample.eventAt().equals(snapshotTime) || !sample.decisionId().equals(decisionId))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "EQUITY_EVENT_CHANGED_READ_AGAIN");
    if (!"VERIFIED".equals(sample.valuationStatus())
        || sample.markPrice().signum() <= 0
        || sample.markObservedAt().isAfter(snapshotTime)
        || sample.markVisibleAt().isAfter(snapshotTime)
        || view.outcomes() == null
        || !("AVAILABLE".equals(view.outcomes().status())
            || "NO_CLOSED_TRADES".equals(view.outcomes().status())))
      return new Result("UNVALUABLE", "VALUATION_BASIS_INCOMPLETE", null);
    BigDecimal cash = amount(sample.cash());
    BigDecimal position =
        amount(
            sample
                .positionQuantity()
                .multiply(sample.markPrice())
                .add(
                    sample.discountAssetQuoteValue() == null
                        ? BigDecimal.ZERO
                        : sample.discountAssetQuoteValue()));
    BigDecimal equity = cash.add(position);
    if (equity.compareTo(amount(sample.equity())) != 0)
      return new Result("UNVALUABLE", "EQUITY_COMPONENT_MISMATCH", null);
    BigDecimal realized = amount(view.outcomes().netProfit().subtract(view.outcomes().netLoss()));
    BigDecimal unrealized = amount(equity.subtract(view.fundingBudget()).subtract(realized));
    BigDecimal peak =
        view.samples().stream()
            .map(StrategySimPerformanceQuery.Sample::equity)
            .max(BigDecimal::compareTo)
            .orElseThrow()
            .max(view.fundingBudget());
    if (peak.signum() <= 0) return new Result("UNVALUABLE", "DRAWDOWN_BASIS_UNAVAILABLE", null);
    BigDecimal drawdown =
        peak.subtract(equity).max(BigDecimal.ZERO).divide(peak, 18, RoundingMode.HALF_UP);
    var details = mapper.createObjectNode();
    details
        .put("paperRunId", runId)
        .put("canonicalAccountId", owned.canonicalAccountId())
        .put("decisionId", decisionId)
        .put("snapshotTime", MICRO_TIME.format(snapshotTime))
        .put("valuationCurrency", "USDT")
        .put("timeBasis", view.timeBasis())
        .put("publishId", owned.publishId())
        .put("strategyVersionId", owned.strategyVersionId())
        .put("fundingBudget", view.fundingBudget().toPlainString())
        .put("drawdownBaseline", "ORIGINAL_BUDGET_AND_COVERED_EQUITY_PEAK")
        .put("laterTradesExcluded", view.tradesAfterCoverage())
        .put("precision", "NUMERIC_36_18_HALF_UP")
        .put("pnlMethod", view.outcomes().method());
    details.set("mark", mapper.valueToTree(sample));
    details.set("ledgerAsOf", mapper.valueToTree(view.ledgerAsOf()));
    details.set("closedTradeOutcomes", mapper.valueToTree(view.outcomes()));
    details.put(
        "factSources",
        "strategy_sim_decisions;trades;ledger_entries;sim_asset_funding;frozen fee and price"
            + " snapshots");
    var snapshot =
        new EquityCurveSnapshot(
            id,
            runId,
            snapshotTime,
            equity,
            cash,
            position,
            unrealized,
            realized,
            drawdown,
            "CANONICAL_SIM_PROJECTION",
            Instant.now().truncatedTo(ChronoUnit.MICROS),
            new ValuationMetadata(
                "USDT",
                snapshotTime,
                sample.valuationSource(),
                METHOD,
                "VERIFIED",
                details.toString()));
    return restored(snapshots.appendCanonical(snapshot), runId, decisionId);
  }

  /** 原对象读取与生成使用相同实例归属；不存在不能被解释为生成失败。 */
  public Optional<EquityCurveSnapshot> find(long owner, String runId, String snapshotId) {
    if (analysis.readIdentity(owner, runId).isEmpty()) return Optional.empty();
    return snapshots.findById(snapshotId).filter(s -> runId.equals(s.paperRunId()));
  }

  private Result restored(EquityCurveSnapshot snapshot, String runId, String decisionId) {
    try {
      if (!runId.equals(snapshot.paperRunId())
          || !decisionId.equals(
              mapper.readTree(snapshot.valuation().detailsJson()).path("decisionId").asText()))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "EQUITY_EVENT_IDENTITY_CONFLICT");
      return new Result("VERIFIED", null, snapshot);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("STORED_EQUITY_PROVENANCE_INVALID", e);
    }
  }

  private BigDecimal amount(BigDecimal value) {
    BigDecimal result = value.setScale(18, RoundingMode.HALF_UP);
    if (result.precision() > 36) throw new IllegalArgumentException("EQUITY_AMOUNT_OUT_OF_RANGE");
    return result;
  }

  public record Result(String status, String reason, EquityCurveSnapshot snapshot) {}
}
