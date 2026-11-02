package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.application.service.PriceEventSourceCodec;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.BarProvenance;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.PriceEventTimes;
import com.guidinglight.nexusquant.marketdata.domain.StoredBarIdentity;
import com.guidinglight.nexusquant.marketdata.domain.port.BarProvenanceReadPort;
import com.guidinglight.nexusquant.marketdata.domain.port.PriceEventSourceRepository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 按同一市场身份及精确bar时点批量查询，避免逐根N+1和范围错配。 */
public final class JdbcBarProvenanceReadPort implements BarProvenanceReadPort {
    private final JdbcTemplate jdbc;
    private final PriceEventSourceRepository sourceRepository;
    private final PriceEventSourceCodec codec;

    /** 保留既有直接构造调用；完整新版数据源按Flyway装配来源读取，不改变原始bar事实。 */
    public JdbcBarProvenanceReadPort(JdbcTemplate jdbc) {
        this(jdbc, new JdbcPriceEventSourceRepository(jdbc), new ObjectMapper());
    }

    public JdbcBarProvenanceReadPort(
            JdbcTemplate jdbc, PriceEventSourceRepository sourceRepository, ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(5);
        this.sourceRepository = sourceRepository;
        this.codec = new PriceEventSourceCodec(mapper);
    }

    public Map<Instant, BarProvenance> read(
            String exchange,
            String market,
            String symbol,
            String interval,
            List<Instant> openTimes) {
        if (openTimes == null || openTimes.size() > 500)
            throw new IllegalArgumentException("bar provenance requires at most 500 times");
        if (openTimes.isEmpty()) return Map.of();
        List<Object> args = new ArrayList<>(List.of(exchange, market, symbol, interval));
        openTimes.forEach(time -> args.add(Timestamp.from(time)));
        String placeholders = String.join(",", Collections.nCopies(openTimes.size(), "?"));
        var identities =
                jdbc.query(
                        "SELECT *,COALESCE(available_at,ingested_at) AS visible_at FROM marketdata_bars "
                                + "WHERE exchange_code=? AND market_type=? AND symbol=? AND \"interval\"=? AND open_time IN ("
                                + placeholders
                                + ")",
                        (rs, n) ->
                                new StoredBarIdentity(
                                        rs.getLong("marketdata_bar_id"),
                                        new HistoricalBar(
                                                rs.getString("exchange_code"),
                                                rs.getString("market_type"),
                                                rs.getString("symbol"),
                                                BarInterval.fromWireValue(rs.getString("interval")),
                                                rs.getTimestamp("open_time").toInstant(),
                                                rs.getTimestamp("close_time").toInstant(),
                                                rs.getBigDecimal("open_price"),
                                                rs.getBigDecimal("high_price"),
                                                rs.getBigDecimal("low_price"),
                                                rs.getBigDecimal("close_price"),
                                                rs.getBigDecimal("volume"),
                                                rs.getBigDecimal("quote_volume"),
                                                rs.getObject("trade_count", Long.class),
                                                rs.getString("quality_status"),
                                                rs.getString("raw_payload_json"),
                                                rs.getTimestamp("visible_at").toInstant()),
                                        rs.getString("source")),
                        args.toArray());
        var versions =
                identities.stream()
                        .map(
                                stored ->
                                        new PriceEventSourceRepository.BarVersion(
                                                stored.barId(), codec.barSha256(stored)))
                        .toList();
        var sources = sourceRepository.readCurrentVersions(versions);
        return identities.stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                stored -> stored.bar().openTime(),
                                stored ->
                                        new BarProvenance(
                                                stored.bar().openTime(),
                                                stored.source(),
                                                stored.bar().availableAt(),
                                                stored.barId(),
                                                codec.barSha256(stored),
                                                sources.containsKey(stored.barId())
                                                        ? sources.get(stored.barId()).times()
                                                        : PriceEventTimes.unavailable(
                                                                "NOT_PROVIDED"))));
    }
}
