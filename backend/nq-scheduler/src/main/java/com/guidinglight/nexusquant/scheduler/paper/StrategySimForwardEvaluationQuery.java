package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 只组织原canonical分析的期间证据；不创建估值、评分、订单或SIM控制动作。 */
@Service
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class StrategySimForwardEvaluationQuery {
  public static final String METHOD = "CONTINUOUS_SIM_OBSERVATION_V1";
  private static final int MAXIMUM = 10000;
  private final JdbcTemplate jdbc;
  private final StrategySimPerformanceQuery performance;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
      .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private Clock clock = Clock.systemUTC();

  public StrategySimForwardEvaluationQuery(JdbcTemplate jdbc, StrategySimPerformanceQuery performance) {
    this.jdbc = new JdbcTemplate(jdbc.getDataSource());
    this.jdbc.setQueryTimeout(5);
    this.performance = performance;
  }

  /** 调用者先完成原实例所有者/租户/费用来源校验，并在同一REPEATABLE_READ快照内调用。 */
  Evaluation read(StrategySimAnalysisQuery.Run run, Instant from, Instant to, Instant requestedAsOf) {
    Instant asOf = requestedAsOf == null ? clock.instant().truncatedTo(ChronoUnit.MICROS) : requestedAsOf;
    if (from == null || to == null || !from.isBefore(to)
        || Duration.between(from, to).compareTo(Duration.ofDays(90)) > 0 || asOf.isAfter(clock.instant()))
      throw new IllegalArgumentException("INVALID_FORWARD_OBSERVATION_RANGE");
    Instant dueFrom = ceilHour(from);
    Instant end = to.isBefore(asOf) ? to : asOf;
    Instant dueTo = end.truncatedTo(ChronoUnit.HOURS);
    if (dueTo.isBefore(dueFrom)) dueTo = dueFrom;
    if (run.createdAt() == null || run.createdAt().isAfter(asOf))
      return unavailableAsOf(run, from, to, asOf, dueFrom, dueTo);
    var data = performance.readObservation(run, from, end, asOf);
    var full = data.performance();
    if (!from.isBefore(end)) return pending(run, from, to, asOf, dueFrom, dueTo, full);
    boolean actual = "ACTUAL_EVENT_TIME".equals(full.timeBasis());
    boolean identityKnown = run.strategyVersionId() != null && !run.strategyVersionId().isBlank()
        && run.publishId() != null && !run.publishId().isBlank();
    var findings = new ArrayList<Finding>();
    var coverage = actual ? coverage(run, dueFrom, dueTo, asOf)
        : new Coverage(new Samples(0, 0, 0, 0, "NOT_COMPARABLE", List.of(), false), List.of(), false);
    if (!identityKnown) findings.add(new Finding("SOURCE_IDENTITY_UNAVAILABLE", "NOT_COMPARABLE",
        "STRATEGY_VERSION_OR_PUBLISH_IDENTITY_MISSING", from, to, null));
    if (coverage.gapsTruncated()) findings.add(new Finding("GAP_RANGES_TRUNCATED", "AVAILABLE",
        "FIRST_64_GAP_RANGES_SHOWN_COUNTS_REMAIN_COMPLETE", dueFrom, dueTo, null));
    if (!actual) findings.add(new Finding("REPLAY_EVENT_TIME", "NOT_COMPARABLE", "REPLAY_IS_NOT_FORWARD_OBSERVATION", from, to, null));
    if (actual && coverage.samples.expected() < 2)
      findings.add(new Finding("OBSERVATION_INSUFFICIENT", "INSUFFICIENT_SAMPLES", "TWO_COMPLETED_HOURLY_SLOTS_REQUIRED", dueFrom, dueTo, coverage.samples.observed()));
    if (coverage.samples.missing() > 0 || coverage.samples.unknown() > 0)
      findings.add(new Finding("DATA_INCOMPLETE", "UNAVAILABLE", "MISSING_OR_UNPROVEN_CLOSED_HOURLY_SAMPLES", dueFrom, dueTo,
          coverage.samples.missing() + coverage.samples.unknown()));
    if (!"AVAILABLE".equals(full.status()))
      findings.add(new Finding("VALUATION_UNAVAILABLE", "UNAVAILABLE", full.status(), full.coverageStart(), full.coverageEnd(), null));

    List<OrderFact> orders = actual ? orders(run, from, end, asOf) : List.of();
    List<RiskFact> risks = actual ? risks(run, from, end, asOf) : List.of();
    boolean limited = orders.size() > MAXIMUM || risks.size() > MAXIMUM || coverage.samples.windowLimited()
        || full.status().endsWith("WINDOW_LIMITED");
    if (limited) findings.add(new Finding("OBSERVATION_WINDOW_LIMITED", "UNAVAILABLE", "BOUNDED_FACT_BUDGET_EXCEEDED", from, to, null));
    boolean boundaryValuations = from.equals(full.coverageStart()) && end.equals(full.coverageEnd());
    boolean comparable = identityKnown && actual && "AVAILABLE".equals(full.status()) && "AVAILABLE".equals(coverage.samples.status())
        && boundaryValuations && !limited;
    if (actual && "AVAILABLE".equals(full.status()) && !boundaryValuations)
      findings.add(new Finding("VALUATION_UNAVAILABLE", "NOT_COMPARABLE", "OBSERVATION_BOUNDARY_MARK_UNAVAILABLE",
          full.coverageStart(), full.coverageEnd(), null));
    long beforeCoverage = full.coverageStart() == null ? 0 : data.fills().stream().filter(fill ->
        !fill.trade().at().isBefore(from) && fill.trade().at().isBefore(full.coverageStart())).count();
    long outside = full.tradesAfterCoverage() + beforeCoverage;
    if (outside > 0) {
      comparable = false;
      findings.add(new Finding("EXECUTIONS_OUTSIDE_VALUATION_COVERAGE", "NOT_COMPARABLE", "WINDOW_EXECUTIONS_HAVE_NO_MATCHING_VALUATION_COVERAGE", from, to, outside));
    }
    var observedHourlyDecisions = coverage.facts().stream().filter(StrategySimForwardEvaluationQuery::provenBar)
        .map(BarFact::decision).collect(Collectors.toSet());
    var calculated = metrics(from, end, asOf, full, data.fills(), orders, risks, comparable, limited, actual,
        observedHourlyDecisions);
    var metrics = calculated.metrics();
    if (metrics.riskEventCount() != null && metrics.riskEventCount() > 0)
      findings.add(new Finding("RISK_EVENT_OBSERVED", "AVAILABLE", "CANONICAL_ORDER_RISK_EVENTS_IN_WINDOW", from, to, metrics.riskEventCount()));
    if (metrics.executionAnomalyCount() != null && metrics.executionAnomalyCount() > 0)
      findings.add(new Finding("EXECUTION_ANOMALY", "AVAILABLE", "REJECTED_FAILED_OR_UNFINISHED_WINDOW_ORDERS", from, to, metrics.executionAnomalyCount()));
    if (!"AVAILABLE".equals(metrics.orderStatusStatus()))
      findings.add(new Finding("ORDER_STATE_UNAVAILABLE", "UNAVAILABLE", "MUTABLE_ORDER_STATE_WAS_UPDATED_AFTER_AS_OF_OR_LIMITED", from, to, null));
    if (actual && !"AVAILABLE".equals(coverage.samples.status())) cursorFinding(run, asOf, findings);
    if (to.isAfter(asOf)) findings.add(new Finding("OBSERVATION_INSUFFICIENT", "INSUFFICIENT_SAMPLES",
        "OBSERVATION_NOT_FULLY_DUE", dueTo, to, null));
    String status = !actual || !identityKnown || outside > 0 || "AVAILABLE".equals(full.status()) && !boundaryValuations ? "NOT_COMPARABLE"
        : to.isAfter(asOf) || coverage.samples.expected() < 2 || "INSUFFICIENT_SAMPLES".equals(full.status()) ? "INSUFFICIENT_SAMPLES"
        : comparable ? "AVAILABLE" : "UNAVAILABLE";
    String reason = "AVAILABLE".equals(status) ? null : !actual ? "REPLAY_EVENT_TIME"
        : !identityKnown ? "SOURCE_IDENTITY_UNAVAILABLE"
        : outside > 0 ? "EXECUTIONS_OUTSIDE_VALUATION_COVERAGE"
        : "AVAILABLE".equals(full.status()) && !boundaryValuations ? "OBSERVATION_BOUNDARY_MARK_UNAVAILABLE"
        : limited ? "OBSERVATION_WINDOW_LIMITED" : to.isAfter(asOf) ? "OBSERVATION_NOT_FULLY_DUE"
        : !"AVAILABLE".equals(coverage.samples.status()) ? "INCOMPLETE_OR_INSUFFICIENT_OBSERVATION"
        : full.status();
    if ("AVAILABLE".equals(status)) findings.add(new Finding("OBSERVATION_AVAILABLE", "AVAILABLE",
        "DATA_QUALITY_IS_NOT_STRATEGY_PROFITABILITY", dueFrom, dueTo, coverage.samples.observed()));

    String watermark = digest(Map.of("run", run.paperRunId(), "asOf", asOf, "coverage", coverage.facts,
        "marks", full.samples(), "fills", data.fills(), "orders", orders, "risks", risks));
    var result = new Evaluation(run.paperRunId(), run.canonicalAccountId(), run.strategyVersionId(), run.publishId(),
        full.timeBasis(), from, to, asOf, dueFrom, dueTo, full.coverageStart(), full.coverageEnd(), watermark, METHOD, null,
        coverage.samples, displayed(full, comparable), metrics,
        frozenCostAssumptions(run.configSnapshotJson(), mapper),
        methodDetails(calculated, full, coverage, from, end, actual), List.copyOf(findings), status, reason);
    ObjectNode semantic = mapper.valueToTree(result); semantic.remove("checksum");
    return result.withChecksum(digest(semantic));
  }

  /** 实例在asOf尚不存在时不读取未来配置或经济事实，也不把缺证据包装成零交易。 */
  private Evaluation unavailableAsOf(StrategySimAnalysisQuery.Run run, Instant from, Instant to, Instant asOf,
      Instant dueFrom, Instant dueTo) {
    String reason = "RUN_NOT_AVAILABLE_AS_OF";
    var empty = new StrategySimPerformanceQuery.View(run.paperRunId(), "UNAVAILABLE", "UNAVAILABLE", "USDT",
        null, null, null, null, null, null, null, null, null,
        com.guidinglight.nexusquant.account.domain.performance.PerformanceStatistics.unavailable(reason, 3600, 0),
        com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics.unavailable(reason), 0, List.of());
    var unknownMetrics = new Metrics(null, "UNAVAILABLE", null, "UNAVAILABLE", null, "UNAVAILABLE", List.of(),
        "UNAVAILABLE", List.of(), "UNAVAILABLE", null, "UNAVAILABLE", null, "UNAVAILABLE",
        "SIM_RECORDED_MODEL_CHARGE_NOT_REAL_EXCHANGE_FEE", false);
    long expected = Math.max(0, Duration.between(dueFrom, dueTo).toHours());
    var result = new Evaluation(run.paperRunId(), run.canonicalAccountId(), null, null, "UNAVAILABLE", from, to,
        asOf, dueFrom, dueTo, null, null, digest(Map.of("run", run.paperRunId(), "asOf", asOf, "reason", reason)),
        METHOD, null, new Samples(expected, 0, 0, expected, "UNAVAILABLE", List.of(), false), empty,
        unknownMetrics, frozenCostAssumptions(null, mapper),
        methodDetails(null, empty, null, from, to, false), List.of(new Finding(reason, "UNAVAILABLE", reason,
        from, to, null)), "UNAVAILABLE", reason);
    ObjectNode semantic = mapper.valueToTree(result); semantic.remove("checksum");
    return result.withChecksum(digest(semantic));
  }

  /** 观察尚未开始时只读取实例类型，既有挂单不是未来期间已经发生的执行异常。 */
  private Evaluation pending(StrategySimAnalysisQuery.Run run, Instant from, Instant to, Instant asOf,
      Instant dueFrom, Instant dueTo, StrategySimPerformanceQuery.View empty) {
    boolean actual = "ACTUAL_EVENT_TIME".equals(empty.timeBasis());
    String status = actual ? "INSUFFICIENT_SAMPLES" : "NOT_COMPARABLE";
    String reason = actual ? "OBSERVATION_NOT_STARTED" : "REPLAY_EVENT_TIME";
    var unknownMetrics = new Metrics(null, "UNAVAILABLE", null, "UNAVAILABLE", null, "UNAVAILABLE", List.of(),
        "UNAVAILABLE", List.of(), "UNAVAILABLE", null, "UNAVAILABLE", null, "UNAVAILABLE",
        "SIM_RECORDED_MODEL_CHARGE_NOT_REAL_EXCHANGE_FEE", false);
    var result = new Evaluation(run.paperRunId(), run.canonicalAccountId(), run.strategyVersionId(), run.publishId(),
        empty.timeBasis(), from, to, asOf, dueFrom, dueTo, null, null,
        digest(Map.of("run", run.paperRunId(), "asOf", asOf, "reason", reason)), METHOD, null,
        new Samples(0, 0, 0, 0, status, List.of(), false), empty, unknownMetrics,
        frozenCostAssumptions(run.configSnapshotJson(), mapper), methodDetails(null, empty, null, from, asOf, false),
        List.of(new Finding(actual ? "OBSERVATION_PENDING" : reason, status, reason, from, to, null)), status, reason);
    ObjectNode semantic = mapper.valueToTree(result); semantic.remove("checksum");
    return result.withChecksum(digest(semantic));
  }

  private Coverage coverage(StrategySimAnalysisQuery.Run run, Instant from, Instant to, Instant asOf) {
    long expected = Math.max(0, Duration.between(from, to).toHours());
    var facts = new ArrayList<BarFact>();
    Instant last = null;
    for (int page = 0; page < 21; page++) {
      int limit = Math.min(500, MAXIMUM + 1 - facts.size());
      var rows = jdbc.query("""
          SELECT b.open_time,b.close_time,b.available_at,b.observed_at,b.bar_sha256,
            b.bar_json->0->>'qualityStatus',d.decision_id
          FROM continuous_sim_bars b
          LEFT JOIN strategy_sim_decisions d ON d.paper_run_id=b.paper_run_id AND d.canonical_account_id=?
            AND d.signal_open_time=b.open_time AND d.created_at<=? AND d.signal_available_at<=?
          WHERE b.paper_run_id=? AND b.open_time>=? AND b.open_time<? AND b.observed_at<=? AND b.available_at<=?
            AND (?::timestamptz IS NULL OR b.open_time>?)
            AND b.open_time>(SELECT seed_last_open_time FROM continuous_sim_runs WHERE paper_run_id=b.paper_run_id)
          ORDER BY b.open_time LIMIT ?
          """, (rs, index) -> new BarFact(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant(),
              rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant(), rs.getString(5), rs.getString(6),
              rs.getString(7)), run.canonicalAccountId(), Timestamp.from(asOf), Timestamp.from(asOf),
          run.paperRunId(), Timestamp.from(from), Timestamp.from(to), Timestamp.from(asOf), Timestamp.from(asOf),
          last == null ? null : Timestamp.from(last), last == null ? null : Timestamp.from(last), limit);
      facts.addAll(rows);
      if (rows.size() < limit || facts.size() == MAXIMUM + 1) break;
      last = rows.getLast().open();
    }
    boolean limited = facts.size() > MAXIMUM;
    Map<Instant, BarFact> byTime = new HashMap<>();
    facts.forEach(bar -> byTime.put(bar.open(), bar));
    long observed = 0, missing = 0, unknown = 0;
    var gaps = new ArrayList<GapRange>(); Instant gap = null; boolean gapsTruncated = false;
    for (Instant at = from; at.isBefore(to); at = at.plusSeconds(3600)) {
      BarFact bar = byTime.get(at);
      boolean good = provenBar(bar);
      if (good) observed++; else if (bar == null) missing++; else unknown++;
      if (!good && gap == null) gap = at;
      if (good && gap != null) {
        if (gaps.size() < 64) gaps.add(new GapRange(gap, at)); else gapsTruncated = true;
        gap = null;
      }
    }
    if (gap != null) {
      if (gaps.size() < 64) gaps.add(new GapRange(gap, to)); else gapsTruncated = true;
    }
    String state = expected < 2 || observed < 2 ? "INSUFFICIENT_SAMPLES"
        : missing > 0 || unknown > 0 || limited ? "UNAVAILABLE" : "AVAILABLE";
    return new Coverage(new Samples(expected, observed, missing, unknown, state, List.copyOf(gaps), limited), List.copyOf(facts), gapsTruncated);
  }

  private List<OrderFact> orders(StrategySimAnalysisQuery.Run run, Instant from, Instant to, Instant asOf) {
    // 原表没有历史状态版本；窗口后或asOf后发生的更新不能冒充窗口内已知订单末态。
    var result = new ArrayList<OrderFact>(); Instant last = null; String id = null;
    for (int page = 0; page < 21; page++) {
      int limit = Math.min(500, MAXIMUM + 1 - result.size());
      var rows = jdbc.query("""
          SELECT order_id,status,created_at,updated_at FROM orders
          WHERE account_id=? AND exchange_code='PAPER' AND trade_env='SIM' AND created_at<? AND created_at<=?
            AND (created_at>=? OR updated_at>=? OR status NOT IN ('FILLED','CANCELLED','REJECTED','RISK_REJECTED','FAILED'))
            AND (?::timestamptz IS NULL OR (created_at,order_id)>(?::timestamptz,?::varchar))
          ORDER BY created_at,order_id LIMIT ?
          """, (rs, index) -> new OrderFact(rs.getString(1), (rs.getTimestamp(4).toInstant().isAfter(asOf) || !rs.getTimestamp(4).toInstant().isBefore(to)) ? null : rs.getString(2),
              rs.getTimestamp(3).toInstant()), run.canonicalAccountId(), Timestamp.from(to), Timestamp.from(asOf),
          Timestamp.from(from), Timestamp.from(from),
          last == null ? null : Timestamp.from(last), last == null ? null : Timestamp.from(last), id, limit);
      result.addAll(rows); if (rows.size() < limit || result.size() == MAXIMUM + 1) break;
      last = rows.getLast().created(); id = rows.getLast().id();
    }
    return List.copyOf(result);
  }

  private List<RiskFact> risks(StrategySimAnalysisQuery.Run run, Instant from, Instant to, Instant asOf) {
    var result = new ArrayList<RiskFact>(); Instant last = null; String id = null;
    for (int page = 0; page < 21; page++) {
      int limit = Math.min(500, MAXIMUM + 1 - result.size());
      var rows = jdbc.query("""
          SELECT r.risk_event_id,r.decision,r.reason,r.created_at FROM risk_events r JOIN orders o
            ON r.scope='ORDER' AND r.scope_id=o.order_id AND o.account_id=? AND o.exchange_code='PAPER' AND o.trade_env='SIM'
          WHERE r.created_at>=? AND r.created_at<? AND r.created_at<=? AND o.created_at<=?
            AND (?::timestamptz IS NULL OR (r.created_at,r.risk_event_id)>(?::timestamptz,?::varchar))
          ORDER BY r.created_at,r.risk_event_id LIMIT ?
          """, (rs, index) -> new RiskFact(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
          run.canonicalAccountId(), Timestamp.from(from), Timestamp.from(to), Timestamp.from(asOf), Timestamp.from(asOf),
          last == null ? null : Timestamp.from(last), last == null ? null : Timestamp.from(last), id, limit);
      result.addAll(rows); if (rows.size() < limit || result.size() == MAXIMUM + 1) break;
      last = rows.getLast().created(); id = rows.getLast().id();
    }
    return List.copyOf(result);
  }

  private void cursorFinding(StrategySimAnalysisQuery.Run run, Instant asOf, List<Finding> findings) {
    var values = jdbc.query("SELECT status,block_reason,gap_start_open_time,updated_at FROM continuous_sim_runs WHERE paper_run_id=? LIMIT 1",
        (rs, index) -> rs.getTimestamp(4).toInstant().isAfter(asOf) ? "CURSOR_HISTORY_UNAVAILABLE"
            : rs.getString(1) + ":" + rs.getString(2), run.paperRunId());
    if (!values.isEmpty()) findings.add(new Finding("DATA_INCOMPLETE", "UNAVAILABLE", values.getFirst(), null, null, null));
  }

  private static CalculatedMetrics metrics(Instant from, Instant to, Instant asOf, StrategySimPerformanceQuery.View full,
      List<StrategySimPerformanceQuery.Fill> fills, List<OrderFact> orders, List<RiskFact> risks,
      boolean comparable, boolean limited, boolean actual, Set<String> observedHourlyDecisions) {
    var current = fills.stream().filter(fill -> !fill.trade().at().isBefore(from)
        && fill.trade().at().isBefore(to) && !fill.trade().at().isAfter(asOf)).toList();
    Map<String, BigDecimal> amounts = new TreeMap<>(); Map<String, Long> counts = new TreeMap<>();
    boolean feesKnown = comparable;
    for (var fill : current) {
      var trade = fill.trade();
      if (trade.feeCurrency() == null || trade.fee() == null) { feesKnown = false; continue; }
      amounts.merge(trade.feeCurrency(), trade.fee(), BigDecimal::add);
      counts.merge(trade.feeCurrency(), 1L, Long::sum);
    }
    List<StrategySimAnalysisQuery.FeeTotal> fees = new ArrayList<>();
    if (feesKnown) amounts.forEach((currency, amount) -> fees.add(new StrategySimAnalysisQuery.FeeTotal(currency,
        amount.toPlainString(), counts.get(currency), counts.get(currency))));
    Map<String, Long> statuses = new TreeMap<>();
    orders.stream().filter(order -> order.status() != null).forEach(order -> statuses.merge(order.status(), 1L, Long::sum));
    boolean orderKnown = actual && !limited && orders.stream().allMatch(order -> order.status() != null);
    long anomalies = orders.stream().filter(order -> order.status() != null
        && !List.of("FILLED", "CANCELLED").contains(order.status())).count();
    Instant elapsedTo = to.isBefore(asOf) ? to : asOf;
    FrequencyBasis frequencyBasis = frequencyBasis(from, elapsedTo);
    BigDecimal days = frequencyBasis.naturalDays();
    BigDecimal frequency = comparable && days.signum() > 0 ? BigDecimal.valueOf(current.size()).divide(days, MathContext.DECIMAL128) : null;
    // 起点前一根bar可以提供开端估值，但不属于目标期已完成的小时槽位，不能多算进换手分母。
    var hourly = full.samples().stream().filter(sample -> "SIGNAL_CLOSE".equals(sample.markType())
        && observedHourlyDecisions.contains(sample.decisionId())).toList();
    BigDecimal turnover = null, mean = null;
    if (comparable && !hourly.isEmpty()) {
      mean = hourly.stream().map(StrategySimPerformanceQuery.Sample::equity).reduce(BigDecimal.ZERO, BigDecimal::add)
          .divide(BigDecimal.valueOf(hourly.size()), MathContext.DECIMAL128);
      if (mean.signum() > 0) turnover = current.stream().map(fill -> fill.trade().quantity().multiply(fill.trade().price()).abs())
          .reduce(BigDecimal.ZERO, BigDecimal::add).divide(mean, MathContext.DECIMAL128);
    }
    String valueStatus = comparable ? "AVAILABLE" : "AVAILABLE".equals(full.status()) ? "NOT_COMPARABLE" : "UNAVAILABLE";
    var result = new Metrics(comparable ? (long) current.size() : null, valueStatus, frequency,
        frequency == null ? "UNAVAILABLE" : "AVAILABLE", turnover, turnover == null ? "UNAVAILABLE" : "AVAILABLE",
        statuses.entrySet().stream().map(entry -> new StrategySimAnalysisQuery.OrderStatusCount(entry.getKey(), entry.getValue())).toList(),
        orderKnown ? "AVAILABLE" : "UNAVAILABLE", List.copyOf(fees), feesKnown ? "AVAILABLE" : valueStatus.equals("AVAILABLE") ? "UNAVAILABLE" : valueStatus,
        actual && !limited ? (long) risks.size() : null, actual && !limited ? "AVAILABLE" : "UNAVAILABLE",
        orderKnown ? anomalies : null, orderKnown ? "AVAILABLE" : "UNAVAILABLE", "SIM_RECORDED_MODEL_CHARGE_NOT_REAL_EXCHANGE_FEE", limited);
    return new CalculatedMetrics(result, frequency == null ? null : frequencyBasis,
        mean == null ? null : (long) hourly.size(), mean);
  }

  /** 自然日分母与频率计算共用同一数值，不按交易日或观测条数推导。 */
  static FrequencyBasis frequencyBasis(Instant from, Instant end) {
    long seconds = end.isAfter(from) ? Duration.between(from, end).toSeconds() : 0;
    return new FrequencyBasis(seconds, BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(86400), MathContext.DECIMAL128));
  }

  /** 只计入目标内从UTC零点到次日零点的完整且逐小时可证实日期，缺口日不能拼接。 */
  static long completeObservationDays(Instant from, Instant end, Set<Instant> provenHours) {
    Instant day = from.truncatedTo(ChronoUnit.DAYS);
    if (day.isBefore(from)) day = day.plusSeconds(86400);
    long count = 0;
    for (; !day.plusSeconds(86400).isAfter(end); day = day.plusSeconds(86400)) {
      boolean complete = true;
      for (int hour = 0; hour < 24; hour++)
        if (!provenHours.contains(day.plusSeconds(hour * 3600L))) { complete = false; break; }
      if (complete) count++;
    }
    return count;
  }

  private static boolean provenBar(BarFact bar) {
    return bar != null && bar.decision() != null && "OK".equals(bar.quality())
        && bar.close().equals(bar.open().plusSeconds(3600).minusMillis(1)) && !bar.available().isBefore(bar.close());
  }

  private JsonNode methodDetails(CalculatedMetrics calculated, StrategySimPerformanceQuery.View full,
      Coverage coverage, Instant from, Instant end, boolean actual) {
    ObjectNode details = mapper.createObjectNode();
    FrequencyBasis frequency = calculated == null ? null : calculated.frequencyBasis();
    details.set("frequencyElapsedSeconds", mapper.valueToTree(frequency == null ? null : frequency.elapsedSeconds()));
    details.set("frequencyNaturalDays", mapper.valueToTree(frequency == null ? null : frequency.naturalDays()));
    details.put("frequencyStatus", frequency == null ? "UNAVAILABLE" : "AVAILABLE");
    boolean daysKnown = actual && coverage != null && !coverage.samples().windowLimited();
    Long completeDays = daysKnown ? completeObservationDays(from, end, coverage.facts().stream()
        .filter(StrategySimForwardEvaluationQuery::provenBar).map(BarFact::open).collect(Collectors.toSet())) : null;
    details.set("completeObservationDays", mapper.valueToTree(completeDays));
    details.put("completeObservationDaysStatus", daysKnown ? "AVAILABLE" : "UNAVAILABLE");
    details.set("turnoverEquitySampleCount", mapper.valueToTree(calculated == null ? null : calculated.hourlyEquityCount()));
    details.set("turnoverMeanHourlyEquity", mapper.valueToTree(calculated == null ? null : calculated.meanHourlyEquity()));
    details.put("turnoverDefinition", "ABSOLUTE_TRADED_NOTIONAL_DIVIDED_BY_MEAN_COMPLETED_OBSERVATION_HOURLY_CLOSE_EQUITY");
    details.put("turnoverStatus", calculated == null ? "UNAVAILABLE" : calculated.metrics().turnoverStatus());
    boolean valuationsKnown = actual && ("AVAILABLE".equals(full.status()) || "INSUFFICIENT_SAMPLES".equals(full.status()));
    Integer rawCount = valuationsKnown ? full.samples().size() : null;
    details.set("rawValuationSampleCount", mapper.valueToTree(rawCount));
    details.set("displayedSampleCount", mapper.valueToTree(rawCount == null ? null : Math.min(rawCount, 1000)));
    details.set("chartDownsampled", mapper.valueToTree(rawCount == null ? null : rawCount > 1000));
    details.put("valuationSamplesStatus", valuationsKnown ? "AVAILABLE" : "UNAVAILABLE");
    return details;
  }

  private static StrategySimPerformanceQuery.View displayed(StrategySimPerformanceQuery.View full, boolean comparable) {
    List<StrategySimPerformanceQuery.Sample> samples = sampled(full.samples());
    return new StrategySimPerformanceQuery.View(full.paperRunId(), comparable ? full.status()
            : "AVAILABLE".equals(full.status()) ? "NOT_COMPARABLE" : full.status(), full.timeBasis(), full.quoteCurrency(),
        full.coverageStart(), full.coverageEnd(), full.ledgerAsOf(), full.initialEquity(), full.finalEquity(), comparable ? full.pnl() : null,
        comparable ? full.returnRatio() : null, comparable ? full.maxDrawdown() : null, comparable ? full.maxDrawdownRatio() : null,
        comparable ? full.sharpe() : com.guidinglight.nexusquant.account.domain.performance.PerformanceStatistics.unavailable("NOT_COMPARABLE", full.sharpe().samplingSeconds(), 0),
        comparable ? full.outcomes() : com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics.unavailable("NOT_COMPARABLE"), full.tradesAfterCoverage(), samples,
        full.fundingBudget());
  }

  static List<StrategySimPerformanceQuery.Sample> sampled(List<StrategySimPerformanceQuery.Sample> values) {
    if (values.size() <= 1000) return List.copyOf(values);
    var result = new ArrayList<StrategySimPerformanceQuery.Sample>(1000);
    for (int index = 0; index < 1000; index++) result.add(values.get((int) ((long) index * (values.size() - 1) / 999)));
    return List.copyOf(result);
  }

  private String digest(Object value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException | java.io.IOException error) { throw new IllegalStateException("OBSERVATION_IDENTITY_UNAVAILABLE", error); }
  }

  /** 只投影本实例冻结成本的公开标量；账户观察原快照和所有者身份不进入响应。 */
  static JsonNode frozenCostAssumptions(String raw, ObjectMapper mapper) {
    ObjectNode result = mapper.createObjectNode();
    result.put("label", "FROZEN_SIM_COST_ASSUMPTIONS");
    result.put("source", "FROZEN_SIM_RUN_CONFIG");
    result.put("feeDisclosure", "SIM_RECORDED_MODEL_CHARGE_NOT_REAL_EXCHANGE_FEE");
    ObjectNode values = result.putObject("values");
    ObjectNode states = result.putObject("fieldStatuses");
    JsonNode config = mapper.createObjectNode();
    if (raw != null && raw.length() <= 65_536) {
      try { config = mapper.readTree(raw); }
      catch (java.io.IOException invalid) { /* 损坏或缺失快照保持成本不可用，不读取当前配置作替代。 */ }
    }
    if (config == null || !config.isObject()) config = mapper.createObjectNode();
    int available = 0;
    List<String> fields = List.of("feeRate", "slippageBps", "costSource", "feeAssumptionVersion",
        "slippageAssumptionVersion", "feeSourceVersion", "feePaymentPolicy", "feeExchangeCode", "feeSymbol",
        "feeObservedAt", "feeAvailableAt", "feeSourceSnapshotSha256");
    for (String field : fields) {
      JsonNode value = config.path("feeExecutionSpec").path(field);
      if (value.isMissingNode() || value.isNull()) value = config.path("costAndRuleAssumptions").path(field);
      boolean known = value.isValueNode() && !value.isNull() && !value.asText().isBlank() && value.asText().length() <= 256;
      states.put(field, known ? "AVAILABLE" : "UNAVAILABLE");
      if (known) { values.put(field, value.asText()); available++; }
    }
    result.put("status", available == 0 ? "UNAVAILABLE" : available == fields.size() ? "AVAILABLE" : "PARTIALLY_AVAILABLE");
    return result;
  }

  private static Instant ceilHour(Instant value) {
    Instant hour = value.truncatedTo(ChronoUnit.HOURS); return hour.equals(value) ? value : hour.plusSeconds(3600);
  }

  record FrequencyBasis(long elapsedSeconds, BigDecimal naturalDays) {}
  private record CalculatedMetrics(Metrics metrics, FrequencyBasis frequencyBasis, Long hourlyEquityCount,
      BigDecimal meanHourlyEquity) {}
  private record BarFact(Instant open, Instant close, Instant available, Instant observed, String sha256,
      String quality, String decision) {}
  private record Coverage(Samples samples, List<BarFact> facts, boolean gapsTruncated) {}
  private record OrderFact(String id, String status, Instant created) {}
  private record RiskFact(String id, String decision, String reason, Instant created) {}
  public record GapRange(Instant from, Instant to) {}
  public record Samples(long expected, long observed, long missing, long unknown, String status,
      List<GapRange> gapRanges, boolean windowLimited) {}
  public record Metrics(Long tradeCount, String tradeCountStatus, BigDecimal tradeFrequencyPerDay, String tradeFrequencyStatus,
      BigDecimal twoWayTurnover, String turnoverStatus, List<StrategySimAnalysisQuery.OrderStatusCount> orderStatuses,
      String orderStatusStatus, List<StrategySimAnalysisQuery.FeeTotal> fees, String feeStatus, Long riskEventCount,
      String riskStatus, Long executionAnomalyCount, String executionStatus, String feeSource, boolean windowLimited) {}
  public record Finding(String code, String status, String reason, Instant from, Instant to, Long count) {}
  public record Evaluation(String paperRunId, long canonicalAccountId, String strategyVersionId, String publishId,
      String timeBasis, Instant observationFrom, Instant observationTo, Instant observedAt, Instant dueFrom, Instant dueTo,
      Instant coverageStart, Instant coverageEnd, String watermark, String methodVersion, String checksum, Samples samples,
      StrategySimPerformanceQuery.View performance, Metrics metrics, JsonNode costAssumptions, JsonNode methodDetails, List<Finding> findings, String status, String reason) {
    Evaluation withChecksum(String checksum) {
      return new Evaluation(paperRunId, canonicalAccountId, strategyVersionId, publishId, timeBasis, observationFrom, observationTo,
          observedAt, dueFrom, dueTo, coverageStart, coverageEnd, watermark, methodVersion, checksum, samples, performance, metrics, costAssumptions, methodDetails, findings, status, reason);
    }
  }
}
