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
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcExchangeAccountCredentialRepositoryTest {

    @Test
    void shouldReadActiveCredentialAndWriteRotationAndVerification() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ExchangeAccountCredentialSummary summary = new ExchangeAccountCredentialSummary(1L, 900001L, "OKX_API_V5", "tes***ey", "PENDING", true, null, null, null, Instant.parse("2026-04-06T00:00:00Z"));
        ExchangeAccountCredentialMaterial material = new ExchangeAccountCredentialMaterial(1L, 900001L, "OKX_API_V5", "tes***ey", "PENDING", true, null, null, null, Instant.parse("2026-04-06T00:00:00Z"), "{\"apiKey\":\"test\"}");
        jdbcTemplate.summaryResults = List.of(summary);
        jdbcTemplate.materialResults = List.of(material);
        jdbcTemplate.queryForObjectSummary = summary;
        JdbcExchangeAccountCredentialRepository repository = new JdbcExchangeAccountCredentialRepository(jdbcTemplate, "master-key");

        assertEquals(Optional.of(summary), repository.findActiveSummary(1L, 900001L));
        assertEquals(Optional.of(summary), repository.findActiveByAccountAndType(900001L, "OKX_API_V5"));
        assertEquals(Optional.of(material), repository.findActiveMaterial(1L, 900001L));
        assertEquals(summary, repository.insertNewVersion(900001L, "OKX_API_V5", "{}", 1, "PGP_SYM_AES256", "tes***ey", null, Instant.parse("2026-04-06T00:01:00Z")));
        repository.deactivateActiveByAccountAndType(900001L, "OKX_API_V5", Instant.parse("2026-04-06T00:02:00Z"));
        assertTrue(repository.markVerificationResult(1L, "VERIFIED", Instant.parse("2026-04-06T00:03:00Z"), null, Instant.parse("2026-04-06T00:03:00Z")));

        assertTrue(jdbcTemplate.querySqls.stream().anyMatch(sql -> sql.contains("pgp_sym_decrypt")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("verification_status = 'REVOKED'")));
        assertTrue(jdbcTemplate.queryForObjectSql.contains("pgp_sym_encrypt"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private List<ExchangeAccountCredentialSummary> summaryResults = new ArrayList<>();
        private List<ExchangeAccountCredentialMaterial> materialResults = new ArrayList<>();
        private ExchangeAccountCredentialSummary queryForObjectSummary;
        private final List<String> querySqls = new ArrayList<>();
        private final List<String> updateSqls = new ArrayList<>();
        private String queryForObjectSql;

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
    }
}
