package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseProvenanceFacts;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Strategy Release provenance adapter 的 bounded read 与 canonical fact-source SQL 回归测试。 */
class JdbcStrategyReleaseProvenanceRepositoryTest {

    @Test
    void shouldUseSingleBoundedSelectAndReturnMissingFact() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        JdbcStrategyReleaseProvenanceRepository repository =
                new JdbcStrategyReleaseProvenanceRepository(jdbcTemplate);

        StrategyReleaseProvenanceFacts result = repository.loadByPublishRecordId("pub-gatex-1");

        assertFalse(result.present());
        assertEquals("pub-gatex-1", result.publishRecordId());
        assertNotNull(jdbcTemplate.lastSql);
        String normalizedSql = jdbcTemplate.lastSql.toUpperCase(java.util.Locale.ROOT);
        assertTrue(normalizedSql.contains("FROM BACKTEST_PUBLISH_RECORDS P"));
        assertTrue(normalizedSql.contains("WHERE P.PUBLISH_RECORD_ID = ?"));
        assertTrue(jdbcTemplate.lastSql.contains("r.dataset_snapshot_json ->> 'datasetId'"));
        assertFalse(normalizedSql.contains(" INSERT "));
        assertFalse(normalizedSql.contains(" UPDATE "));
        assertFalse(normalizedSql.contains(" DELETE "));
        assertEquals(List.of("pub-gatex-1"), List.of(jdbcTemplate.lastArgs));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String lastSql;
        private Object[] lastArgs;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.lastSql = sql;
            this.lastArgs = args;
            return List.of();
        }
    }
}
