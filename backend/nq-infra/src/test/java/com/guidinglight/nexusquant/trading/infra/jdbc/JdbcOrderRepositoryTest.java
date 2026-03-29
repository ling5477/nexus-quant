package com.guidinglight.nexusquant.trading.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class JdbcOrderRepositoryTest {

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
        repository.updateStatus("ord-1", OrderStatus.ACCEPTED, "accepted", Instant.parse("2026-03-24T03:00:01Z"));
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

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            return 1;
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


