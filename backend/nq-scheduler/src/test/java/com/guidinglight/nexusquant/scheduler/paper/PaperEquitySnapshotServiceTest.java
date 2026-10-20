package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 复现原预算回撤及原事件迟到恢复；持久化并发与权限由独立PG用例证明。 */
class PaperEquitySnapshotServiceTest {
  private final Instant at = Instant.parse("2026-10-01T00:00:00Z");
  private final StrategySimAnalysisQuery query = mock(StrategySimAnalysisQuery.class);
  private final EquityCurveSnapshotRepository repository =
      mock(EquityCurveSnapshotRepository.class);

  private StrategySimPerformanceQuery.Mark mark(String id, Instant time) {
    return new StrategySimPerformanceQuery.Mark(
        id, time, "100", "BTC-USDT", time.toString(), time.toString(), "EXECUTION_QUOTE");
  }

  private StrategySimPerformanceQuery.Fill fill(
      String id, String side, Instant time, String price, String cash) {
    var trade =
        new ClosedTradeStatistics.Trade(
            id,
            "BTC-USDT",
            side,
            BigDecimal.ONE,
            new BigDecimal(price),
            BigDecimal.ONE,
            "USDT",
            time);
    return new StrategySimPerformanceQuery.Fill(
        trade, new BigDecimal(cash), 4, BigDecimal.ZERO, true, true);
  }

  private PaperEquitySnapshotService service(
      List<StrategySimPerformanceQuery.Mark> marks, List<StrategySimPerformanceQuery.Fill> fills) {
    var identity =
        new StrategySimAnalysisQuery.Run(
            "run",
            1,
            "STOPPED",
            "BTC-USDT",
            "1h",
            "pub",
            "version",
            "strategy",
            "v1",
            1,
            "config",
            "backtest",
            "{}",
            at,
            at);
    when(query.readIdentity(1, "run")).thenReturn(Optional.of(identity));
    when(query.readPerformance(eq(1L), eq("run"), nullable(Instant.class), nullable(Instant.class)))
        .thenAnswer(
            invocation ->
                Optional.of(
                    StrategySimPerformanceQuery.calculate(
                        "run",
                        "REPLAY_EVENT_TIME",
                        3600,
                        at,
                        new BigDecimal("1000"),
                        marks,
                        fills,
                        invocation.getArgument(2),
                        invocation.getArgument(3))));
    when(repository.findById(anyString())).thenReturn(Optional.empty());
    when(repository.appendCanonical(any())).thenAnswer(invocation -> invocation.getArgument(0));
    return new PaperEquitySnapshotService(query, repository);
  }

  @Test
  void firstValuationAfterBuyIncludesOriginalBudgetInPeak() {
    var service =
        service(List.of(mark("first", at)), List.of(fill("buy", "BUY", at, "100", "-101")));
    var result = service.generate(1, "run", "first", at);
    assertEquals("VERIFIED", result.status());
    assertEquals(0, result.snapshot().totalEquity().compareTo(new BigDecimal("999")));
    assertEquals(0, result.snapshot().drawdown().compareTo(new BigDecimal("0.001")));
    assertEquals(0, result.snapshot().unrealizedPnl().compareTo(new BigDecimal("-1")));
  }

  @Test
  void originalUnsavedEventCanRecoverWithoutConsumingLaterSellOrEquity() {
    var service =
        service(
            List.of(mark("first", at), mark("later", at.plusSeconds(3600))),
            List.of(
                fill("buy", "BUY", at, "100", "-101"),
                fill("sell", "SELL", at.plusSeconds(3600), "120", "119")));
    var result = service.generate(1, "run", "first", at);
    assertEquals("VERIFIED", result.status());
    assertEquals(at, result.snapshot().snapshotTime());
    assertEquals(0, result.snapshot().cashBalance().compareTo(new BigDecimal("899")));
    assertEquals(0, result.snapshot().totalEquity().compareTo(new BigDecimal("999")));
    assertEquals(0, result.snapshot().realizedPnl().signum());
    assertEquals(0, result.snapshot().drawdown().compareTo(new BigDecimal("0.001")));
  }

  @Test
  void unavailableFuturePriceDoesNotWriteAnyAmount() {
    var future =
        new StrategySimPerformanceQuery.Mark(
            "first", at, "100", "BTC-USDT", at.plusSeconds(1).toString(), at.toString());
    var result = service(List.of(future), List.of()).generate(1, "run", "first", at);
    assertEquals("UNVALUABLE", result.status());
    assertNull(result.snapshot());
    verify(repository, never()).appendCanonical(any());
  }
}
