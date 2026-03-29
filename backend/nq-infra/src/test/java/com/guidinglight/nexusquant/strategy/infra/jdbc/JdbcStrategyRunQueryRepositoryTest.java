package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderSummary;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcStrategyRunQueryRepositoryTest {

    @Test
    void shouldQueryRunOrderAndTradeSummaries() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.runResults = List.of(new StrategyRun(
                "run-1",
                "str-1",
                1001L,
                "BINANCE",
                "SIM",
                "MANUAL",
                StrategyRunStatus.RUNNING,
                "{}",
                "req-1",
                Instant.parse("2026-03-24T03:00:00Z"),
                null,
                null,
                "trc-1"
        ));
        jdbcTemplate.orderResults = List.of(new StrategyRunOrderSummary(
                "ord-1",
                "coid-1",
                "ex-ord-1",
                "ACCEPTED",
                "BTC-USDT",
                "BUY",
                "LIMIT",
                new BigDecimal("100.00"),
                new BigDecimal("0.01")
        ));
        jdbcTemplate.tradeResults = List.of(new StrategyRunTradeSummary(
                "trd-1",
                "ex-trd-1",
                "ex-ord-1",
                new BigDecimal("100.10"),
                new BigDecimal("0.01"),
                Instant.parse("2026-03-24T03:00:05Z")
        ));
        JdbcStrategyRunQueryRepository repository = new JdbcStrategyRunQueryRepository(jdbcTemplate);

        assertTrue(repository.findRunByStrategyRunId("run-1").isPresent());
        assertEquals(1, repository.listRecentRunsByStrategyId("str-1", 20).size());
        assertEquals(1, repository.listRecentRunsByScheduleJobId("sch-1", 20).size());
        assertEquals(1, repository.listOrderSummariesByStrategyRunId("run-1").size());
        assertEquals(1, repository.listTradeSummariesByStrategyRunId("run-1").size());
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private List<StrategyRun> runResults = new ArrayList<>();
        private List<StrategyRunOrderSummary> orderResults = new ArrayList<>();
        private List<StrategyRunTradeSummary> tradeResults = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("FROM strategy_runs")) {
                @SuppressWarnings("unchecked")
                List<T> casted = (List<T>) runResults;
                return casted;
            }
            if (sql.contains("FROM orders")) {
                @SuppressWarnings("unchecked")
                List<T> casted = (List<T>) orderResults;
                return casted;
            }
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) tradeResults;
            return casted;
        }
    }
}


