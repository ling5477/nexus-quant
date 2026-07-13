package com.guidinglight.nexusquant.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.OkxVenueRuleContract;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleChecksumCalculator;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcInstrumentCatalogRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * VenueRuleFactsPostgresIntegrationTest 在显式本地 disposable PostgreSQL 上验证 V1->V34、V33->V34 和
 * repository UPSERT。测试强制 localhost/127.0.0.1/::1，且只创建并删除随机 gatew3_* schema；默认 Maven
 * 未提供 properties 时跳过，绝不连接生产数据库。
 */
class VenueRuleFactsPostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.venue-rules.postgres.required";
    private static final String URL_PROPERTY = "nq.venue-rules.postgres.url";
    private static final String USER_PROPERTY = "nq.venue-rules.postgres.user";
    private static final String PASSWORD_PROPERTY = "nq.venue-rules.postgres.password";

    @Test
    void freshDatabaseShouldMigrateFromV1ToV34AndSupportRepositoryLifecycle() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("fresh");
        long startedNanos = System.nanoTime();
        try {
            migrate(config, schema, null);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
            JdbcTemplate jdbc = jdbc(config, schema);

            assertEquals("34", currentFlywayVersion(jdbc));
            assertColumnPrecision(jdbc);
            assertConstraintsCommentsAndExistingLookupIndexes(jdbc);
            assertRepositoryLifecycle(jdbc);
            System.out.printf(
                    Locale.ROOT,
                    "gatew3_postgres_migration path=V1-V34 local_disposable=true elapsed_ms=%d flyway_version=34%n",
                    elapsed.toMillis()
            );
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    void existingV33DatabaseShouldUpgradeToV34WithoutBackfillingLegacyFacts() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("upgrade");
        try {
            migrate(config, schema, MigrationVersion.fromVersion("33"));
            JdbcTemplate jdbc = jdbc(config, schema);
            jdbc.update(
                    """
                            INSERT INTO instrument_catalog (
                                exchange_code, instrument_type, exchange_symbol, internal_symbol,
                                base_asset, quote_asset, status, tick_size, step_size, min_quantity,
                                source, synced_at, created_at, updated_at
                            ) VALUES ('OKX', 'SPOT', 'LEGACY-USDT', 'LEGACY-USDT', 'LEGACY', 'USDT', 'LIVE',
                                      0.000000000001, 0.000000000001, 0.000000000001,
                                      'OKX_INSTRUMENTS_CACHE', TIMESTAMPTZ '2026-07-13 10:00:00Z',
                                      TIMESTAMPTZ '2026-07-13 10:00:00Z', TIMESTAMPTZ '2026-07-13 10:00:00Z')
                            """
            );
            String relationFileBefore = relationFile(jdbc);
            long tableBytesBefore = tableBytes(jdbc);
            long startedNanos = System.nanoTime();

            migrate(config, schema, null);

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
            JdbcTemplate upgraded = jdbc(config, schema);
            String relationFileAfter = relationFile(upgraded);
            assertEquals("34", currentFlywayVersion(upgraded));
            assertEquals(new BigDecimal("0.000000000001000000"), upgraded.queryForObject(
                    "SELECT tick_size FROM instrument_catalog WHERE exchange_symbol = 'LEGACY-USDT'",
                    BigDecimal.class
            ));
            for (String column : List.of(
                    "max_limit_quantity",
                    "max_market_size",
                    "max_market_size_unit",
                    "max_limit_notional_usd",
                    "max_market_notional_usd",
                    "source_schema_version",
                    "observed_at",
                    "next_rule_effective_at",
                    "rule_checksum"
            )) {
                assertNull(upgraded.queryForObject(
                        "SELECT " + column + " FROM instrument_catalog WHERE exchange_symbol = 'LEGACY-USDT'",
                        Object.class
                ), "legacy field must remain null: " + column);
            }
            assertConstraintRejections(upgraded);
            System.out.printf(
                    Locale.ROOT,
                    "gatew3_postgres_migration path=V33-V34 local_disposable=true elapsed_ms=%d "
                            + "flyway_version=34 rows=1 table_bytes_before=%d relation_file_changed=%s%n",
                    elapsed.toMillis(),
                    tableBytesBefore,
                    !relationFileBefore.equals(relationFileAfter)
            );
        } finally {
            dropSchema(config, schema);
        }
    }

    private static void assertRepositoryLifecycle(JdbcTemplate jdbc) {
        JdbcInstrumentCatalogRepository repository = new JdbcInstrumentCatalogRepository(jdbc);
        Instant firstObservedAt = Instant.parse("2026-07-13T10:00:00Z");
        InstrumentCatalogItem first = checksummed(item("LIVE", "0.100000000000000001", firstObservedAt));

        InstrumentCatalogUpsertStats inserted = repository.upsertVenueRuleFacts(
                List.of(first),
                firstObservedAt.plusSeconds(1)
        );
        InstrumentCatalogItem firstRead = repository.findByExchangeAndSymbols("OKX", List.of("BTC-USDT")).getFirst();
        assertEquals(1, inserted.insertedCount());
        assertEquals(first.tickSize(), firstRead.tickSize());
        assertEquals(first.ruleChecksum(), firstRead.ruleChecksum());

        Instant refreshedObservedAt = firstObservedAt.plusSeconds(60);
        InstrumentCatalogItem refreshed = checksummed(item("LIVE", "0.100000000000000001", refreshedObservedAt));
        assertEquals(first.ruleChecksum(), refreshed.ruleChecksum());
        InstrumentCatalogUpsertStats refreshedStats = repository.upsertVenueRuleFacts(
                List.of(refreshed),
                refreshedObservedAt.plusSeconds(1)
        );
        InstrumentCatalogItem refreshedRead = repository.findByExchangeAndSymbols("OKX", List.of("BTC-USDT"))
                .getFirst();
        assertEquals(1, refreshedStats.updatedCount());
        assertEquals(refreshedObservedAt, refreshedRead.observedAt());

        InstrumentCatalogItem changed = checksummed(item("LIVE", "0.2", refreshedObservedAt.plusSeconds(60)));
        assertNotEquals(refreshed.ruleChecksum(), changed.ruleChecksum());
        repository.upsertVenueRuleFacts(List.of(changed), changed.observedAt().plusSeconds(1));
        InstrumentCatalogItem changedRead = repository.findByExchangeAndSymbols("OKX", List.of("BTC-USDT"))
                .getFirst();
        assertEquals(new BigDecimal("0.200000000000000000"), changedRead.tickSize());
        assertEquals(changed.ruleChecksum(), changedRead.ruleChecksum());

        InstrumentCatalogItem suspended = checksummed(item(
                "SUSPEND",
                "0.2",
                changed.observedAt().plusSeconds(60)
        ));
        repository.upsertVenueRuleFacts(List.of(suspended), suspended.observedAt().plusSeconds(1));
        assertEquals("SUSPEND", repository.findByExchangeAndSymbols("OKX", List.of("BTC-USDT"))
                .getFirst().status());
        assertThrows(IllegalArgumentException.class, () -> repository.findByExchangeAndSymbols(
                "OKX", List.of("A-USDT", "B-USDT", "C-USDT", "D-USDT")
        ));
    }

    private static void assertConstraintRejections(JdbcTemplate jdbc) {
        jdbc.update(
                """
                        INSERT INTO instrument_catalog (
                            exchange_code, instrument_type, exchange_symbol, internal_symbol,
                            base_asset, quote_asset, status, tick_size, step_size, min_quantity,
                            max_limit_quantity, max_market_size, max_market_size_unit,
                            max_limit_notional_usd, max_market_notional_usd,
                            source, source_schema_version, observed_at, synced_at,
                            next_rule_effective_at, rule_checksum, created_at, updated_at
                        ) VALUES (
                            'OKX', 'SPOT', 'VALID-USDT', 'VALID-USDT', 'VALID', 'USDT', 'LIVE',
                            0.1, 0.001, 0.001, 100, 100000, 'USDT', 1000000, 1000000,
                            'OKX_PUBLIC_INSTRUMENTS', 'NQ_OKX_VENUE_RULE_FACTS_V1',
                            TIMESTAMPTZ '2026-07-13 10:00:00Z', TIMESTAMPTZ '2026-07-13 10:00:01Z',
                            TIMESTAMPTZ '2026-07-13 11:00:00Z', repeat('a', 64),
                            TIMESTAMPTZ '2026-07-13 10:00:01Z', TIMESTAMPTZ '2026-07-13 10:00:01Z'
                        )
                        """
        );
        for (String column : List.of(
                "tick_size",
                "step_size",
                "min_quantity",
                "max_limit_quantity",
                "max_market_size",
                "max_limit_notional_usd",
                "max_market_notional_usd"
        )) {
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE instrument_catalog SET " + column + " = 0 WHERE exchange_symbol = 'VALID-USDT'"
            ), "zero must be rejected: " + column);
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE instrument_catalog SET " + column + " = -1 WHERE exchange_symbol = 'VALID-USDT'"
            ), "negative must be rejected: " + column);
        }
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE instrument_catalog SET rule_checksum = 'ABC' WHERE exchange_symbol = 'VALID-USDT'"
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE instrument_catalog SET max_market_size_unit = NULL WHERE exchange_symbol = 'VALID-USDT'"
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE instrument_catalog SET observed_at = synced_at + INTERVAL '1 second' "
                        + "WHERE exchange_symbol = 'VALID-USDT'"
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE instrument_catalog SET next_rule_effective_at = observed_at "
                        + "WHERE exchange_symbol = 'VALID-USDT'"
        ));
    }

    private static void assertColumnPrecision(JdbcTemplate jdbc) {
        for (String column : List.of("tick_size", "step_size", "min_quantity")) {
            assertEquals(38, jdbc.queryForObject(
                    """
                            SELECT numeric_precision
                            FROM information_schema.columns
                            WHERE table_schema = current_schema()
                              AND table_name = 'instrument_catalog'
                              AND column_name = ?
                            """,
                    Integer.class,
                    column
            ));
            assertEquals(18, jdbc.queryForObject(
                    """
                            SELECT numeric_scale
                            FROM information_schema.columns
                            WHERE table_schema = current_schema()
                              AND table_name = 'instrument_catalog'
                              AND column_name = ?
                            """,
                    Integer.class,
                    column
            ));
        }
    }

    private static void assertConstraintsCommentsAndExistingLookupIndexes(JdbcTemplate jdbc) {
        for (String constraint : List.of(
                "chk_instrument_catalog_tick_size_positive",
                "chk_instrument_catalog_step_size_positive",
                "chk_instrument_catalog_min_quantity_positive",
                "chk_instrument_catalog_max_limit_quantity_positive",
                "chk_instrument_catalog_max_market_size_unit",
                "chk_instrument_catalog_max_limit_notional_positive",
                "chk_instrument_catalog_max_market_notional_positive",
                "chk_instrument_catalog_rule_checksum",
                "chk_instrument_catalog_observed_before_synced",
                "chk_instrument_catalog_next_rule_after_observed",
                "uq_instrument_catalog_exchange_symbol",
                "uq_instrument_catalog_exchange_internal_symbol"
        )) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?",
                    Integer.class,
                    constraint
            ));
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() "
                        + "AND indexname = 'idx_instrument_catalog_exchange_status_symbol'",
                Integer.class
        ));
        for (String column : List.of(
                "max_limit_quantity",
                "max_market_size",
                "max_market_size_unit",
                "max_limit_notional_usd",
                "max_market_notional_usd",
                "source_schema_version",
                "observed_at",
                "next_rule_effective_at",
                "rule_checksum"
        )) {
            assertEquals(Boolean.TRUE, jdbc.queryForObject(
                    """
                            SELECT col_description(to_regclass('instrument_catalog'), ordinal_position) IS NOT NULL
                            FROM information_schema.columns
                            WHERE table_schema = current_schema()
                              AND table_name = 'instrument_catalog'
                              AND column_name = ?
                            """,
                    Boolean.class,
                    column
            ));
        }
    }

    private static InstrumentCatalogItem checksummed(InstrumentCatalogItem item) {
        String checksum = new VenueRuleChecksumCalculator().calculate(item);
        return new InstrumentCatalogItem(
                item.instrumentId(), item.exchangeCode(), item.instrumentType(), item.exchangeSymbol(),
                item.internalSymbol(), item.baseAsset(), item.quoteAsset(), item.status(), item.tickSize(),
                item.stepSize(), item.minQuantity(), item.maxLimitQuantity(), item.maxMarketSize(),
                item.maxMarketSizeUnit(), item.maxLimitNotionalUsd(), item.maxMarketNotionalUsd(), item.source(),
                item.sourceSchemaVersion(), item.observedAt(), item.syncedAt(), item.nextRuleEffectiveAt(), checksum,
                item.createdAt(), item.updatedAt()
        );
    }

    private static InstrumentCatalogItem item(String status, String tickSize, Instant observedAt) {
        return new InstrumentCatalogItem(
                null,
                "OKX",
                "SPOT",
                "BTC-USDT",
                "BTC-USDT",
                "BTC",
                "USDT",
                status,
                new BigDecimal(tickSize),
                new BigDecimal("0.000000000000000001"),
                new BigDecimal("0.000000000000000001"),
                new BigDecimal("100"),
                new BigDecimal("100000"),
                "USDT",
                new BigDecimal("1000000"),
                new BigDecimal("1000000"),
                OkxVenueRuleContract.SOURCE,
                OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                observedAt,
                null,
                Instant.parse("2026-07-14T00:00:00Z"),
                null,
                null,
                null
        );
    }

    private static void migrate(PostgresConfig config, String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(config.url(), config.user(), config.password())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true);
        if (target != null) {
            configuration.target(target);
        }
        Flyway flyway = configuration.load();
        flyway.migrate();
    }

    private static JdbcTemplate jdbc(PostgresConfig config, String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(withCurrentSchema(config.url(), schema));
        dataSource.setUsername(config.user());
        dataSource.setPassword(config.password());
        return new JdbcTemplate(dataSource);
    }

    private static String withCurrentSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private static String currentFlywayVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class
        );
    }

    private static String relationFile(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT pg_relation_filepath('instrument_catalog'::regclass)", String.class);
    }

    private static long tableBytes(JdbcTemplate jdbc) {
        Long bytes = jdbc.queryForObject("SELECT pg_total_relation_size('instrument_catalog'::regclass)", Long.class);
        return bytes == null ? 0L : bytes;
    }

    private static String randomSchema(String suffix) {
        return "gatew3_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void dropSchema(PostgresConfig config, String schema) {
        if (schema == null || !schema.matches("gatew3_[a-z]+_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("refusing to drop non-GateW3 schema");
        }
        JdbcTemplate admin = jdbc(config, "public");
        admin.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
    }

    private static PostgresConfig requireLocalDisposableConfig() {
        PostgresConfig config = PostgresConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "local disposable PostgreSQL properties are not configured");
        }
        assertTrue(config.configured(), "missing required local disposable PostgreSQL properties");
        assertTrue(config.localhost(), "venue-rule PostgreSQL test refuses non-local database URLs");
        return config;
    }

    private record PostgresConfig(String url, String user, String password, boolean required) {

        private static PostgresConfig fromSystemProperties() {
            return new PostgresConfig(
                    property(URL_PROPERTY),
                    property(USER_PROPERTY),
                    property(PASSWORD_PROPERTY),
                    Boolean.parseBoolean(property(REQUIRED_PROPERTY))
            );
        }

        private boolean configured() {
            return !url.isBlank() && !user.isBlank() && !password.isBlank();
        }

        private boolean localhost() {
            try {
                URI uri = URI.create(url.substring("jdbc:".length()));
                return List.of("127.0.0.1", "localhost", "::1").contains(uri.getHost());
            } catch (RuntimeException ex) {
                return false;
            }
        }
    }

    private static String property(String name) {
        return System.getProperty(name, "").trim();
    }
}
