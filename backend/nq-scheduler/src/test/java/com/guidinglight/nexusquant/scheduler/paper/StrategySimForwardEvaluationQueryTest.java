package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.performance.PerformanceStatistics;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** 计算器和绘图预算的边界；大于正常小时观察规模的输入只作为防御性资源反例。 */
class StrategySimForwardEvaluationQueryTest {
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Test void longObservationBudgetPreservesLegacyLimitAndUsesOneFormula() {
    for (int size : List.of(1000, 1001, 10000, 10001)) {
      var marks = marks(size, -1);
      var old = StrategySimPerformanceQuery.calculate("run", "ACTUAL_EVENT_TIME", 3600, START,
          new BigDecimal("1000"), marks, List.of(), null, null);
      assertEquals(size <= 1000 ? "AVAILABLE" : "HISTORY_WINDOW_LIMITED", old.status());
      var longer = StrategySimPerformanceQuery.calculateObservation("run", "ACTUAL_EVENT_TIME", 3600, START,
          new BigDecimal("1000"), marks, List.of(), null, null);
      assertEquals(size <= 10000 ? "AVAILABLE" : "HISTORY_WINDOW_LIMITED", longer.status());
      if (size <= 10000) {
        assertEquals(size, longer.samples().size());
        assertEquals(0, longer.pnl().signum());
        assertTrue(StrategySimForwardEvaluationQuery.sampled(longer.samples()).size() <= 1000);
      }
      if (size == 1000) {
        assertEquals(old.pnl(), longer.pnl()); assertEquals(old.maxDrawdown(), longer.maxDrawdown());
        assertEquals(old.sharpe(), longer.sharpe());
      }
    }
  }

  @Test void fullPathDrawdownIsNeverReplacedByTheDownsampledChart() {
    var trade = new ClosedTradeStatistics.Trade("buy", "BTC-USDT", "BUY", BigDecimal.ONE,
        new BigDecimal("100"), BigDecimal.ZERO, "USDT", START.plusSeconds(1));
    var fill = new StrategySimPerformanceQuery.Fill(trade, new BigDecimal("-100"), 2, BigDecimal.ZERO, true, true);
    var full = StrategySimPerformanceQuery.calculateObservation("run", "ACTUAL_EVENT_TIME", 3600, START,
        new BigDecimal("1000"), marks(10000, 5005), List.of(fill), null, null);
    assertEquals(0, full.maxDrawdown().compareTo(new BigDecimal("99")));
    var chart = StrategySimForwardEvaluationQuery.sampled(full.samples());
    assertEquals(1000, chart.size());
    assertEquals(full.samples().getFirst(), chart.getFirst());
    assertEquals(full.samples().getLast(), chart.getLast());
    assertFalse(chart.stream().anyMatch(sample -> sample.decisionId().equals("mark-5005")));
    assertEquals(0, full.pnl().signum());
  }

  @Test void halfOpenOpeningEquityIncludesTheFeeOfAnExecutionExactlyAtFrom() {
    var buy = new ClosedTradeStatistics.Trade("buy", "BTC-USDT", "BUY", BigDecimal.ONE,
        new BigDecimal("100"), new BigDecimal("2"), "USDT", START);
    var fill = new StrategySimPerformanceQuery.Fill(buy, new BigDecimal("-102"), 4, BigDecimal.ZERO, true, true);
    Instant end = START.plusSeconds(3600);
    var atEnd = new ClosedTradeStatistics.Trade("excluded", "BTC-USDT", "SELL", BigDecimal.ONE,
        new BigDecimal("100"), new BigDecimal("7"), "USDT", end);
    var excluded = new StrategySimPerformanceQuery.Fill(atEnd, new BigDecimal("93"), 4, BigDecimal.ZERO, true, true);
    var marks = marks(2, -1);
    var legacy = StrategySimPerformanceQuery.calculate("run", "ACTUAL_EVENT_TIME", 3600, end,
        new BigDecimal("1000"), marks, List.of(fill), START, end);
    var observed = StrategySimPerformanceQuery.calculateObservation("run", "ACTUAL_EVENT_TIME", 3600, end,
        new BigDecimal("1000"), marks, List.of(fill, excluded), START, end);
    // Oracle: 开盘1000，买入后现金898+持仓100=998，期间损失及最大回撤均为2。
    assertEquals(0, observed.initialEquity().compareTo(new BigDecimal("1000")));
    assertEquals(0, observed.finalEquity().compareTo(new BigDecimal("998")));
    assertEquals(0, observed.pnl().compareTo(new BigDecimal("-2")));
    assertEquals(0, observed.maxDrawdown().compareTo(new BigDecimal("2")));
    assertEquals(0, observed.returnRatio().compareTo(new BigDecimal("-0.002")));
    assertEquals(0, observed.samples().getFirst().positionQuantity().signum());
    assertEquals(0, observed.samples().getLast().positionQuantity().compareTo(BigDecimal.ONE));
    assertEquals(0, legacy.initialEquity().compareTo(new BigDecimal("998")));
    assertEquals(0, legacy.pnl().signum());
  }

  @Test void sellExactlyAtFromUsesPriorCostBasisAndIsIncludedInWindowOutcomes() {
    var buy = new ClosedTradeStatistics.Trade("prior", "BTC-USDT", "BUY", BigDecimal.ONE,
        new BigDecimal("100"), BigDecimal.ZERO, "USDT", START.minusSeconds(1));
    var sell = new ClosedTradeStatistics.Trade("opening-sell", "BTC-USDT", "SELL", BigDecimal.ONE,
        new BigDecimal("100"), new BigDecimal("2"), "USDT", START);
    var fills = List.of(new StrategySimPerformanceQuery.Fill(buy, new BigDecimal("-100"), 2, BigDecimal.ZERO, true, true),
        new StrategySimPerformanceQuery.Fill(sell, new BigDecimal("98"), 4, BigDecimal.ZERO, true, true));
    var observed = StrategySimPerformanceQuery.calculateObservation("run", "ACTUAL_EVENT_TIME", 3600, START,
        new BigDecimal("1000"), marks(2, -1), fills, START, START.plusSeconds(3600));
    assertEquals(0, observed.initialEquity().compareTo(new BigDecimal("1000")));
    assertEquals(0, observed.pnl().compareTo(new BigDecimal("-2")));
    assertEquals("AVAILABLE", observed.outcomes().status());
    assertEquals(1, observed.outcomes().losingCount());
  }

  @Test void sharpeKeepsItsDefaultBoundAndSupportsOnlyExplicitFiniteBudgets() {
    var values = new ArrayList<PerformanceStatistics.Point>();
    for (int i = 0; i < 1001; i++) values.add(new PerformanceStatistics.Point(START.plusSeconds(i * 3600L),
        new BigDecimal("1000"), BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class, () -> PerformanceStatistics.sharpe(values, 3600));
    assertEquals("ZERO_VARIANCE", PerformanceStatistics.sharpe(values, 3600, 10000).status());
    assertThrows(IllegalArgumentException.class, () -> PerformanceStatistics.sharpe(values, 3600, Integer.MAX_VALUE));
  }

  @Test void naturalDayDenominatorAndCompleteUtcDaysDoNotCombineGappedDates() {
    var basis = StrategySimForwardEvaluationQuery.frequencyBasis(START, START.plusSeconds(43200));
    assertEquals(43200, basis.elapsedSeconds());
    assertEquals(0, basis.naturalDays().compareTo(new BigDecimal("0.5")));
    var hours = new HashSet<Instant>();
    // 两个UTC日各有12个可信小时，不能把24个离散小时称为一个完整观察日。
    for (int hour = 0; hour < 12; hour++) {
      hours.add(START.plusSeconds(hour * 3600L));
      hours.add(START.plusSeconds(86400 + hour * 3600L));
    }
    assertEquals(24, hours.size());
    assertEquals(0, StrategySimForwardEvaluationQuery.completeObservationDays(START, START.plusSeconds(172800), hours));
    for (int hour = 12; hour < 24; hour++) hours.add(START.plusSeconds(hour * 3600L));
    assertEquals(1, StrategySimForwardEvaluationQuery.completeObservationDays(START, START.plusSeconds(172800), hours));
    assertEquals(0, StrategySimForwardEvaluationQuery.completeObservationDays(START.plusSeconds(1), START.plusSeconds(172800), hours));
  }

  @Test void replayObservationNeverFallsBackToUnboundedEconomicFactsAfterAsOf() throws Exception {
    var source = mock(DataSource.class);
    var connection = mock(Connection.class);
    var statement = mock(PreparedStatement.class);
    var rows = mock(ResultSet.class);
    var metadata = mock(ResultSetMetaData.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      assertTrue(sql.contains("continuous_sim_runs"), "Replay observation must not read trade/ledger/mark facts: " + sql);
      return statement;
    });
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.getMetaData()).thenReturn(metadata);
    when(metadata.getColumnCount()).thenReturn(1);
    when(rows.next()).thenReturn(true, false);
    when(rows.getBoolean(1)).thenReturn(false);
    var run = new StrategySimAnalysisQuery.Run("replay", 10L, "RUNNING", "BTC-USDT", "1h", "publish", "version",
        "SMA", "v1", 1, "config", "backtest", "{}", START.minusSeconds(3600), START);
    var query = new StrategySimPerformanceQuery(new JdbcTemplate(source));
    var value = query.readObservation(run, START, START.plusSeconds(7200), START.plusSeconds(3600));
    assertEquals("NOT_COMPARABLE", value.performance().status());
    assertEquals("REPLAY_EVENT_TIME", value.performance().timeBasis());
    assertNull(value.performance().pnl());
    assertNull(value.performance().initialEquity());
    assertNull(value.performance().ledgerAsOf());
    assertTrue(value.performance().samples().isEmpty());
    assertTrue(value.fills().isEmpty());
    verify(connection, times(1)).prepareStatement(anyString());
  }

  @Test void futureObservationDoesNotForecastExistingPendingOrdersAsWindowAnomalies() throws Exception {
    var source = mock(DataSource.class);
    var connection = mock(Connection.class);
    var statement = mock(PreparedStatement.class);
    var rows = mock(ResultSet.class);
    var metadata = mock(ResultSetMetaData.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      assertTrue(sql.contains("continuous_sim_runs"), "Future window cannot inspect existing pending orders or economic facts: " + sql);
      return statement;
    });
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.getMetaData()).thenReturn(metadata);
    when(metadata.getColumnCount()).thenReturn(1);
    when(rows.next()).thenReturn(true, false);
    when(rows.getBoolean(1)).thenReturn(true);
    var jdbc = new JdbcTemplate(source);
    var query = new StrategySimForwardEvaluationQuery(jdbc, new StrategySimPerformanceQuery(jdbc));
    var run = new StrategySimAnalysisQuery.Run("continuous-with-old-pending-order", 10L, "RUNNING", "BTC-USDT", "1h",
        "publish", "version", "SMA", "v1", 1, "config", "backtest", "{}", START.minusSeconds(3600), START);
    var value = query.read(run, START.plusSeconds(3600), START.plusSeconds(10800), START);
    assertEquals("INSUFFICIENT_SAMPLES", value.status());
    assertEquals("OBSERVATION_NOT_STARTED", value.reason());
    assertEquals(0, value.samples().expected());
    assertEquals(0, value.samples().observed());
    assertNull(value.performance().pnl());
    assertNull(value.performance().initialEquity());
    assertTrue(value.performance().samples().isEmpty());
    assertNull(value.metrics().executionAnomalyCount());
    assertNull(value.metrics().riskEventCount());
    assertNull(value.metrics().tradeCount());
    assertTrue(value.metrics().orderStatuses().isEmpty());
    assertFalse(value.findings().stream().anyMatch(finding -> "EXECUTION_ANOMALY".equals(finding.code())));
    verify(connection, times(1)).prepareStatement(anyString());
  }

  @Test void frozenCostsKeepExactScalarValuesAndExcludePrivateSnapshotFields() {
    var result = StrategySimForwardEvaluationQuery.frozenCostAssumptions("""
        {"costAndRuleAssumptions":{"feeRate":"0.00100","slippageBps":"10","costSource":"EXPERIMENT_ASSUMPTION",
          "feeAssumptionVersion":"frozen-v1","slippageAssumptionVersion":"slip-v1","secret":"never-expose"},
          "feeExecutionSpec":{"feeSourceSnapshotJson":"private-account-observation","feeSourceOwnerUserId":"8765"}}
        """, new ObjectMapper());
    assertEquals("FROZEN_SIM_COST_ASSUMPTIONS", result.path("label").asText());
    assertEquals("0.00100", result.path("values").path("feeRate").asText());
    assertEquals("10", result.path("values").path("slippageBps").asText());
    assertEquals("PARTIALLY_AVAILABLE", result.path("status").asText());
    assertEquals("UNAVAILABLE", result.path("fieldStatuses").path("feeSourceVersion").asText());
    assertFalse(result.toString().contains("never-expose"));
    assertFalse(result.toString().contains("private-account-observation"));
    assertFalse(result.toString().contains("8765"));
  }

  @Test void absentOrInvalidFrozenCostsRemainUnavailableAndNeverBecomeZeroRates() {
    for (String raw : List.of("{}", "{broken", "null", "{\"executionSpec\":{\"feeRate\":\"9\"}}")) {
      var result = StrategySimForwardEvaluationQuery.frozenCostAssumptions(raw, new ObjectMapper());
      assertEquals("UNAVAILABLE", result.path("status").asText());
      assertEquals(0, result.path("values").size());
      assertEquals("UNAVAILABLE", result.path("fieldStatuses").path("feeRate").asText());
    }
  }

  private static List<StrategySimPerformanceQuery.Mark> marks(int count, int low) {
    var result = new ArrayList<StrategySimPerformanceQuery.Mark>(count);
    for (int i = 0; i < count; i++) {
      Instant at = START.plusSeconds(i * 3600L);
      result.add(new StrategySimPerformanceQuery.Mark("mark-" + i, at, i == low ? "1" : "100", "BTC-USDT",
          at.toString(), at.toString()));
    }
    return result;
  }
}
