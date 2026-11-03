package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** 用独立字面oracle验证双时间、整点半开权益和只读基准，不把合成行情称作真实观察。 */
class ClosedBarValuationTest {
  private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
  private final ObjectMapper mapper = new ObjectMapper();
  private final ClosedBarValuation verifier = new ClosedBarValuation(new JdbcTemplate(), mapper);

  @Test void receivedPriceAfterBoundaryIsLegalOnlyWhenKnowledgeCutoffIncludesTheResponse() {
    var row = bar(FROM, FROM.plusSeconds(2), FROM.plusSeconds(4));
    assertNull(verifier.validate(row, FROM, FROM.plusSeconds(4)));
    assertEquals("PRICE_NOT_AVAILABLE_AS_OF", verifier.validate(row, FROM, FROM.plusSeconds(3)));
    assertEquals(FROM.minusMillis(1), row.close());
    assertFalse(row.received().equals(row.close()));
  }

  @Test void receiptTimelinessAndClockBoundsAreIndependent() {
    assertEquals("VALUATION_COLLECTION_LATE", verifier.validate(
        bar(FROM, FROM.plusSeconds(899), FROM.plusSeconds(901)), FROM, FROM.plusSeconds(1000)));
    assertEquals("VALUATION_CLOCK_OFFSET", verifier.validate(
        bar(FROM, FROM.plusSeconds(1), FROM.plusSeconds(122)), FROM, FROM.plusSeconds(1000)));
    assertEquals("VALUATION_CLOCK_OFFSET", verifier.validate(
        bar(FROM, FROM.plusSeconds(10), FROM.plusSeconds(4)), FROM, FROM.plusSeconds(1000)));
    assertNull(verifier.validate(bar(FROM, FROM.plusSeconds(780), FROM.plusSeconds(900)), FROM, FROM.plusSeconds(1000)));
    assertEquals("VALUATION_TIME_INVALID", verifier.validate(
        bar(FROM, FROM.minusMillis(1), FROM.plusSeconds(1)), FROM, FROM.plusSeconds(1000)));
  }

  @Test void oldRowsAndChangedReceiptCannotAcquireNewCollectionAuthority() throws Exception {
    var original = bar(FROM, FROM.plusSeconds(1), FROM.plusSeconds(2));
    var json = mapper.readTree(original.json());
    ((ObjectNode) json.get(0)).put("rawPayloadJson", "[]");
    assertEquals("COLLECTION_RECEIPT_UNPROVEN", verifier.validate(withJson(original, json.toString()), FROM, FROM.plusSeconds(30)));
    assertEquals("COLLECTION_RECEIPT_MISMATCH", verifier.validate(new ClosedBarValuation.StoredBar(original.open(), original.close(),
        original.available(), original.received().plusSeconds(1), original.sha(), original.json()), FROM, FROM.plusSeconds(30)));
  }

  @Test void identityContentAndConfirmedSourceMustAllMatch() throws Exception {
    var original = bar(FROM, FROM.plusSeconds(1), FROM.plusSeconds(2));
    var json = mapper.readTree(original.json());
    var node = (ObjectNode) json.get(0);
    node.put("symbol", "ETH-USDT");
    assertEquals("VALUATION_SOURCE_INVALID", verifier.validate(withJson(original, json.toString()), FROM, FROM.plusSeconds(30)));
    node.put("symbol", "BTC-USDT"); node.put("closePrice", "101");
    assertEquals("VALUATION_SOURCE_INVALID", verifier.validate(withJson(original, json.toString()), FROM, FROM.plusSeconds(30)));
    assertEquals("VALUATION_DIGEST_MISMATCH", verifier.validate(new ClosedBarValuation.StoredBar(original.open(), original.close(),
        original.available(), original.received(), "0".repeat(64), original.json()), FROM, FROM.plusSeconds(30)));
  }

  @Test void everyBoundaryExcludesItsOwnTradeAndAdjacentWindowsJoinWithoutDoubleCounting() {
    var fills = List.of(fill("buy-from", FROM, "BUY", "1", "100", "2", "USDT", "-102"),
        fill("sell-mid", FROM.plusSeconds(3600), "SELL", "1", "110", "3", "USDT", "107"),
        fill("buy-to", FROM.plusSeconds(7200), "BUY", "1", "120", "7", "USDT", "-127"));
    var full = calculate(List.of("100", "110", "120"), fills);
    // E0=1000；E1=898+110=1008（同刻卖出尚未发生）；E2=898+107=1005。
    assertDecimal("1000", full.initialEquity()); assertDecimal("1005", full.finalEquity());
    assertDecimal("5", full.pnl()); assertDecimal("3", full.maxDrawdown());
    assertDecimal("0.005", full.returnRatio());
    assertDecimal("1008", full.samples().get(1).equity());
    assertDecimal("0", full.samples().getLast().positionQuantity());
    var firstDay = StrategySimPerformanceQuery.calculateClosedBars("run", FROM, new BigDecimal("1000"),
        marks(List.of("100", "110")), fills, FROM, FROM.plusSeconds(3600), FROM.plusSeconds(8000));
    var next = StrategySimPerformanceQuery.calculateClosedBars("run", FROM, new BigDecimal("1000"),
        marks(List.of("100", "110", "120")).subList(1, 3), fills,
        FROM.plusSeconds(3600), FROM.plusSeconds(7200), FROM.plusSeconds(8000));
    assertEquals(firstDay.finalEquity(), next.initialEquity());
    assertDecimal("5", firstDay.pnl().add(next.pnl()));
    assertEquals(1, full.outcomes().winningCount());
  }

  @Test void nonzeroOpeningPositionAndBaseFeesUseTheSameCanonicalQuantityCore() {
    var prior = fill("prior", FROM.minusSeconds(1), "BUY", "1", "100", "0.01", "BTC", "-100");
    var full = calculate(List.of("100", "110", "90"), List.of(prior));
    assertDecimal("999", full.initialEquity()); assertDecimal("989.10", full.finalEquity());
    assertDecimal("0.99", full.samples().getFirst().positionQuantity());
    assertDecimal("19.80", full.maxDrawdown());
    assertEquals("NO_CLOSED_TRADES", full.outcomes().status());
    assertEquals("continuous_sim_bars:bar-0", full.samples().getFirst().valuationSource());
    assertEquals("CANONICAL_SIM_LEDGER_AND_CLOSED_BAR_V1", full.samples().getFirst().valuationMethod());
  }

  @Test void noTradesIsAValidFlatStrategyAndNotEvidenceOfAClosedTradeWinRate() {
    var full = calculate(List.of("100", "120", "90"), List.of());
    assertEquals("AVAILABLE", full.status()); assertDecimal("0", full.pnl());
    assertDecimal("0", full.returnRatio()); assertDecimal("0", full.maxDrawdown());
    assertNull(full.outcomes().winRate());
  }

  @Test void futureReceiptCannotBeSmuggledThroughTheSharedCalculatorOrTheOldMethod() {
    var prices = marks(List.of("100", "110", "120"));
    var early = StrategySimPerformanceQuery.calculateClosedBars("run", FROM, new BigDecimal("1000"),
        prices, List.of(), FROM, FROM.plusSeconds(7200), FROM.plusSeconds(7201));
    assertEquals("MARK_SOURCE_UNAVAILABLE", early.status()); assertNull(early.pnl());
    var legacy = StrategySimPerformanceQuery.calculateObservation("run", "ACTUAL_EVENT_TIME", 3600, FROM,
        new BigDecimal("1000"), prices, List.of(), FROM, FROM.plusSeconds(7200));
    assertEquals("MARK_SOURCE_UNAVAILABLE", legacy.status());
  }

  @Test void grossBenchmarksUseTheSamePositiveOpeningEquityAcrossRisingFallingAndFlatMarkets() {
    for (String close : List.of("120", "80", "100")) {
      var full = calculate(List.of("100", "110", close), List.of());
      var projection = StrategySimForwardEvaluationQuery.benchmarks(full, "BTC-USDT", FROM, FROM.plusSeconds(7200),
          FROM.plusSeconds(8000), "frozen-prices", true, null);
      assertDecimal("1000", projection.path("initialEquity").decimalValue());
      assertDecimal("0", projection.path("cash").path("returnRatio").decimalValue());
      BigDecimal expected = new BigDecimal(close).divide(new BigDecimal("100")).subtract(BigDecimal.ONE);
      assertEquals(0, expected.compareTo(projection.path("buyHold").path("returnRatio").decimalValue()));
      assertEquals(0, expected.negate().compareTo(projection.path("buyHold").path("excessReturn").decimalValue()));
      assertEquals("frozen-prices", projection.path("priceDigest").asText());
      assertTrue(projection.path("buyHold").path("costAssumption").asText().startsWith("GROSS_PRICE_RETURN"));
    }
  }

  @Test void unavailableStrategyWithholdsAllCoupledBenchmarkNumbers() {
    var full = calculate(List.of("100", "110", "120"), List.of());
    var projection = StrategySimForwardEvaluationQuery.benchmarks(full, "BTC-USDT", FROM, FROM.plusSeconds(7200),
        FROM.plusSeconds(8000), "digest", false, "MISSING_PRICE");
    assertEquals("UNAVAILABLE", projection.path("status").asText());
    assertTrue(projection.path("initialEquity").isNull());
    for (String benchmark : List.of("cash", "buyHold"))
      for (String number : List.of("returnRatio", "finalEquity", "excessReturn"))
        assertTrue(projection.path(benchmark).path(number).isNull());
  }

  private StrategySimPerformanceQuery.View calculate(List<String> prices, List<StrategySimPerformanceQuery.Fill> fills) {
    return StrategySimPerformanceQuery.calculateClosedBars("run", FROM, new BigDecimal("1000"), marks(prices), fills,
        FROM, FROM.plusSeconds((prices.size() - 1) * 3600L), FROM.plusSeconds(prices.size() * 3600L));
  }

  private List<StrategySimPerformanceQuery.Mark> marks(List<String> prices) {
    var result = new ArrayList<StrategySimPerformanceQuery.Mark>();
    for (int index = 0; index < prices.size(); index++) {
      Instant at = FROM.plusSeconds(index * 3600L);
      result.add(new StrategySimPerformanceQuery.Mark("bar-" + index, at, prices.get(index), "BTC-USDT",
          at.plusSeconds(1).toString(), at.minusMillis(1).toString(), "CLOSED_BAR_PERIOD_END", at.plusSeconds(2)));
    }
    return result;
  }

  private StrategySimPerformanceQuery.Fill fill(String id, Instant at, String side, String quantity,
      String price, String fee, String feeCurrency, String cash) {
    return new StrategySimPerformanceQuery.Fill(new ClosedTradeStatistics.Trade(id, "BTC-USDT", side,
        new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(fee), feeCurrency, at),
        new BigDecimal(cash), 4, BigDecimal.ZERO, true, true);
  }

  private ClosedBarValuation.StoredBar bar(Instant boundary, Instant available, Instant received) {
    var raw = mapper.createObjectNode();
    raw.put("collectionMethod", ClosedBarValuation.COLLECTION_METHOD); raw.put("receivedAt", received.toString());
    raw.put("serverTime", available.toString());
    var candle = raw.putArray("candle"); candle.add(boundary.minusSeconds(3600).toEpochMilli());
    for (String value : List.of("100", "110", "90", "100", "5", "5", "500", "1")) candle.add(value);
    var bar = new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR, boundary.minusSeconds(3600),
        boundary.minusMillis(1), new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"),
        new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("500"), null, "OK", raw.toString(), available);
    return new ClosedBarValuation.StoredBar(bar.openTime(), bar.closeTime(), available, received,
        ContinuousSimRepository.contentSha(bar), SpotBarIdentity.capture(List.of(bar), mapper).canonicalJson());
  }

  private static ClosedBarValuation.StoredBar withJson(ClosedBarValuation.StoredBar bar, String json) {
    return new ClosedBarValuation.StoredBar(bar.open(), bar.close(), bar.available(), bar.received(), bar.sha(), json);
  }

  private static void assertDecimal(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
