package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.number;
import static org.junit.jupiter.api.Assertions.*;

/** PG统计从另一个数据库读取，测量连接不进入被测数据库事务总量。 */
@EnabledIfSystemProperty(named = "nq.l6.transactions", matches = "true")
class L6TransactionAccountingPostgresTest {
    @Test void realPostgresAmplificationTripsFrozenGuard() throws Exception {
        var json = new ObjectMapper(); var rows = json.createArrayNode();
        var budget = L6HardBudgetsTest.budget(0);
        try (var pg = B0Processes.Pg.startBounded(); var admin = DriverManager.getConnection(pg.ownedUrl(), "postgres", "")) {
            String database = "l6_runaway_" + UUID.randomUUID().toString().replace("-", "");
            try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE " + database); }
            long started = System.nanoTime(); budget.check(0, 0);
            boolean rejected = false;
            for (int sample = 1; sample <= 6 && !rejected; sample++) {
                try (var connection = DriverManager.getConnection(pg.ownedUrl().replace("/postgres", "/" + database), "postgres", "");
                     var statement = connection.createStatement()) {
                    for (int i = 0; i < 6000; i++) try (var result = statement.executeQuery("SELECT 1")) { assertTrue(result.next()); }
                }
                long wait = started + sample * 10_000_000_000L - System.nanoTime();
                if (wait > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(wait);
                long actual = number(admin, "SELECT xact_commit+xact_rollback FROM pg_stat_database WHERE datname='" + database + "'");
                long elapsed = (System.nanoTime() - started) / 1_000_000;
                rows.addObject().put("elapsedMillis", elapsed).put("serverTransactions", actual);
                try { budget.check(elapsed, actual); }
                catch (IllegalStateException failure) { assertTrue(failure.getMessage().contains("RATE_AMPLIFICATION")); rejected = true; }
            }
            assertTrue(rejected); assertTrue(budget.evidence().path("transactionHardCap").asLong() > 100_000);
        } finally {
            var proof = json.createObjectNode(); proof.set("samples", rows); proof.set("guard", budget.evidence());
            var path = B0Processes.root().resolve("backend/nq-app/target/l6-real-runaway-" + UUID.randomUUID() + ".json");
            Files.writeString(path, json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            System.out.println("L6_REAL_RUNAWAY " + path);
        }
    }
    @Test void jdbcTransactionBoundariesAndInternalCommands() throws Exception {
        var json = new ObjectMapper(); var rows = json.createArrayNode();
        try (var pg = B0Processes.Pg.startBounded(); var admin = DriverManager.getConnection(pg.ownedUrl(), "postgres", "")) {
            for (String mode : new String[]{"EMPTY", "AUTOCOMMIT", "ONE_TRANSACTION", "ISOLATION", "VALIDATE", "ROLLBACK"}) {
                String database = "l6_tx_" + UUID.randomUUID().toString().replace("-", "");
                try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE " + database); }
                var before = L6TransactionAccounting.snapshot();
                var properties = new Properties(); properties.setProperty("user", "postgres");
                try (var connection = new L6AccountingDriver().connect(pg.ownedUrl().replace("/postgres", "/" + database), properties)) {
                    L6TransactionAccounting.within("JDBC_BOUNDARY_TEST", () -> {
                        if (mode.equals("ONE_TRANSACTION") || mode.equals("ROLLBACK")) connection.setAutoCommit(false);
                        for (int i = 0; i < 100; i++) {
                            if (mode.equals("EMPTY")) break;
                            if (mode.equals("ISOLATION")) { connection.getTransactionIsolation(); continue; }
                            if (mode.equals("VALIDATE")) { assertTrue(connection.isValid(2)); continue; }
                            try (var statement = connection.createStatement()) {
                                try (var result = statement.executeQuery("SELECT 1")) { assertTrue(result.next()); }
                            }
                        }
                        if (mode.equals("ONE_TRANSACTION")) connection.commit();
                        if (mode.equals("ROLLBACK")) connection.rollback();
                        return null;
                    });
                }
                Thread.sleep(1100);
                long actual = number(admin, "SELECT xact_commit+xact_rollback FROM pg_stat_database WHERE datname='" + database + "'");
                long counted = 0;
                var after = L6TransactionAccounting.snapshot();
                var names = after.fieldNames();
                while (names.hasNext()) { String key = names.next(); counted += after.path(key).asLong() - before.path(key).asLong(); }
                rows.addObject().put("mode", mode).put("server", actual).put("counted", counted).put("unclassified", actual - counted);
                assertEquals(actual, counted, rows.toString());
            }
        } finally {
            var path = B0Processes.root().resolve("backend/nq-app/target/l6-jdbc-boundaries-" + UUID.randomUUID() + ".json");
            Files.writeString(path, json.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
            System.out.println("L6_JDBC_BOUNDARIES " + path);
        }
    }
}
