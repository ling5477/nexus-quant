package com.guidinglight.nexusquant.infra.audit;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcAuditLogRepositoryTest {

    @Test
    void shouldSerializeDetailJsonWhenAppendingAuditLog() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(jdbcTemplate, new ObjectMapper());

        repository.append("trading", "submit", "user-1", "trc-1", Map.of("orderId", "ord-1"));
        assertTrue(jdbcTemplate.lastSql.contains("INSERT INTO audit_logs"));
        assertTrue(String.valueOf(jdbcTemplate.lastArgs[4]).contains("\"orderId\":\"ord-1\""));
    }

    @Test
    void shouldAllowNullDetailJson() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(jdbcTemplate, new ObjectMapper());

        repository.append("trading", "submit", "user-1", "trc-1", Map.of());
        assertNull(jdbcTemplate.lastArgs[4]);
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
