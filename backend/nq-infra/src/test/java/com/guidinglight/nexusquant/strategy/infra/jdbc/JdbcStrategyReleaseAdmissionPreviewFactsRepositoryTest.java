package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewFacts;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Admission preview facts adapter 的 exact-publish bounded SELECT 与 no-write 回归。 */
class JdbcStrategyReleaseAdmissionPreviewFactsRepositoryTest {

    @Test
    void shouldUseOnlyBoundedSelectsAndReturnMissingFacts() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        JdbcStrategyReleaseAdmissionPreviewFactsRepository repository =
                new JdbcStrategyReleaseAdmissionPreviewFactsRepository(jdbcTemplate);

        StrategyReleaseAdmissionPreviewFacts facts = repository.loadByPublishRecordId("pub-preview-1");

        assertNull(facts.validationFact());
        assertNull(facts.windowStart());
        assertNull(facts.windowEnd());
        assertNotNull(jdbcTemplate.lastSql);
        String sql = jdbcTemplate.lastSql.toUpperCase(Locale.ROOT);
        assertTrue(sql.startsWith("WITH SELECTED_PUBLISH"));
        assertTrue(sql.contains("WHERE P.PUBLISH_RECORD_ID = ?"));
        assertTrue(sql.contains("WHERE PR.PUBLISH_ID = ?"));
        assertTrue(sql.contains("SP.STRATEGY_VERSION_ID = SR.STRATEGY_VERSION_ID"));
        assertTrue(sql.contains("SP.PUBLISH_RECORD_ID = SR.PUBLISH_ID"));
        assertTrue(sql.contains("R.CONFIG_SNAPSHOT_JSON ->> 'STARTTIME'"));
        assertFalse(sql.contains(" INSERT "));
        assertFalse(sql.contains(" UPDATE "));
        assertFalse(sql.contains(" DELETE "));
        assertEquals(
                List.of("pub-preview-1", "pub-preview-1"),
                List.of(jdbcTemplate.lastArgs)
        );
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
