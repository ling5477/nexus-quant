package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅在任务临时 PG16 内验证 V46 升级及空库迁移；清理范围限本测试生成的 schema。 */
@EnabledIfSystemProperty(named = "nq.l4.blockers.enabled", matches = "true")
class OrderVersionFlywayPostgresIntegrationTest {
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void migratesAndValidatesVersionContract(boolean upgrade) {
        String url = System.getProperty("spring.datasource.url", "");
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.endsWith("/nq_l4_blocker"));
        String schema = "c1_occ_" + UUID.randomUUID().toString().replace("-", "");
        var dataSource = new DriverManagerDataSource(url + "?currentSchema=" + schema, "postgres", "");
        var jdbc = new JdbcTemplate(dataSource);
        var repository = new JdbcOrderRepository(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Flyway finalFlyway = flyway(url, schema, "47");
        try {
            assertTrue(jdbc.queryForObject("SHOW server_version", String.class).startsWith("16."));
            if (upgrade) {
                Flyway previous = flyway(url, schema, "46");
                assertEquals(46, previous.migrate().migrationsExecuted);
                previous.validate();
                jdbc.update("INSERT INTO accounts(account_code,venue,status) VALUES ('occ-old','OKX','ACTIVE')");
                repository.insert(order("existing", 1L, OrderStatus.SENT), Instant.EPOCH);
                assertEquals(1, finalFlyway.migrate().migrationsExecuted);
                assertEquals(0, repository.findByOrderId("existing").orElseThrow().version());
            } else {
                assertEquals(47, finalFlyway.migrate().migrationsExecuted);
                jdbc.update("INSERT INTO accounts(account_code,venue,status) VALUES ('occ-new','OKX','ACTIVE')");
            }
            finalFlyway.validate();
            assertEquals("47", finalFlyway.info().current().getVersion().getVersion());
            assertEquals(0, finalFlyway.info().pending().length);
            assertNotNull(jdbc.queryForObject("SELECT col_description('orders'::regclass, attnum) FROM pg_attribute"
                    + " WHERE attrelid='orders'::regclass AND attname='version'", String.class));
            repository.insert(order("constraint", 1L, OrderStatus.SENT), Instant.EPOCH);
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE orders SET version=-1 WHERE order_id='constraint'"));
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE orders SET version=NULL WHERE order_id='constraint'"));

            OrderStatus[][] matrix = {{OrderStatus.SENT, OrderStatus.SENT, OrderStatus.ACCEPTED},
                    {OrderStatus.SENT, OrderStatus.FILLED, OrderStatus.ACCEPTED},
                    {OrderStatus.SENT, OrderStatus.FILLED, OrderStatus.REJECTED},
                    {OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELLED},
                    {OrderStatus.CANCEL_REQUESTED, OrderStatus.FILLED, OrderStatus.CANCELLED},
                    {OrderStatus.CANCEL_REQUESTED, OrderStatus.FILLED, OrderStatus.CANCEL_REJECTED}};
            for (int i = 0; i < matrix.length; i++) {
                var row = matrix[i];
                String id = "matrix-" + i;
                repository.insert(order(id, 1L, row[1]), Instant.EPOCH);
                int expected = row[0] == row[1] ? 1 : 0;
                Integer affected = tx.execute(status -> repository.compareAndSetStatus(id, row[0], 0, row[2], "test", Instant.now()));
                assertEquals(expected, affected.intValue());
                var actual = repository.findByOrderId(id).orElseThrow();
                assertEquals(expected, actual.version());
                assertEquals(expected == 1 ? row[2] : row[1], actual.status());
            }
            assertEquals(1, repository.updateExternalOrderId("constraint", "venue-A", Instant.now()));
            assertEquals(0, repository.updateExternalOrderId("constraint", "venue-A", Instant.now()));
            assertEquals(0, repository.updateExternalOrderId("constraint", "venue-B", Instant.now()));
            assertEquals("venue-A", repository.findByOrderId("constraint").orElseThrow().externalOrderId());
            assertEquals(0, repository.findByOrderId("constraint").orElseThrow().version());
            System.out.println("C1 MIGRATION PASS: upgrade=" + upgrade + " version=47 validate=PASS matrix=6 identity=PASS");
        } finally {
            finalFlyway.clean();
        }
    }

    private static Flyway flyway(String url, String schema, String target) {
        return Flyway.configure().dataSource(url, "postgres", "").locations("classpath:db/migration")
                .schemas(schema).defaultSchema(schema).createSchemas(true).cleanDisabled(false).target(target).load();
    }
    private static OrderRecord order(String id, Long account, OrderStatus status) {
        return new OrderRecord(id, account, null, "OKX", "BTC-USDT", id, "BUY", "LIMIT", BigDecimal.ONE,
                BigDecimal.ONE, null, status, "OCC_TEST", "occ-test", "SIM");
    }
}
