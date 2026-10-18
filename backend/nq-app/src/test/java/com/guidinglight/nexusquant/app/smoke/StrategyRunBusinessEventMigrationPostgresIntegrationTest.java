package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** V24只新增索引；用真实旧事件、畸形/超长载荷及锁超时证明升级不改写事件事实。 */
class StrategyRunBusinessEventMigrationPostgresIntegrationTest {
    private static final List<String> INDEXES =
            List.of(
                    "idx_event_store_order_business_identity",
                    "idx_event_store_trade_business_identity",
                    "idx_event_store_risk_business_identity",
                    "idx_event_store_ack_business_identity",
                    "idx_event_store_position_business_identity");

    @Test
    void indexUpgradeRollsBackOnLockAndPreservesEveryOldEvent() throws Exception {
        String maintenance = System.getenv("TASK_RUN_EVENTS_MIGRATION_MAINTENANCE_URL");
        assumeTrue(maintenance != null, "owned loopback PostgreSQL proof is opt-in");
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
        String user = System.getenv("TASK_RUN_EVENTS_DB_USER");
        String password = System.getenv("TASK_RUN_EVENTS_DB_PASSWORD");
        Path resultPath =
                Path.of(System.getenv("TASK_RUN_EVENTS_MIGRATION_RESULT_PATH")).toAbsolutePath();
        assertTrue(
                resultPath.startsWith(Path.of("E:/Project/nq-production-reconstruction-output")));
        assertTrue(Files.isDirectory(resultPath.getParent()) && !Files.exists(resultPath));
        String database = "run_events_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://127.0.0.1:15442/" + database;
        boolean created = false, passed = false;
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            try (Connection control = connect(maintenance, user, password)) {
                control.createStatement().execute("CREATE DATABASE " + database);
                created = true;
            }
            flyway(url, user, password, "23").migrate();
            try (Connection connection = connect(url, user, password)) {
                seedOldEvents(connection);
            }
            String before;
            try (Connection connection = connect(url, user, password)) {
                before = fingerprint(connection);
                assertEquals(0, indexCount(connection));
            }
            try (Connection lock = connect(url, user, password)) {
                lock.setAutoCommit(false);
                lock.createStatement().execute("LOCK TABLE event_store IN ACCESS EXCLUSIVE MODE");
                FlywayException rejected =
                        assertThrows(
                                FlywayException.class,
                                () -> flyway(url, user, password, "24").migrate());
                boolean lockTimeout = false;
                for (Throwable cause = rejected; cause != null; cause = cause.getCause())
                    if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState()))
                        lockTimeout = true;
                assertTrue(
                        lockTimeout, "must prove lock timeout rather than a different DDL failure");
                try (Connection check = connect(url, user, password)) {
                    assertEquals(0, indexCount(check));
                    assertEquals(
                            "23",
                            scalar(
                                    check,
                                    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
                    assertEquals(
                            "0",
                            scalar(
                                    check,
                                    "SELECT count(*)::text FROM flyway_schema_history WHERE NOT success"));
                }
                lock.rollback();
            }
            flyway(url, user, password, "24").migrate();
            flyway(url, user, password, "24").validate();
            try (Connection connection = connect(url, user, password)) {
                assertEquals(before, fingerprint(connection));
                assertEquals(5, indexCount(connection));
                for (String index : INDEXES) {
                    assertEquals(
                            "true",
                            scalar(
                                    connection,
                                    "SELECT (i.indisvalid AND i.indisready)::text FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid WHERE c.relname='"
                                            + index
                                            + "'"));
                }
                // 索引不要求新事件改字段；未知类型和超长历史身份也不因BTREE条目上限被拒绝。
                insert(
                        connection,
                        "migration-after",
                        "OrderCreated",
                        "{\"payload\":{\"order_id\":\"" + "z".repeat(6000) + "\"}}");
                assertEquals("9", scalar(connection, "SELECT count(*)::text FROM event_store"));
            }
            result.put("eventHashBeforeAfterEqual", true);
            result.put("lockTimeoutSqlState", "55P03");
            result.put("indexes", INDEXES);
            passed = true;
        } finally {
            if (passed) {
                try (Connection control = connect(maintenance, user, password)) {
                    control.createStatement().execute("DROP DATABASE " + database);
                }
            }
            result.put(
                    "status",
                    passed ? "PASS_SCOPED_V23_TO_V24_INDEX_MIGRATION" : "FAILED_RETAINED");
            result.put("database", database);
            result.put(
                    "cleanup",
                    passed ? "DROPPED_AFTER_SUCCESS" : created ? "RETAINED" : "NOT_CREATED");
            result.put(
                    "scope",
                    "event_store forward indexes only; not API, GUI or whole-site acceptance");
            Files.writeString(resultPath, new ObjectMapper().writeValueAsString(result));
        }
    }

    private static Flyway flyway(String url, String user, String password, String version) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load();
    }

    private static Connection connect(String url, String user, String password)
            throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        try {
            connection.createStatement().execute("SET TIME ZONE 'UTC'");
            connection.createStatement().execute("SET statement_timeout='15s'");
            return connection;
        } catch (SQLException failure) {
            connection.close();
            throw failure;
        }
    }

    private static void seedOldEvents(Connection connection) throws SQLException {
        insert(
                connection,
                "migration-1",
                "OrderCreated",
                "{\"payload\":{\"order_id\":\"order\",\"value\":9007199254740993.123456789012345678}}");
        insert(
                connection,
                "migration-2",
                "TradeExecuted",
                "{\"payload\":{\"trade_id\":\"trade\"}}");
        insert(
                connection,
                "migration-3",
                "RiskEventRaised",
                "{\"payload\":{\"scope\":\"ORDER\",\"scope_id\":\"order\"}}");
        insert(
                connection,
                "migration-4",
                "OrderAck",
                "{\"payload\":{\"account_id\":9007199254740993,\"venue\":\"BINANCE\",\"client_order_id\":\"client\"}}");
        insert(
                connection,
                "migration-5",
                "OrderAck",
                "{\"payload\":{\"account_id\":1,\"venue\":\"OKX\",\"external_order_id\":\"external\"}}");
        insert(connection, "migration-6", "Unknown", "[1,2,3]");
        insert(
                connection,
                "migration-7",
                "TradeExecuted",
                "{\"payload\":{\"trade_id\":\"" + "x".repeat(6000) + "\"}}");
        insert(
                connection,
                "migration-8",
                "OrderAck",
                "{\"payload\":{\"account_id\":1,\"venue\":\"OKX\",\"client_order_id\":\""
                        + "y".repeat(6000)
                        + "\"}}");
    }

    private static void insert(Connection connection, String id, String type, String json)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        "INSERT INTO event_store(event_id,topic,schema_version,event_type,payload_json,key_value,trace_id,created_at) VALUES(?,'order.event.v1',1,?,CAST(? AS jsonb),'synthetic-key','synthetic-trace','2026-10-01T00:00:00.123456Z')")) {
            statement.setString(1, id);
            statement.setString(2, type);
            statement.setString(3, json);
            statement.executeUpdate();
        }
    }

    private static String fingerprint(Connection connection) throws SQLException {
        return scalar(
                connection,
                "SELECT md5(string_agg(to_jsonb(e)::text,',' ORDER BY event_id)) FROM event_store e");
    }

    private static int indexCount(Connection connection) throws SQLException {
        return Integer.parseInt(
                scalar(
                        connection,
                        "SELECT count(*)::text FROM pg_indexes WHERE schemaname='public' AND indexname IN ('"
                                + String.join("','", INDEXES)
                                + "')"));
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }
}
