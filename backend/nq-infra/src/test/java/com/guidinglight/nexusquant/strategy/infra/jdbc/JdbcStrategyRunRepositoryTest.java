package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcStrategyRunRepositoryTest {

    @Test
    void shouldFindExistsAndUpdateStrategyRun() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryResults = List.of(new StrategyRun(
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
        jdbcTemplate.queryForObjectResult = 1;
        JdbcStrategyRunRepository repository = new JdbcStrategyRunRepository(jdbcTemplate);

        assertTrue(repository.findByStrategyRunId("run-1").isPresent());
        assertTrue(repository.findLatestByRequestId("req-1").isPresent());
        assertTrue(repository.existsActiveRunByStrategyId("str-1"));
        assertTrue(repository.updateStatus("run-1", StrategyRunStatus.FAILED, Instant.now(), null));
        assertTrue(jdbcTemplate.lastUpdateSql.contains("UPDATE strategy_runs SET status = ?"));
    }

    @Test
    void shouldReturnFalseWhenUpdateTargetMissing() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateCount = 0;
        jdbcTemplate.queryForObjectResult = 0;
        JdbcStrategyRunRepository repository = new JdbcStrategyRunRepository(jdbcTemplate);

        assertFalse(repository.existsActiveRunByStrategyId("missing"));
        assertFalse(repository.updateStatus("missing", StrategyRunStatus.FAILED, Instant.now(), "boom"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private List<StrategyRun> queryResults = new ArrayList<>();
        private Integer queryForObjectResult = 0;
        private String lastUpdateSql;
        private int updateCount = 1;

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            return updateCount;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) queryResults;
            return casted;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(queryForObjectResult);
        }
    }
}


