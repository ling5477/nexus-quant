package com.guidinglight.nexusquant.infra.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcLedgerRiskAuditRepositoryTest {

    @Test
    void shouldAppendRiskAndAuditRows() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        JdbcLedgerRiskAuditRepository repository = new JdbcLedgerRiskAuditRepository(jdbcTemplate, new ObjectMapper());

        repository.appendRiskEvent("LEDGER", "entry-1", "REJECT", "ledger.rule", "HIGH", "trc-ledger-risk");
        assertTrue(jdbcTemplate.lastSql.contains("INSERT INTO risk_events"));
        assertTrue(String.valueOf(jdbcTemplate.lastArgs[0]).startsWith("rsk-"));
        assertEquals("ledger.rule", jdbcTemplate.lastArgs[1]);

        repository.appendAudit("ledger", "post", "system", "trc-ledger-audit", Map.of("entryId", "entry-1"));
        assertTrue(jdbcTemplate.lastSql.contains("INSERT INTO audit_logs"));
        assertTrue(String.valueOf(jdbcTemplate.lastArgs[4]).contains("\"entryId\":\"entry-1\""));
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
