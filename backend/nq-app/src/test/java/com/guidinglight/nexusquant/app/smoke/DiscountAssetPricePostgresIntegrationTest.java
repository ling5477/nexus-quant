package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.application.service.DiscountAssetPriceService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcBarProvenanceReadPort;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcDiscountAssetPriceSnapshotRepository;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcHistoricalMarketDataPort;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcMarketdataBarRepository;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcMarketdataDatasetRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 仅在随机自建PostgreSQL库升级并验证持久来源；不读取验收库，不访问真实供应方，不形成资金事实。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class DiscountAssetPricePostgresIntegrationTest {
    private static ReleaseBaselineTestDatabase database;
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource source;
    private static JdbcMarketdataDatasetRepository datasets;
    private static DiscountAssetPriceService service;
    private static TransactionTemplate repeatableRead;
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FROM = START.plusSeconds(300),
            TO = START.plusSeconds(600),
            FROZEN = TO.plusSeconds(60);
    private static long owner;
    private static int scenarioNumber;

    @BeforeAll
    static void prepare() throws Exception {
        database = ReleaseBaselineTestDatabase.create();
        Flyway old = migration("13");
        old.migrate();
        old.validate();
        source = database.source();
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
        owner =
                jdbc.queryForObject(
                        "INSERT INTO users(username,password_hash) VALUES (?,'synthetic-noncredential') RETURNING id",
                        Long.class,
                        "discount-price-" + UUID.randomUUID());
        String before =
                jdbc.queryForObject(
                        "SELECT md5(row_to_json(u)::text) FROM users u WHERE id=?",
                        String.class,
                        owner);
        Flyway current = migration("15");
        assertTrue(current.migrate().migrationsExecuted >= 1);
        current.validate();
        assertEquals(0, current.migrate().migrationsExecuted);
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT md5(row_to_json(u)::text) FROM users u WHERE id=?",
                        String.class,
                        owner));
        datasets = new JdbcMarketdataDatasetRepository(jdbc, new ObjectMapper(), null);
        service = reader();
        repeatableRead = new TransactionTemplate(new DataSourceTransactionManager(source));
        repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        repeatableRead.setTimeout(30);
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void shouldFreezeActualVisibilityAndRereadAfterSourceRevision() {
        Scenario scenario = seed(1);
        DiscountAssetPriceSnapshot snapshot = freeze(scenario);
        assertEquals(new BigDecimal("304"), snapshot.priceAt(scenario.from()).quotePerAsset());
        assertEquals(
                scenario.from().plusSeconds(1),
                snapshot.priceAt(scenario.from().plusSeconds(1)).visibleAt());
        assertEquals(
                scenario.from(), snapshot.priceAt(scenario.from().plusSeconds(1)).observedAt());
        jdbc.update(
                "UPDATE marketdata_bars SET close_price=777,open_price=777,high_price=777,low_price=777 WHERE exchange_code='BINANCE' AND symbol='BNB-USDT' AND open_time=?",
                Timestamp.from(scenario.start().plusSeconds(180)));
        // 新repository/连接不重取行情；已冻结消费仍保留原版本。
        DiscountAssetPriceSnapshot restarted =
                reader().read(owner, snapshot.snapshotId(), snapshot.sourceSha256());
        assertEquals(snapshot.sourceJson(), restarted.sourceJson());
        assertEquals(snapshot.points(), restarted.points());
        assertEquals(new BigDecimal("304"), restarted.priceAt(scenario.from()).quotePerAsset());
        DiscountAssetPriceSnapshot next = freeze(scenario);
        assertNotEquals(snapshot.sourceSha256(), next.sourceSha256());
        assertEquals(new BigDecimal("777"), next.priceAt(scenario.from()).quotePerAsset());
    }

    @Test
    void shouldEnforceOwnerContentIdentityAndImmutabilityInPostgres() {
        DiscountAssetPriceSnapshot snapshot = freeze(seed(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.read(owner + 10_000, snapshot.snapshotId(), snapshot.sourceSha256()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.read(owner, snapshot.snapshotId(), "a".repeat(64)));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE discount_asset_price_snapshots SET source_json=source_json WHERE snapshot_id=?",
                                snapshot.snapshotId()));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "DELETE FROM discount_asset_price_snapshots WHERE snapshot_id=?",
                                snapshot.snapshotId()));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                """
                INSERT INTO discount_asset_price_snapshots SELECT ?,dataset_id,owner_user_id,exchange_code,asset_currency,
                    quote_currency,coverage_from,coverage_to,frozen_at,point_count,source_sha256,source_json
                FROM discount_asset_price_snapshots WHERE snapshot_id=?
                """,
                                UUID.randomUUID(),
                                snapshot.snapshotId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trades", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class));
    }

    @Test
    void shouldRejectLatePriceWithoutPretendingItWasVisibleAtClose() {
        Scenario scenario = seed(1);
        jdbc.update(
                "UPDATE marketdata_bars SET available_at=? WHERE exchange_code='BINANCE' AND symbol='BNB-USDT' AND open_time>=? AND close_time<=?",
                Timestamp.from(scenario.to().plusSeconds(30)),
                Timestamp.from(scenario.start()),
                Timestamp.from(scenario.to()));
        DiscountAssetPriceSnapshot snapshot = freeze(scenario);
        assertEquals(
                "FEE_DISCOUNT_PRICE_UNAVAILABLE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> snapshot.priceAt(scenario.from()))
                        .getMessage());
    }

    @Test
    void shouldRollbackSourceInsertAndRejectMissingBarOrWrongDataset() {
        Scenario scenario = seed(1);
        int before =
                jdbc.queryForObject(
                        "SELECT count(*) FROM discount_asset_price_snapshots", Integer.class);
        assertThrows(
                IllegalStateException.class,
                () ->
                        repeatableRead.execute(
                                status -> {
                                    service.freeze(
                                            owner,
                                            scenario.dataset().datasetId(),
                                            "BINANCE",
                                            "BNB",
                                            "USDT",
                                            scenario.from(),
                                            scenario.to(),
                                            scenario.frozen());
                                    throw new IllegalStateException(
                                            "synthetic failure after source insert");
                                }));
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT count(*) FROM discount_asset_price_snapshots", Integer.class));
        jdbc.update(
                "DELETE FROM marketdata_bars WHERE exchange_code='BINANCE' AND symbol='BNB-USDT' AND open_time=?",
                Timestamp.from(scenario.start().plusSeconds(240)));
        assertThrows(IllegalArgumentException.class, () -> freeze(scenario));
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT count(*) FROM discount_asset_price_snapshots", Integer.class));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.freeze(
                                owner,
                                scenario.dataset().datasetId(),
                                "OKX",
                                "BNB",
                                "USDT",
                                scenario.from(),
                                scenario.to(),
                                scenario.frozen()));
    }

    @Test
    void shouldRejectMalformedPointEvenWhenItsWholeTextDigestIsRecomputed() throws Exception {
        DiscountAssetPriceSnapshot original = freeze(seed(1));
        for (String fault :
                List.of(
                        "NON_FINITE_PRICE",
                        "VISIBLE_BEFORE_OBSERVED",
                        "RAW_HASH_DRIFT",
                        "NANOSECOND_TIME")) {
            ObjectNode changed = (ObjectNode) new ObjectMapper().readTree(original.sourceJson());
            UUID id = UUID.randomUUID();
            changed.put("snapshotId", id.toString());
            ObjectNode point = (ObjectNode) changed.path("points").get(3);
            switch (fault) {
                case "NON_FINITE_PRICE" -> point.put("closePrice", "NaN");
                case "VISIBLE_BEFORE_OBSERVED" ->
                        point.put(
                                "visibleAt",
                                Instant.parse(point.path("observedAt").asText())
                                        .minusSeconds(1)
                                        .toString());
                case "RAW_HASH_DRIFT" -> point.put("rawPayloadSha256", "a".repeat(64));
                case "NANOSECOND_TIME" ->
                        point.put(
                                "visibleAt",
                                Instant.parse(point.path("visibleAt").asText())
                                        .plusNanos(1)
                                        .toString());
                default -> throw new IllegalArgumentException("unknown synthetic fault");
            }
            String raw = changed.toString();
            // 重新计算SHA仍不能使错误价格或时间成为已验证来源；CHECK必须拒绝整行。
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    """
                    INSERT INTO discount_asset_price_snapshots(snapshot_id,dataset_id,owner_user_id,exchange_code,asset_currency,
                        quote_currency,coverage_from,coverage_to,frozen_at,point_count,source_sha256,source_json)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                                    id,
                                    original.datasetId(),
                                    owner,
                                    "BINANCE",
                                    "BNB",
                                    "USDT",
                                    Timestamp.from(original.from()),
                                    Timestamp.from(original.to()),
                                    Timestamp.from(original.frozenAt()),
                                    original.points().size(),
                                    DiscountAssetPriceSnapshot.sha256(raw),
                                    raw),
                    fault);
        }
    }

    private static Scenario seed(int delay) {
        // 正式dataset scope具有唯一约束；每个场景使用独立窗口，不能通过删约束来方便夹具。
        Instant start = START.plusSeconds(++scenarioNumber * 3600L),
                from = start.plusSeconds(300),
                to = start.plusSeconds(600),
                frozen = to.plusSeconds(60);
        List<HistoricalBar> bars = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Instant open = start.plusSeconds(index * 60L), close = open.plusSeconds(60);
            BigDecimal price = BigDecimal.valueOf(301L + index);
            bars.add(
                    new HistoricalBar(
                            "BINANCE",
                            "SPOT",
                            "BNB-USDT",
                            BarInterval.ONE_MINUTE,
                            open,
                            close,
                            price,
                            price,
                            price,
                            price,
                            BigDecimal.ONE,
                            BigDecimal.TEN,
                            1L,
                            "OK",
                            "{\"synthetic\":true}",
                            close.plusSeconds(delay)));
        }
        new JdbcMarketdataBarRepository(jdbc).upsertBars(bars, "FIXTURE_SYNC", frozen);
        MarketdataDataset dataset =
                new MarketdataDataset(
                        UUID.randomUUID(),
                        "Isolated BNB source",
                        "BINANCE",
                        "SPOT",
                        "BNB-USDT",
                        BarInterval.ONE_MINUTE,
                        start,
                        to,
                        MarketdataDatasetStatus.READY,
                        MarketdataQualityStatus.OK,
                        10,
                        0,
                        "marketdata_bars",
                        "synthetic",
                        frozen,
                        frozen,
                        "{\"synthetic\":true}");
        datasets.insert(dataset);
        return new Scenario(dataset, start, from, to, frozen);
    }

    private static DiscountAssetPriceSnapshot freeze(Scenario scenario) {
        return repeatableRead.execute(
                status ->
                        service.freeze(
                                owner,
                                scenario.dataset().datasetId(),
                                "BINANCE",
                                "BNB",
                                "USDT",
                                scenario.from(),
                                scenario.to(),
                                scenario.frozen()));
    }

    private record Scenario(
            MarketdataDataset dataset, Instant start, Instant from, Instant to, Instant frozen) {}

    private static DiscountAssetPriceService reader() {
        JdbcTemplate connection = new JdbcTemplate(source);
        return new DiscountAssetPriceService(
                new JdbcMarketdataDatasetRepository(connection, new ObjectMapper(), null),
                new JdbcHistoricalMarketDataPort(connection),
                new JdbcDiscountAssetPriceSnapshotRepository(connection),
                new JdbcBarProvenanceReadPort(connection));
    }

    private static Flyway migration(String target) {
        return Flyway.configure()
                .dataSource(database.source())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .target(target)
                .load();
    }
}
