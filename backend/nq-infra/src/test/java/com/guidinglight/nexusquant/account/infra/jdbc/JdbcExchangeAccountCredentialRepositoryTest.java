package com.guidinglight.nexusquant.account.infra.jdbc;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcExchangeAccountCredentialRepositoryTest {

    @Test
    void shouldReadActiveCredentialAndWriteLifecycleRotationAndVerification() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ExchangeAccountCredentialSummary summary = new ExchangeAccountCredentialSummary(1L, 900001L, "OKX_API_V5", "tes***ey", "ACTIVE", "PENDING", true, null, null, null, null, null, Instant.parse("2026-04-06T00:00:00Z"));
        ExchangeAccountCredentialMaterial material = new ExchangeAccountCredentialMaterial(1L, 900001L, "OKX_API_V5", "tes***ey", "ACTIVE", "PENDING", true, null, null, null, null, null, Instant.parse("2026-04-06T00:00:00Z"), "{\"apiKey\":\"test\"}");
        jdbcTemplate.summaryResults = List.of(summary);
        jdbcTemplate.materialResults = List.of(material);
        jdbcTemplate.queryForObjectSummary = summary;
        JdbcExchangeAccountCredentialRepository repository = new JdbcExchangeAccountCredentialRepository(jdbcTemplate, "master-key");

        assertEquals(Optional.of(summary), repository.findActiveSummary(1L, 900001L));
        assertEquals(Optional.of(summary), repository.findActiveSummary(1L, 900001L, "OKX_API_V5"));
        assertEquals(Optional.of(summary), repository.findActiveByAccountAndType(900001L, "OKX_API_V5"));
        assertEquals(Optional.of(summary), repository.findByCredentialIdForOwner(1L, 900001L, 1L));
        assertEquals(Optional.of(summary), repository.findActiveByCredentialIdForOwnerForUpdate(1L, 900001L, 1L));
        assertEquals(Optional.of(material), repository.findByCredentialIdForOwnerForUpdate(1L, 900001L, 1L));
        assertFalse(repository.existsOtherActiveCredential(900001L, "OKX_API_V5", 1L));
        assertEquals(Optional.of(material), repository.findActiveMaterial(1L, 900001L));
        assertEquals(Optional.of(material), repository.findActiveMaterial(1L, 900001L, "OKX_API_V5"));
        assertEquals(summary, repository.insertNewVersion(900001L, "OKX_API_V5", "{}", 1, "PGP_SYM_AES256", "tes***ey", null, Instant.parse("2026-04-06T00:01:00Z")));
        repository.deactivateActiveByAccountAndType(900001L, "OKX_API_V5", Instant.parse("2026-04-06T00:02:00Z"));
        assertTrue(repository.markVerificationResult(1L, "VERIFIED", Instant.parse("2026-04-06T00:03:00Z"), null, Instant.parse("2026-04-06T00:03:00Z")));
        assertTrue(repository.markPermissionProbeInProgress(1L, 900001L, Instant.parse("2026-04-06T00:03:10Z")));
        assertTrue(repository.markPermissionProbeResult(1L, 900001L, "FAILED", null, "FAILED", Instant.parse("2026-04-06T00:03:20Z"), "AUTH_FAILED", true, Instant.parse("2026-04-06T00:03:20Z")));
        assertTrue(repository.markEnabled(1L, 900001L, "VERIFIED", Instant.parse("2026-04-06T00:03:30Z"), Instant.parse("2026-04-06T00:03:30Z")));
        assertTrue(repository.updateLifecycleStatus(1L, 900001L, "REVOKED", false, Instant.parse("2026-04-06T00:04:00Z"), "admin", "offboarded", Instant.parse("2026-04-06T00:04:00Z")));
        assertTrue(repository.markRotated(1L, 900001L, "admin", Instant.parse("2026-04-06T00:05:00Z")));
        repository.appendCredentialAuditLog(1L, 900001L, "REVOKED", "admin", "offboarded", "{\"credentialStatus\":\"REVOKED\"}", Instant.parse("2026-04-06T00:04:00Z"));

        assertTrue(jdbcTemplate.querySqls.stream().anyMatch(sql -> sql.contains("pgp_sym_decrypt")));
        assertTrue(jdbcTemplate.querySqls.stream().anyMatch(sql -> sql.contains("credential_type = ?")));
        assertTrue(jdbcTemplate.querySqls.stream().anyMatch(sql -> sql.contains("credential_status = 'ACTIVE'")));
        assertTrue(jdbcTemplate.querySqls.stream().anyMatch(sql -> sql.contains("FOR UPDATE")));
        assertTrue(jdbcTemplate.queryForObjectCountSql.contains("COUNT(1)"));
        assertFalse(jdbcTemplate.querySqls.stream().anyMatch(sql -> sql.contains("ORDER BY updated_at DESC") && sql.contains("LIMIT 1")));
        assertTrue(jdbcTemplate.querySqls.stream().anyMatch(sql -> sql.contains("permission_probe_status")));
        assertTrue(jdbcTemplate.querySqls.stream().anyMatch(sql -> sql.contains("permission_scope")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("permission_probe_status = 'IN_PROGRESS'")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("failed_auth_count = failed_auth_count + CASE WHEN")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("credential_status = 'ROTATED'")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("credential_status = 'ACTIVE'")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("credential_status = ?")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("rotated_by = ?")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("INSERT INTO credential_audit_logs")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("CAST(? AS jsonb)")));
        assertTrue(jdbcTemplate.queryForObjectSql.contains("pgp_sym_encrypt"));
    }

    @Test
    void shouldRejectAmbiguousNoTypeActiveSelectionBeforeDecryptingMaterial() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ExchangeAccountCredentialSummary okx = new ExchangeAccountCredentialSummary(1L, 900001L, "OKX_API_V5", "okx***ey", "ACTIVE", "PENDING", true, null, null, null, null, null, Instant.parse("2026-04-06T00:00:00Z"));
        ExchangeAccountCredentialSummary binance = new ExchangeAccountCredentialSummary(2L, 900001L, "BINANCE_HMAC", "bin***ey", "ACTIVE", "PENDING", true, null, null, null, null, null, Instant.parse("2026-04-06T00:01:00Z"));
        jdbcTemplate.summaryResults = List.of(okx, binance);
        JdbcExchangeAccountCredentialRepository repository = new JdbcExchangeAccountCredentialRepository(jdbcTemplate, "master-key");

        IllegalStateException summaryConflict = assertThrows(IllegalStateException.class, () -> repository.findActiveSummary(1L, 900001L));
        IllegalStateException materialConflict = assertThrows(IllegalStateException.class, () -> repository.findActiveMaterial(1L, 900001L));

        assertEquals("multiple active credential types require credentialType", summaryConflict.getMessage());
        assertEquals("multiple active credential types require credentialType", materialConflict.getMessage());
        assertFalse(jdbcTemplate.querySqls.stream().anyMatch(sql -> sql.contains("pgp_sym_decrypt")));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private List<ExchangeAccountCredentialSummary> summaryResults = new ArrayList<>();
        private List<ExchangeAccountCredentialMaterial> materialResults = new ArrayList<>();
        private ExchangeAccountCredentialSummary queryForObjectSummary;
        private final List<String> querySqls = new ArrayList<>();
        private final List<String> updateSqls = new ArrayList<>();
        private String queryForObjectSql;
        private String queryForObjectCountSql;

        @Override
        public int update(String sql, Object... args) {
            updateSqls.add(sql);
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            querySqls.add(sql);
            if (sql.contains("pgp_sym_decrypt")) {
                @SuppressWarnings("unchecked")
                List<T> casted = (List<T>) materialResults;
                return casted;
            }
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) summaryResults;
            return casted;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            queryForObjectSql = sql;
            @SuppressWarnings("unchecked")
            T casted = (T) queryForObjectSummary;
            return casted;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryForObjectCountSql = sql;
            return requiredType.cast(0);
        }
    }
}
