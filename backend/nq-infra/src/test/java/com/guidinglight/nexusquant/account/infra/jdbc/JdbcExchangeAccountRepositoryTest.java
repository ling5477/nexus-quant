package com.guidinglight.nexusquant.account.infra.jdbc;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcExchangeAccountRepositoryTest {

    @Test
    void shouldReadAndWriteExchangeAccounts() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ExchangeAccountSummary created = new ExchangeAccountSummary(900001L, 900001L, 1L, "OKX", "SIM", "demo", "ext-1", false, "ACTIVE");
        jdbcTemplate.queryResults = List.of(created);
        jdbcTemplate.queryForObjectResult = created;
        JdbcExchangeAccountRepository repository = new JdbcExchangeAccountRepository(jdbcTemplate);

        assertEquals(1, repository.listByOwnerUserId(1L).size());
        assertEquals(Optional.of(created), repository.findByIdForOwner(1L, 900001L));
        assertEquals(Optional.of(created), repository.findDefaultByOwnerUserId(1L));
        assertEquals(created, repository.create(1L, "OKX", "SIM", "demo", "ext-1", Instant.parse("2026-04-06T00:00:00Z")));
        assertTrue(repository.updateProfile(1L, 900001L, "demo-2", "ext-2", Instant.parse("2026-04-06T00:00:01Z")));
        assertTrue(repository.enable(1L, 900001L, Instant.parse("2026-04-06T00:00:02Z")));
        assertTrue(repository.disable(1L, 900001L, Instant.parse("2026-04-06T00:00:03Z")));
        repository.clearDefaultByScope(1L, "OKX", "SIM", Instant.parse("2026-04-06T00:00:04Z"));
        assertTrue(repository.markDefault(1L, 900001L, Instant.parse("2026-04-06T00:00:05Z")));

        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("SET is_default = TRUE")));
        assertTrue(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("SET status = 'DISABLED'")));
        assertTrue(jdbcTemplate.queryForObjectSql.contains("INSERT INTO exchange_accounts"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private List<ExchangeAccountSummary> queryResults = new ArrayList<>();
        private ExchangeAccountSummary queryForObjectResult;
        private final List<String> updateSqls = new ArrayList<>();
        private String queryForObjectSql;

        @Override
        public int update(String sql, Object... args) {
            updateSqls.add(sql);
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) queryResults;
            return casted;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            this.queryForObjectSql = sql;
            @SuppressWarnings("unchecked")
            T casted = (T) queryForObjectResult;
            return casted;
        }
    }
}
