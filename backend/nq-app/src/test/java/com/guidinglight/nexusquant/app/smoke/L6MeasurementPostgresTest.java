package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.DriverManager;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 独立PG上的测量正负例；临时关系仅测试SQL测量，不作为业务资格事实。 */
@EnabledIfSystemProperty(named = "nq.l6.measurement", matches = "true")
class L6MeasurementPostgresTest {
    @Test void missingMeterRejectedAndRealTimeoutCountIncrements() throws Exception {
        var registry = new SimpleMeterRegistry();
        var unavailable = new SimpleMeterRegistry();
        try (var pg = B0Processes.Pg.startBounded()) {
            var config = new HikariConfig();
            config.setJdbcUrl(pg.ownedUrl()); config.setUsername("postgres"); config.setPassword("");
            config.setPoolName("l6-timeout-positive"); config.setMaximumPoolSize(1); config.setMinimumIdle(1);
            config.setConnectionTimeout(250); config.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
            try (var pool = new HikariDataSource(config)) {
                assertThrows(IllegalStateException.class, () -> L6Measurements.timeoutCounter(pool, unavailable));
                var counter = L6Measurements.timeoutCounter(pool, registry);
                double before = counter.count();
                try (var held = pool.getConnection()) {
                    assertEquals(1, pool.getHikariPoolMXBean().getActiveConnections());
                    assertThrows(SQLTransientConnectionException.class, pool::getConnection);
                }
                assertEquals(1, counter.count() - before);
                System.out.println("L6_HIKARI_POSITIVE metric=" + counter.getId().getName() + " start=" + before + " end=" + counter.count());
            }
            try (var connection = DriverManager.getConnection(pg.ownedUrl(), "postgres", "");
                 var source = new SingleConnectionDataSource(connection, true); var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE orders(order_id text,venue text,status text,created_at timestamptz)");
                statement.execute("CREATE TEMP TABLE trades(trade_id text,order_id text)");
                statement.execute("CREATE TEMP TABLE ledger_entries(ref_id text)");
                var jdbc = new NamedParameterJdbcTemplate(source);
                var statuses = List.of("ACCEPTED", "FILLED");
                var empty = L6Measurements.age(jdbc, statuses);
                assertEquals(0, empty.path("eligibleCandidateCount").asInt());
                assertTrue(empty.path("oldestCandidateAgeMillis").isNull());
                statement.execute("INSERT INTO orders VALUES('pending','OKX','ACCEPTED',CURRENT_TIMESTAMP-INTERVAL '2 seconds'),"
                        + "('outside','OKX','NEW',CURRENT_TIMESTAMP-INTERVAL '1 day')");
                var pending = L6Measurements.age(jdbc, statuses);
                assertEquals(1, pending.path("eligibleCandidateCount").asInt());
                assertTrue(pending.path("oldestCandidateAgeMillis").asLong() >= 2000);
                assertEquals(pending.toString(), new ObjectMapper().readTree(pending.toString()).toString());
                // 模拟测量输入集合消失；真实业务收敛由随后的readiness独立证明。
                statement.execute("DELETE FROM orders WHERE order_id='pending'");
                var drained = L6Measurements.age(jdbc, statuses);
                assertEquals(0, drained.path("eligibleCandidateCount").asInt());
                assertTrue(drained.path("oldestCandidateAgeMillis").isNull());
                assertThrows(IllegalStateException.class, () -> L6Measurements.age(jdbc, List.of()));
            }
        } finally { registry.close(); unavailable.close(); }
    }
}
