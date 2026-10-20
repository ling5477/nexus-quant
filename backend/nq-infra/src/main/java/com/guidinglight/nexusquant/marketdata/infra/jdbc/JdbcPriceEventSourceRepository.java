package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.PriceEventSourceRecord;
import com.guidinglight.nexusquant.marketdata.domain.StoredBarIdentity;
import com.guidinglight.nexusquant.marketdata.domain.port.PriceEventSourceRepository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** 独立来源存储不写marketdata_bars；共享锁保护版本匹配，冲突插入不破坏事务后续读取。 */
public final class JdbcPriceEventSourceRepository implements PriceEventSourceRepository {
    private final JdbcTemplate jdbc;

    public JdbcPriceEventSourceRepository(JdbcTemplate jdbc) {
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(5);
    }

    @Override
    public Optional<StoredBarIdentity> findBar(long id, boolean lock) {
        return jdbc
                .query(
                        "SELECT *,coalesce(available_at,ingested_at) visible_at FROM marketdata_bars WHERE marketdata_bar_id=?"
                                + (lock ? " FOR SHARE" : ""),
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
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PriceEventSourceRecord> findById(UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM marketdata_price_event_sources WHERE source_id=?",
                        (rs, n) -> map(rs),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PriceEventSourceRecord> findForBar(long id, String hash) {
        return jdbc
                .query(
                        "SELECT * FROM marketdata_price_event_sources WHERE marketdata_bar_id=? AND bar_identity_sha256=?",
                        (rs, n) -> map(rs),
                        id,
                        hash)
                .stream()
                .findFirst();
    }

    @Override
    public boolean insertIfAbsent(PriceEventSourceRecord source) {
        return jdbc.update(
                        """
                INSERT INTO marketdata_price_event_sources(source_id,marketdata_bar_id,bar_identity_sha256,source_name,document_sha256,document_json,bar_snapshot_json,
                    open_price_at,high_price_at,low_price_at,close_price_at,created_by_user_id,created_at)
                VALUES(?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """,
                        source.sourceId(),
                        source.barId(),
                        source.barIdentitySha256(),
                        source.sourceName(),
                        source.documentSha256(),
                        source.documentJson(),
                        source.barSnapshotJson(),
                        Timestamp.from(source.openPriceAt()),
                        Timestamp.from(source.highPriceAt()),
                        Timestamp.from(source.lowPriceAt()),
                        Timestamp.from(source.closePriceAt()),
                        source.createdByUserId(),
                        Timestamp.from(source.createdAt()))
                == 1;
    }

    @Override
    public Map<Long, PriceEventSourceRecord> readCurrentVersions(List<BarVersion> versions) {
        if (versions == null || versions.size() > 500)
            throw new IllegalArgumentException("price event source batch exceeds 500");
        if (versions.isEmpty()) return Map.of();
        var args = new ArrayList<Object>();
        versions.forEach(
                version -> {
                    args.add(version.barId());
                    args.add(version.identitySha256());
                });
        String pairs = String.join(",", Collections.nCopies(versions.size(), "(?,?)"));
        return jdbc
                .query(
                        "SELECT * FROM marketdata_price_event_sources WHERE (marketdata_bar_id,bar_identity_sha256) IN ("
                                + pairs
                                + ")",
                        (rs, n) -> map(rs),
                        args.toArray())
                .stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                PriceEventSourceRecord::barId, record -> record));
    }

    private static PriceEventSourceRecord map(ResultSet rs) throws SQLException {
        return new PriceEventSourceRecord(
                rs.getObject("source_id", UUID.class),
                rs.getLong("marketdata_bar_id"),
                rs.getString("bar_identity_sha256"),
                rs.getString("source_name"),
                rs.getString("document_sha256"),
                rs.getString("document_json"),
                rs.getString("bar_snapshot_json"),
                rs.getTimestamp("open_price_at").toInstant(),
                rs.getTimestamp("high_price_at").toInstant(),
                rs.getTimestamp("low_price_at").toInstant(),
                rs.getTimestamp("close_price_at").toInstant(),
                rs.getLong("created_by_user_id"),
                rs.getTimestamp("created_at").toInstant());
    }
}
