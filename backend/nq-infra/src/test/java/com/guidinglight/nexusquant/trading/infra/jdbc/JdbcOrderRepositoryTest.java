package com.guidinglight.nexusquant.trading.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class JdbcOrderRepositoryTest {

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void returnsActualRowCountAndBindsExpectedGeneration(int affected) {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        jdbc.affected = affected;
        JdbcOrderRepository repository = new JdbcOrderRepository(jdbc);
        assertEquals(affected, repository.compareAndSetStatus("ord-1", OrderStatus.CANCEL_REQUESTED, 9,
                OrderStatus.CANCELLED, "test", Instant.EPOCH));
        assertEquals("UPDATE orders SET status = ?, reason = ?, version = version + 1, updated_at = ?"
                + " WHERE order_id = ? AND status = ? AND version = ?", jdbc.lastUpdateSql);
        assertArrayEquals(new Object[]{"CANCELLED", "test", java.sql.Timestamp.from(Instant.EPOCH),
                "ord-1", "CANCEL_REQUESTED", 9L}, jdbc.lastArgs);
    }

    @Test void persistedMappingReadsVersionAndIdentityCopyPreservesIt() throws Exception {
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getString("status")).thenReturn("SENT");
        when(rs.getString("trade_env")).thenReturn("SIM");
        when(rs.getLong("version")).thenReturn(41L);
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                assertTrue(sql.contains("trade_env, version"));
                try { return List.of(mapper.mapRow(rs, 0)); }
                catch (java.sql.SQLException ex) { throw new AssertionError(ex); }
            }
        };
        OrderRecord order = new JdbcOrderRepository(jdbc).findByOrderId("ord-1").orElseThrow();
        assertEquals(41, order.version());
        assertEquals(41, order.withExternalOrderId("venue-id").version());
        assertEquals(42, order.withStatus(OrderStatus.ACCEPTED, "test").version());
        verify(rs).getLong("version");
    }

    @Test void guardsInvalidInsertVersionAndIdentityInputs() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        JdbcOrderRepository repository = new JdbcOrderRepository(jdbc);
        assertThrows(IllegalArgumentException.class, () -> repository.insert(orderRecord().withStatus(OrderStatus.FILLED, "test"), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> repository.compareAndSetStatus("ord-1", OrderStatus.SENT, -1,
                OrderStatus.ACCEPTED, "test", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> repository.compareAndSetStatus("ord-1", OrderStatus.SENT, Long.MAX_VALUE,
                OrderStatus.ACCEPTED, "test", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> repository.updateExternalOrderId("ord-1", " ", Instant.EPOCH));
        assertEquals(1, repository.updateExternalOrderId("ord-1", "venue-id", Instant.EPOCH));
        assertTrue(jdbc.lastUpdateSql.contains("external_order_id IS NULL OR BTRIM(external_order_id) = ''"));
        assertTrue(!jdbc.lastUpdateSql.contains("version"));
    }

    @Test
    void shouldFindInsertUpdateAndQueryStatuses() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingNamedParameterJdbcTemplate namedParameterJdbcTemplate = new RecordingNamedParameterJdbcTemplate(jdbcTemplate);
        OrderRecord order = orderRecord();
        jdbcTemplate.queryResults = List.of(order);
        namedParameterJdbcTemplate.queryResults = List.of(order);
        JdbcOrderRepository repository = new JdbcOrderRepository(jdbcTemplate, namedParameterJdbcTemplate);

        assertTrue(repository.findByAccountAndClientOrderId(1001L, "coid-1").isPresent());
        assertTrue(repository.findByOrderId("ord-1").isPresent());
        repository.insert(order, Instant.parse("2026-03-24T03:00:00Z"));
        assertTrue(jdbcTemplate.lastUpdateSql.contains("exchange_code"));
        assertTrue(jdbcTemplate.lastUpdateSql.contains("trade_env"));
        assertEquals(1, repository.compareAndSetStatus("ord-1", OrderStatus.SENT, 2L,
                OrderStatus.ACCEPTED, "accepted", Instant.parse("2026-03-24T03:00:01Z")));
        repository.updateExternalOrderId("ord-1", "ex-ord-1", Instant.parse("2026-03-24T03:00:02Z"));
        assertEquals(1, repository.findByStatuses(List.of(OrderStatus.ACCEPTED), 10).size());

        assertTrue(jdbcTemplate.lastUpdateSql.contains("external_order_id"));
        assertTrue(namedParameterJdbcTemplate.lastQuerySql.contains("WHERE status IN (:statuses)"));
    }

    private static OrderRecord orderRecord() {
        return new OrderRecord(
                "ord-1",
                1001L,
                "run-1",
                "BINANCE",
                "BTC-USDT",
                "coid-1",
                "BUY",
                "LIMIT",
                new BigDecimal("100.00"),
                new BigDecimal("0.01"),
                null,
                OrderStatus.ACCEPTED,
                null,
                "trc-1"
        );
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private List<OrderRecord> queryResults = new ArrayList<>();
        private String lastUpdateSql;
        private Object[] lastArgs;
        private int affected = 1;

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            this.lastArgs = args;
            return affected;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) queryResults;
            return casted;
        }
    }

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private List<OrderRecord> queryResults = new ArrayList<>();
        private String lastQuerySql;

        private RecordingNamedParameterJdbcTemplate(JdbcTemplate classicJdbcTemplate) {
            super(classicJdbcTemplate);
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.lastQuerySql = sql;
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) queryResults;
            return casted;
        }
    }
}


