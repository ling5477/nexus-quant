package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Real two-JVM restart proof for durable trading reconciliation facts. */
class TradingRestartRecoveryPostgresIntegrationTest {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(90);
    private static final String RESULT_MARKER = "NQ_F002_RESULT ";

    @Test
    void recoversDurableTradeAfterLedgerFailureAcrossDistinctJvmProcesses() throws Exception {
        try (RestartDatabase database = RestartDatabase.create("r1")) {
            Path outputDir = outputDirectory(database.caseId());
            ChildRun processA = runChild("R1_A", database, outputDir.resolve("r1-a.log"));
            long processAObservedExit = System.currentTimeMillis();
            assertChildExited(processA);
            TargetFacts interprocessFacts = database.targetFacts(
                    processA.value("order"),
                    processA.value("tradeId")
            );
            assertEquals(database.databaseName(), interprocessFacts.databaseName());
            assertEquals("FILLED", interprocessFacts.orderStatus());
            assertEquals(1L, interprocessFacts.orderTradeCount());
            assertEquals(1L, interprocessFacts.targetTradeCount());
            assertEquals(0L, interprocessFacts.ledgerEntries());
            assertEquals(0L, interprocessFacts.ledgerEvents());
            assertDecimal("0", interprocessFacts.position());
            assertDecimal("0", interprocessFacts.account());
            assertEquals(1L, database.productionTradeCountForAccountCode("f002-r1-unrelated-" + database.caseId()));
            printInterprocessProof("R1", processA, interprocessFacts);
            System.out.println("INTERPROCESS_WINDOW_ESTABLISHED=YES scenario=R1");
            System.out.println("INTERPROCESS_DB_IDENTITY_MATCH=PASS scenario=R1");
            System.out.println("R1_INTERPROCESS_DURABILITY_CHECK=PASS");
            System.out.println("PARENT_INTERPROCESS_BUSINESS_WRITES=0 scenario=R1");

            advanceClockPast(processAObservedExit);
            ChildRun processB = runChild("R1_B", database, outputDir.resolve("r1-b.log"));

            assertProcessBoundary(processA, processB, processAObservedExit);
            assertEquals("1", processA.value("trades"));
            assertEquals("0", processA.value("ledger"));
            assertEquals("0", processA.value("position"));
            assertEquals("0", processA.value("account"));
            assertEquals("GRACEFUL_EXIT", processA.value("state"));

            assertEquals(processA.value("order"), processB.value("order"));
            assertEquals(processA.value("tradeId"), processB.value("tradeId"));
            assertEquals("1", processB.value("trades"));
            assertEquals("2", processB.value("ledger"));
            assertEquals(0, new BigDecimal("0.10000000").compareTo(processB.decimal("position")));
            assertEquals(0, new BigDecimal("0.10000000").compareTo(processB.decimal("account")));
            assertEquals("RECOVERED", processB.value("state"));
            assertEquals("false", processB.value("automatic"));

            TargetFacts recoveredFacts = database.targetFacts(processA.value("order"), processA.value("tradeId"));
            assertEquals(1L, recoveredFacts.orderTradeCount());
            assertEquals(1L, recoveredFacts.targetTradeCount());
            assertEquals(2L, recoveredFacts.ledgerEntries());
            assertEquals(2L, recoveredFacts.ledgerEvents());
            assertDecimal("0.10000000", recoveredFacts.position());
            assertDecimal("0.10000000", recoveredFacts.account());
            printProof("R1", processA, processB, processAObservedExit);
        }
    }

    @Test
    void continuesPartialFillAcrossDistinctJvmProcesses() throws Exception {
        try (RestartDatabase database = RestartDatabase.create("r2")) {
            Path outputDir = outputDirectory(database.caseId());
            ChildRun processA = runChild("R2_A", database, outputDir.resolve("r2-a.log"));
            long processAObservedExit = System.currentTimeMillis();
            assertChildExited(processA);
            String orderId = processA.value("order");
            String fillAExchangeTradeId = exchangeTradeId("A", orderId);
            String fillBExchangeTradeId = exchangeTradeId("B", orderId);
            TargetFacts interprocessFacts = database.targetFacts(orderId, processA.value("tradeId"));
            TradeLedgerFacts interprocessFillA = database.tradeLedgerFacts(orderId, fillAExchangeTradeId);
            TradeLedgerFacts interprocessFillB = database.tradeLedgerFacts(orderId, fillBExchangeTradeId);
            assertEquals(database.databaseName(), interprocessFacts.databaseName());
            assertEquals("PARTIALLY_FILLED", interprocessFacts.orderStatus());
            assertEquals(1L, interprocessFacts.orderTradeCount());
            assertEquals(1L, interprocessFillA.tradeCount());
            assertEquals(processA.value("tradeId"), interprocessFillA.tradeId());
            assertEquals(2L, interprocessFillA.ledgerEntries());
            assertEquals(2L, interprocessFillA.ledgerEvents());
            assertEquals(0L, interprocessFillB.tradeCount());
            assertEquals(0L, interprocessFillB.ledgerEntries());
            assertEquals(0L, interprocessFillB.ledgerEvents());
            assertDecimal("0.04000000", interprocessFacts.position());
            assertDecimal("0.04000000", interprocessFacts.account());
            printInterprocessProof("R2", processA, interprocessFacts);
            System.out.println("INTERPROCESS_WINDOW_ESTABLISHED=YES scenario=R2");
            System.out.println("INTERPROCESS_DB_IDENTITY_MATCH=PASS scenario=R2");
            System.out.println("R2_INTERPROCESS_DURABILITY_CHECK=PASS");
            System.out.println("PARENT_INTERPROCESS_BUSINESS_WRITES=0 scenario=R2");

            advanceClockPast(processAObservedExit);
            ChildRun processB = runChild("R2_B", database, outputDir.resolve("r2-b.log"));

            assertProcessBoundary(processA, processB, processAObservedExit);
            assertEquals("1", processA.value("trades"));
            assertEquals("2", processA.value("ledger"));
            assertEquals(0, new BigDecimal("0.04000000").compareTo(processA.decimal("position")));
            assertEquals("PARTIAL", processA.value("state"));

            assertEquals(processA.value("order"), processB.value("order"));
            assertEquals("2", processB.value("trades"));
            assertEquals("4", processB.value("ledger"));
            assertEquals(0, new BigDecimal("0.10000000").compareTo(processB.decimal("position")));
            assertEquals(0, new BigDecimal("0.10000000").compareTo(processB.decimal("account")));
            assertEquals("FILLED", processB.value("state"));

            TargetFacts finalFacts = database.targetFacts(orderId, processA.value("tradeId"));
            TradeLedgerFacts finalFillA = database.tradeLedgerFacts(orderId, fillAExchangeTradeId);
            TradeLedgerFacts finalFillB = database.tradeLedgerFacts(orderId, fillBExchangeTradeId);
            assertEquals("FILLED", finalFacts.orderStatus());
            assertEquals(2L, finalFacts.orderTradeCount());
            assertEquals(interprocessFillA, finalFillA);
            assertEquals(1L, finalFillB.tradeCount());
            assertEquals(processB.value("tradeId"), finalFillB.tradeId());
            assertEquals(2L, finalFillB.ledgerEntries());
            assertEquals(2L, finalFillB.ledgerEvents());
            assertDecimal("0.10000000", finalFacts.position());
            assertDecimal("0.10000000", finalFacts.account());
            System.out.println("R2_LEDGER_A_EXACTLY_ONCE=PASS");
            System.out.println("R2_LEDGER_B_EXACTLY_ONCE=PASS");
            System.out.println("R2_PER_FILL_LEDGER_DISTRIBUTION_PROVEN=YES");
            printProof("R2", processA, processB, processAObservedExit);
        }
    }

    private void assertChildExited(ChildRun process) {
        assertFalse(ProcessHandle.of(process.pid()).map(ProcessHandle::isAlive).orElse(false));
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private String exchangeTradeId(String suffix, String orderId) {
        return "f002-fill-" + suffix + "-f002-order-" + orderId;
    }

    private void printInterprocessProof(String scenario, ChildRun processA, TargetFacts facts) {
        System.out.printf(
                "NQ_F002_INTERPROCESS_PROOF scenario=%s pidA=%d database=%s order=%s trades=%d "
                        + "ledgerEntries=%d ledgerEvents=%d position=%s account=%s%n",
                scenario,
                processA.pid(),
                facts.databaseName(),
                facts.orderStatus(),
                facts.orderTradeCount(),
                facts.ledgerEntries(),
                facts.ledgerEvents(),
                facts.position().toPlainString(),
                facts.account().toPlainString()
        );
    }

    private void printProof(String scenario, ChildRun processA, ChildRun processB, long processAObservedExit) {
        System.out.printf(
                "NQ_F002_RESTART_PROOF scenario=%s pidA=%d pidB=%d aExit=%d bStart=%d stateA=%s stateB=%s%n",
                scenario,
                processA.pid(),
                processB.pid(),
                processAObservedExit,
                processB.longValue("start"),
                processA.value("state"),
                processB.value("state")
        );
    }

    private ChildRun runChild(String phase, RestartDatabase database, Path logFile) throws Exception {
        Files.createDirectories(logFile.getParent());
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
        ).toString();
        String classpath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path")
        );
        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable,
                "-cp",
                classpath,
                TradingRestartRecoveryProcessMain.class.getName()
        );
        builder.redirectErrorStream(true);
        builder.redirectOutput(logFile.toFile());
        builder.environment().put("NQ_F002_PHASE", phase);
        builder.environment().put("NQ_F002_CASE_ID", database.caseId());
        builder.environment().put("NQ_F002_DB_URL", database.databaseUrl());
        builder.environment().put("NQ_F002_DB_USER", database.user());
        builder.environment().put("NQ_F002_DB_PASSWORD", database.password());
        // Spring environment variables outrank builder default properties in the child JVM.
        builder.environment().put("SPRING_DATASOURCE_URL", database.databaseUrl());
        builder.environment().put("SPRING_DATASOURCE_USERNAME", database.user());
        builder.environment().put("SPRING_DATASOURCE_PASSWORD", database.password());

        Process process = builder.start();
        try {
            boolean exited = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
                throw new AssertionError("forked restart process timed out, phase=" + phase);
            }
            String output = Files.readString(logFile);
            assertEquals(0, process.exitValue(), () -> "forked process failed: " + output);
            String marker = Arrays.stream(output.split("\\R"))
                    .filter(line -> line.startsWith(RESULT_MARKER))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new AssertionError("forked result marker missing: " + output));
            Map<String, String> values = parseMarker(marker.substring(RESULT_MARKER.length()));
            assertEquals(Long.toString(process.pid()), values.get("pid"));
            return new ChildRun(process.pid(), values);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private void assertProcessBoundary(ChildRun processA, ChildRun processB, long processAObservedExit) {
        assertNotEquals(processA.pid(), processB.pid());
        assertTrue(processA.longValue("exit") <= processAObservedExit);
        assertTrue(processAObservedExit < processB.longValue("start"));
        assertFalse(processA.value("order").isBlank());
        assertFalse(processB.value("order").isBlank());
    }

    private Map<String, String> parseMarker(String marker) {
        Map<String, String> values = new HashMap<>();
        for (String token : marker.trim().split("\\s+")) {
            int separator = token.indexOf('=');
            if (separator > 0) {
                values.put(token.substring(0, separator), token.substring(separator + 1));
            }
        }
        return values;
    }

    private void advanceClockPast(long timestamp) {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (System.currentTimeMillis() <= timestamp && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(System.currentTimeMillis() > timestamp, "wall clock did not advance before Process B launch");
    }

    private Path outputDirectory(String caseId) throws IOException {
        Path target = Path.of("target", "f002-restart", caseId).toAbsolutePath().normalize();
        Path expectedRoot = Path.of("target", "f002-restart").toAbsolutePath().normalize();
        if (!target.startsWith(expectedRoot)) {
            throw new IOException("unsafe restart output directory");
        }
        Files.createDirectories(target);
        return target;
    }

    record ChildRun(long pid, Map<String, String> values) {
        String value(String key) {
            String value = values.get(key);
            if (value == null) {
                throw new AssertionError("missing child result field: " + key);
            }
            return value;
        }

        long longValue(String key) {
            return Long.parseLong(value(key));
        }

        BigDecimal decimal(String key) {
            return new BigDecimal(value(key));
        }
    }

    record TargetFacts(
            String databaseName,
            String orderStatus,
            long orderTradeCount,
            long targetTradeCount,
            long ledgerEntries,
            long ledgerEvents,
            BigDecimal position,
            BigDecimal account
    ) {
    }

    record TradeLedgerFacts(String tradeId, long tradeCount, long ledgerEntries, long ledgerEvents) {
    }

    record OrderIdentity(Long accountId, String status) {
    }

    static final class RestartDatabase implements AutoCloseable {
        private final String caseId;
        private final String maintenanceUrl;
        private final String databaseUrl;
        private final String user;
        private final String password;
        private final String databaseName;

        private RestartDatabase(
                String caseId,
                String maintenanceUrl,
                String databaseUrl,
                String user,
                String password,
                String databaseName
        ) {
            this.caseId = caseId;
            this.maintenanceUrl = maintenanceUrl;
            this.databaseUrl = databaseUrl;
            this.user = user;
            this.password = password;
            this.databaseName = databaseName;
        }

        static RestartDatabase create(String scenario) throws Exception {
            String baseUrl = requiredEnvironment("SPRING_DATASOURCE_URL");
            String user = requiredEnvironment("SPRING_DATASOURCE_USERNAME");
            String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
            String caseId = scenario + "_" + UUID.randomUUID().toString().replace("-", "");
            String databaseName = "nq_f002_" + caseId;
            String maintenanceUrl = replaceDatabase(baseUrl, "postgres");
            String databaseUrl = replaceDatabase(baseUrl, databaseName);
            boolean created = false;
            try {
                try (Connection connection = DriverManager.getConnection(maintenanceUrl, user, password);
                        Statement statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE " + databaseName);
                    created = true;
                }
                Flyway flyway = Flyway.configure()
                        .dataSource(databaseUrl, user, password)
                        .locations("classpath:db/migration")
                        .baselineOnMigrate(false)
                        .cleanDisabled(true)
                        .load();
                flyway.migrate();
                flyway.validate();
                assertEquals("46", flyway.info().current().getVersion().getVersion());
                return new RestartDatabase(caseId, maintenanceUrl, databaseUrl, user, password, databaseName);
            } catch (Exception | AssertionError ex) {
                if (created) {
                    try {
                        dropDatabase(maintenanceUrl, user, password, databaseName);
                    } catch (Exception cleanupFailure) {
                        ex.addSuppressed(cleanupFailure);
                    }
                }
                throw ex;
            }
        }

        TargetFacts targetFacts(String orderId, String tradeId) throws SQLException {
            try (Connection connection = DriverManager.getConnection(databaseUrl, user, password)) {
                String actualDatabase = queryString(connection, "SELECT current_database()");
                OrderIdentity order = orderIdentity(connection, orderId);
                return new TargetFacts(
                        actualDatabase,
                        order.status(),
                        queryLong(connection, "SELECT COUNT(*) FROM trades WHERE order_id=?", orderId),
                        queryLong(connection, "SELECT COUNT(*) FROM trades WHERE order_id=? AND trade_id=?", orderId, tradeId),
                        queryLong(
                                connection,
                                "SELECT COUNT(*) FROM ledger_entries WHERE ref_type='TRADE' AND ref_id=?",
                                tradeId
                        ),
                        queryLong(
                                connection,
                                "SELECT COUNT(*) FROM ledger_events event "
                                        + "JOIN ledger_entries entry ON entry.entry_id=event.entry_id "
                                        + "WHERE entry.ref_type='TRADE' AND entry.ref_id=?",
                                tradeId
                        ),
                        queryDecimal(
                                connection,
                                "SELECT COALESCE(MAX(qty),0) FROM positions WHERE account_id=? AND symbol='BTC-USDT'",
                                order.accountId()
                        ),
                        queryDecimal(
                                connection,
                                "SELECT COALESCE((SELECT balance FROM account_snapshots "
                                        + "WHERE account_id=? AND currency='BTC' "
                                        + "ORDER BY ts DESC,snapshot_id DESC LIMIT 1),0)",
                                order.accountId()
                        )
                );
            }
        }

        TradeLedgerFacts tradeLedgerFacts(String orderId, String exchangeTradeId) throws SQLException {
            try (Connection connection = DriverManager.getConnection(databaseUrl, user, password);
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT COUNT(*),MIN(trade_id) FROM trades "
                                    + "WHERE order_id=? AND exchange='OKX' AND exchange_trade_id=?"
                    )) {
                statement.setString(1, orderId);
                statement.setString(2, exchangeTradeId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    long tradeCount = resultSet.getLong(1);
                    String tradeId = resultSet.getString(2);
                    if (tradeId == null) {
                        return new TradeLedgerFacts(null, tradeCount, 0, 0);
                    }
                    long ledgerEntries = queryLong(
                            connection,
                            "SELECT COUNT(*) FROM ledger_entries WHERE ref_type='TRADE' AND ref_id=?",
                            tradeId
                    );
                    long ledgerEvents = queryLong(
                            connection,
                            "SELECT COUNT(*) FROM ledger_events event "
                                    + "JOIN ledger_entries entry ON entry.entry_id=event.entry_id "
                                    + "WHERE entry.ref_type='TRADE' AND entry.ref_id=?",
                            tradeId
                    );
                    return new TradeLedgerFacts(tradeId, tradeCount, ledgerEntries, ledgerEvents);
                }
            }
        }

        long productionTradeCountForAccountCode(String accountCode) throws SQLException {
            try (Connection connection = DriverManager.getConnection(databaseUrl, user, password)) {
                return queryLong(
                        connection,
                        "SELECT COUNT(*) FROM trades trade "
                                + "JOIN orders local_order ON local_order.order_id=trade.order_id "
                                + "JOIN accounts account ON account.account_id=local_order.account_id "
                                + "WHERE account.account_code=?",
                        accountCode
                );
            }
        }

        String databaseName() {
            return databaseName;
        }

        String caseId() {
            return caseId;
        }

        String databaseUrl() {
            return databaseUrl;
        }

        String user() {
            return user;
        }

        String password() {
            return password;
        }

        private static OrderIdentity orderIdentity(Connection connection, String orderId) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT account_id,status FROM orders WHERE order_id=?"
            )) {
                statement.setString(1, orderId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new AssertionError("target order missing from scenario database");
                    }
                    OrderIdentity identity = new OrderIdentity(resultSet.getLong(1), resultSet.getString(2));
                    if (resultSet.next()) {
                        throw new AssertionError("duplicate target order identity");
                    }
                    return identity;
                }
            }
        }

        private static long queryLong(Connection connection, String sql, Object... parameters) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        }

        private static BigDecimal queryDecimal(Connection connection, String sql, Object... parameters)
                throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    BigDecimal value = resultSet.getBigDecimal(1);
                    return value == null ? BigDecimal.ZERO : value;
                }
            }
        }

        private static String queryString(Connection connection, String sql, Object... parameters)
                throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getString(1);
                }
            }
        }

        private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
        }

        @Override
        public void close() throws Exception {
            dropDatabase(maintenanceUrl, user, password, databaseName);
            deleteOutputDirectory();
        }

        private static void dropDatabase(String maintenanceUrl, String user, String password, String databaseName)
                throws Exception {
            try (Connection connection = DriverManager.getConnection(maintenanceUrl, user, password);
                    Statement statement = connection.createStatement()) {
                statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                        + "WHERE datname='" + databaseName + "' AND pid<>pg_backend_pid()");
                statement.execute("DROP DATABASE IF EXISTS " + databaseName);
            }
        }

        private void deleteOutputDirectory() throws IOException {
            Path directory = Path.of("target", "f002-restart", caseId).toAbsolutePath().normalize();
            if (!Files.exists(directory)) {
                return;
            }
            try (var paths = Files.walk(directory)) {
                paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ex) {
                                throw new IllegalStateException(ex);
                            }
                        });
            }
        }

        private static String requiredEnvironment(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("missing required environment variable: " + name);
            }
            return value;
        }

        private static String replaceDatabase(String jdbcUrl, String database) {
            int query = jdbcUrl.indexOf('?');
            String suffix = query < 0 ? "" : jdbcUrl.substring(query);
            String base = query < 0 ? jdbcUrl : jdbcUrl.substring(0, query);
            int slash = base.lastIndexOf('/');
            if (!base.startsWith("jdbc:postgresql://") || slash < "jdbc:postgresql://".length()) {
                throw new IllegalArgumentException("PostgreSQL JDBC URL required");
            }
            return base.substring(0, slash + 1) + database + suffix;
        }
    }
}
