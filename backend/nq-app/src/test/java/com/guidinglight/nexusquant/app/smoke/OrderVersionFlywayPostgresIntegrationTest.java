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

/** 仅在任务临时 PG16 内验证正式基线首次安装和已有订单的重复迁移；清理范围限本测试生成的 schema。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class OrderVersionFlywayPostgresIntegrationTest {
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void migratesAndValidatesVersionContract(boolean populated) throws Exception {
        var database = ReleaseBaselineTestDatabase.create();
        var jdbc = new JdbcTemplate(database.source());
        var repository = new JdbcOrderRepository(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(database.source()));
        Flyway finalFlyway = database.flyway();
        try {
            assertTrue(jdbc.queryForObject("SHOW server_version", String.class).startsWith("16."));
            if (populated) {
                Flyway previous = database.flyway();
                assertEquals(1, previous.migrate().migrationsExecuted);
                previous.validate();
                jdbc.update("INSERT INTO accounts(account_code,venue,status) VALUES ('occ-old','OKX','ACTIVE')");
                repository.insert(order("existing", 1L, OrderStatus.SENT), Instant.EPOCH);
                assertEquals(0, finalFlyway.migrate().migrationsExecuted);
                assertEquals(0, repository.findByOrderId("existing").orElseThrow().version());
            } else {
                assertEquals(1, finalFlyway.migrate().migrationsExecuted);
                jdbc.update("INSERT INTO accounts(account_code,venue,status) VALUES ('occ-new','OKX','ACTIVE')");
            }
            finalFlyway.validate();
            assertEquals("1", finalFlyway.info().current().getVersion().getVersion());
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
            System.out.println("ORDER_BASELINE_PASS populated=" + populated + " version=1 validate=PASS matrix=6 identity=PASS");
        } finally {
            database.close();
        }
    }

    private static OrderRecord order(String id, Long account, OrderStatus status) {
        return new OrderRecord(id, account, null, "OKX", "BTC-USDT", id, "BUY", "LIMIT", BigDecimal.ONE,
                BigDecimal.ONE, null, status, "OCC_TEST", "occ-test", "SIM");
    }
}
