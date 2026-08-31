package com.guidinglight.nexusquant.scheduler.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcTradeRepositoryTest {

    @Test
    void shouldFindAndInsertTradeRecords() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryResults = List.of(new PaperTradeRecord(
                "trd-1",
                "ord-1",
                1001L,
                "BTC-USDT",
                "OKX",
                "ex-ord-1",
                "ex-trd-1",
                new BigDecimal("100.00"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO,
                "USDT",
                "trc-trade-1",
                Instant.parse("2026-03-28T11:00:00Z")
        ));
        JdbcTradeRepository repository = new JdbcTradeRepository(jdbcTemplate);

        assertTrue(repository.findByOrderId("ord-1").isPresent());
        assertTrue(repository.findAllByOrderId("ord-1", 10).size() == 1);
        assertTrue(repository.findByExchangeAndExchangeTradeId("OKX", "ex-trd-1").isPresent());
        repository.insert(jdbcTemplate.queryResults.getFirst());
        assertTrue(jdbcTemplate.lastUpdateSql.contains("INSERT INTO trades"));
    }

    @Test
    void shouldFailClosedWhenPerOrderTradeLimitWouldTruncate() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PaperTradeRecord first = trade("trd-1", "ex-trd-1", Instant.parse("2026-03-28T11:00:00Z"));
        PaperTradeRecord second = trade("trd-2", "ex-trd-2", Instant.parse("2026-03-28T11:00:01Z"));
        jdbcTemplate.queryResults = List.of(first, second);

        JdbcTradeRepository repository = new JdbcTradeRepository(jdbcTemplate);

        assertThrows(IllegalStateException.class, () -> repository.findAllByOrderId("ord-1", 1));
    }

    private PaperTradeRecord trade(String tradeId, String exchangeTradeId, Instant ts) {
        return new PaperTradeRecord(
                tradeId,
                "ord-1",
                1001L,
                "BTC-USDT",
                "OKX",
                "ex-ord-1",
                exchangeTradeId,
                new BigDecimal("100.00"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO,
                "USDT",
                "trc-trade-1",
                ts
        );
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private List<PaperTradeRecord> queryResults = new ArrayList<>();
        private String lastUpdateSql;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) queryResults;
            return casted;
        }

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            return 1;
        }
    }
}

