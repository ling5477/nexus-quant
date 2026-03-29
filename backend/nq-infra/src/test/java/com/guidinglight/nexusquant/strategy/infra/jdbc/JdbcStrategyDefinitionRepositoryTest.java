package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcStrategyDefinitionRepositoryTest {

    @Test
    void shouldFindListAndUpdateStrategyDefinition() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        StrategyDefinition definition = new StrategyDefinition(
                "str-1",
                "demo-grid",
                "Demo Grid",
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                false,
                "{}",
                1,
                Instant.parse("2026-03-23T10:00:00Z"),
                Instant.parse("2026-03-23T10:00:00Z")
        );
        jdbcTemplate.queryResults = List.of(definition);
        JdbcStrategyDefinitionRepository repository = new JdbcStrategyDefinitionRepository(jdbcTemplate);

        assertTrue(repository.findByStrategyId("str-1").isPresent());
        assertEquals(1, repository.listAll().size());
        assertTrue(repository.updateEnabled("str-1", true, Instant.now()));
        assertTrue(jdbcTemplate.lastUpdateSql.contains("UPDATE strategy_definitions SET enabled = ?"));
    }

    @Test
    void shouldReturnFalseWhenUpdateTargetMissing() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.updateCount = 0;
        JdbcStrategyDefinitionRepository repository = new JdbcStrategyDefinitionRepository(jdbcTemplate);

        assertFalse(repository.updateEnabled("missing", false, Instant.now()));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private List<StrategyDefinition> queryResults = new ArrayList<>();
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
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) queryResults;
            return casted;
        }
    }
}

