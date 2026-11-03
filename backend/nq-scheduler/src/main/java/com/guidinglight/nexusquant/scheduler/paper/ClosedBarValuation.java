package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** 只验证既有持久小时行情的接收证据，不采集、不回填或产生经济事实。 */
final class ClosedBarValuation {
  static final String COLLECTION_METHOD = "RESPONSE_COMPLETED_UTC_V1";
  static final long MAXIMUM_LATENESS_SECONDS = 900;
  static final long MAXIMUM_CLOCK_OFFSET_SECONDS = 120;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  ClosedBarValuation(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  Result read(StrategySimAnalysisQuery.Run run, Instant from, Instant to, Instant asOf) {
    // 修订保护保留原bar，完整旧网格不能掩盖截至知识截止已检测到的来源失效。
    // 未记录修订影响的具体小时，因此对该run保守拒绝，不用未来检测结果污染更早asOf。
    if (Boolean.TRUE.equals(jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM continuous_sim_runs WHERE paper_run_id=?
          AND block_reason='DATA_REVISION_DETECTED' AND updated_at<=?)
        """, Boolean.class, run.paperRunId(), Timestamp.from(asOf))))
      return new Result(List.of(), List.of(), "DATA_REVISION_DETECTED");
    // 90日最多2161个网格点；仍保持独立10000事实上限，不扫描其他run或窗口外行情。
    var rows = jdbc.query("""
        SELECT open_time,close_time,available_at,observed_at,bar_sha256,bar_json::text
        FROM continuous_sim_bars WHERE paper_run_id=? AND open_time>=? AND open_time<?
        ORDER BY open_time LIMIT 10001
        """, (rs, index) -> new StoredBar(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant(),
            rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant(), rs.getString(5), rs.getString(6)),
        run.paperRunId(), Timestamp.from(from.minusSeconds(3600)), Timestamp.from(to));
    if (rows.size() > 10000) return new Result(List.of(), List.of(), "VALUATION_WINDOW_LIMITED");
    var byOpen = new HashMap<Instant, StoredBar>();
    rows.forEach(row -> byOpen.put(row.open(), row));
    var marks = new ArrayList<StrategySimPerformanceQuery.Mark>();
    var prices = new ArrayList<Price>();
    for (Instant boundary = from; !boundary.isAfter(to); boundary = boundary.plusSeconds(3600)) {
      StoredBar row = byOpen.get(boundary.minusSeconds(3600));
      if (row == null) return new Result(List.of(), List.copyOf(prices), boundary.equals(from)
          ? "OPENING_PRICE_UNAVAILABLE" : boundary.equals(to) ? "CLOSING_PRICE_UNAVAILABLE" : "INTERMEDIATE_PRICE_UNAVAILABLE");
      String reason = validate(row, boundary, asOf);
      if (reason != null) return new Result(List.of(), List.copyOf(prices), reason);
      try {
        JsonNode bar = mapper.readTree(row.json()).get(0);
        BigDecimal price = decimal(bar, "closePrice");
        prices.add(new Price(boundary, row.close(), row.available(), row.received(), row.sha(), price));
        marks.add(new StrategySimPerformanceQuery.Mark(row.open().toString(), boundary, price.toPlainString(),
            "BTC-USDT", row.available().toString(), row.close().toString(), "CLOSED_BAR_PERIOD_END", row.received()));
      } catch (java.io.IOException invalid) {
        return new Result(List.of(), List.copyOf(prices), "VALUATION_SOURCE_INVALID");
      }
    }
    return new Result(List.copyOf(marks), List.copyOf(prices), null);
  }

  /** 旧raw数组没有接收完成证明，即使数值相同也不能自动升级为新方法。 */
  String validate(StoredBar row, Instant boundary, Instant asOf) {
    if (row.received().isAfter(asOf) || row.available().isAfter(asOf)) return "PRICE_NOT_AVAILABLE_AS_OF";
    if (!row.close().equals(boundary.minusMillis(1)) || !row.open().equals(boundary.minusSeconds(3600)))
      return "VALUATION_PERIOD_INVALID";
    if (row.available().isBefore(boundary) || row.received().isBefore(boundary)) return "VALUATION_TIME_INVALID";
    if (row.received().isAfter(boundary.plusSeconds(MAXIMUM_LATENESS_SECONDS))
        || row.available().isAfter(boundary.plusSeconds(MAXIMUM_LATENESS_SECONDS))) return "VALUATION_COLLECTION_LATE";
    if (row.received().isBefore(row.available().minusSeconds(5))
        || row.received().isAfter(row.available().plusSeconds(MAXIMUM_CLOCK_OFFSET_SECONDS))) return "VALUATION_CLOCK_OFFSET";
    try {
      JsonNode array = mapper.readTree(row.json());
      if (!array.isArray() || array.size() != 1) return "VALUATION_SOURCE_INVALID";
      JsonNode bar = array.get(0);
      JsonNode raw = mapper.readTree(bar.path("rawPayloadJson").asText());
      if (raw == null || !COLLECTION_METHOD.equals(raw.path("collectionMethod").asText()))
        return "COLLECTION_RECEIPT_UNPROVEN";
      if (!Instant.parse(raw.path("receivedAt").asText()).equals(row.received())
          || !Instant.parse(raw.path("serverTime").asText()).equals(row.available())) return "COLLECTION_RECEIPT_MISMATCH";
      if (!"OKX".equals(bar.path("exchangeCode").asText()) || !"SPOT".equals(bar.path("marketType").asText())
          || !"BTC-USDT".equals(bar.path("symbol").asText()) || !"1h".equals(bar.path("interval").asText())
          || !"OK".equals(bar.path("qualityStatus").asText())
          || !Instant.parse(bar.path("openTime").asText()).equals(row.open())
          || !Instant.parse(bar.path("closeTime").asText()).equals(row.close())
          || !Instant.parse(bar.path("availableAt").asText()).equals(row.available())) return "VALUATION_SOURCE_INVALID";
      JsonNode candle = raw.path("candle");
      if (!candle.isArray() || candle.size() < 9 || !"1".equals(candle.get(8).asText())
          || !Instant.ofEpochMilli(Long.parseLong(candle.get(0).asText())).equals(row.open())) return "VALUATION_SOURCE_INVALID";
      String[] fields = {"openPrice", "highPrice", "lowPrice", "closePrice", "volume"};
      for (int index = 0; index < fields.length; index++)
        if (new BigDecimal(candle.get(index + 1).asText()).compareTo(decimal(bar, fields[index])) != 0)
          return "VALUATION_SOURCE_INVALID";
      BigDecimal quoteVolume = bar.path("quoteVolume").isNull() ? null : decimal(bar, "quoteVolume");
      if (quoteVolume != null && new BigDecimal(candle.get(7).asText()).compareTo(quoteVolume) != 0)
        return "VALUATION_SOURCE_INVALID";
      HistoricalBar source = new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR,
          row.open(), row.close(), decimal(bar, "openPrice"), decimal(bar, "highPrice"), decimal(bar, "lowPrice"),
          decimal(bar, "closePrice"), decimal(bar, "volume"), quoteVolume, null, "OK",
          bar.path("rawPayloadJson").asText(), row.available());
      if (source.closePrice().signum() <= 0 || !ContinuousSimRepository.contentSha(source).equals(row.sha()))
        return "VALUATION_DIGEST_MISMATCH";
      return null;
    } catch (java.io.IOException | RuntimeException invalid) {
      return "VALUATION_SOURCE_INVALID";
    }
  }

  private static BigDecimal decimal(JsonNode value, String field) { return new BigDecimal(value.path(field).asText()); }

  record StoredBar(Instant open, Instant close, Instant available, Instant received, String sha, String json) {}
  record Price(Instant valuationBoundary, Instant sourceCloseTime, Instant availableAt, Instant receivedAt,
      String barSha256, BigDecimal price) {}
  record Result(List<StrategySimPerformanceQuery.Mark> marks, List<Price> prices, String reason) {}
}
