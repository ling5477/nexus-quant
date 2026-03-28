package com.guidinglight.nexusquant.infra.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcRiskEventRepositoryTest {

    @Test
    void shouldAppendRiskEvent() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        JdbcRiskEventRepository repository = new JdbcRiskEventRepository(jdbcTemplate);

        repository.append("ORDER", "ord-1", RiskDecision.REJECT, "risk.rule", RiskSeverity.HIGH, "trc-1");

        assertTrue(jdbcTemplate.lastSql.contains("INSERT INTO risk_events"));
        assertTrue(String.valueOf(jdbcTemplate.lastArgs[0]).startsWith("rsk-"));
        assertEquals("risk.rule", jdbcTemplate.lastArgs[1]);
        assertEquals("REJECT", jdbcTemplate.lastArgs[4]);
        assertEquals("HIGH", jdbcTemplate.lastArgs[6]);
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String lastSql;
        private Object[] lastArgs;

        @Override
        public int update(String sql, Object... args) {
            this.lastSql = sql;
            this.lastArgs = args;
            return 1;
        }
    }
}
