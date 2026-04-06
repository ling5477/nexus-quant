package com.guidinglight.nexusquant.infra.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.contracts.model.LedgerDirection;
import com.guidinglight.nexusquant.ledger.contracts.model.AccountSnapshotProjection;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingEntry;
import com.guidinglight.nexusquant.ledger.contracts.model.PositionProjection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcLedgerPostingRepositoryTest {

    @Test
    void shouldQueryInsertAndUpsertLedgerData() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.countResult = 1;
        jdbcTemplate.balanceResult = new BigDecimal("12.34");
        jdbcTemplate.positionResults = List.of(new PositionProjection(
                1001L,
                "BTC-USDT",
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                new BigDecimal("100.00"),
                "trc-pos-1"
        ));
        JdbcLedgerPostingRepository repository = new JdbcLedgerPostingRepository(jdbcTemplate);

        assertTrue(repository.existsByIdempotencyKey("idem-1"));
        assertEquals(new BigDecimal("12.34"), repository.currentBalance(1001L, "USDT"));
        assertTrue(repository.findPosition(1001L, "BTC-USDT").isPresent());

        repository.insertEntry(new LedgerPostingEntry(
                "entry-1",
                1001L,
                "USDT",
                new BigDecimal("1.23"),
                new BigDecimal("12.34"),
                LedgerDirection.CREDIT,
                "TRADE",
                "trd-1",
                "idem-1",
                "trc-ledger-1",
                Instant.parse("2026-03-28T11:00:00Z")
        ));
        repository.insertLedgerEvent("entry-1", "POSTED", "{\"entryId\":\"entry-1\"}", "trc-ledger-1");
        repository.insertAccountSnapshot(new AccountSnapshotProjection(
                1001L,
                "USDT",
                new BigDecimal("12.34"),
                new BigDecimal("12.34"),
                BigDecimal.ZERO,
                Instant.parse("2026-03-28T11:00:01Z"),
                "trc-ledger-1"
        ));
        repository.upsertPosition(new PositionProjection(
                1001L,
                "BTC-USDT",
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                new BigDecimal("100.00"),
                "trc-ledger-1"
        ), Instant.parse("2026-03-28T11:00:02Z"));

        assertTrue(jdbcTemplate.lastUpdateSql.contains("INSERT INTO positions"));
    }

    @Test
    void shouldReturnDefaultsWhenLedgerRowsMissing() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.countResult = 0;
        jdbcTemplate.balanceResult = null;
        JdbcLedgerPostingRepository repository = new JdbcLedgerPostingRepository(jdbcTemplate);

        assertFalse(repository.existsByIdempotencyKey("missing"));
        assertEquals(BigDecimal.ZERO, repository.currentBalance(1001L, "USDT"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private Integer countResult = 0;
        private BigDecimal balanceResult;
        private List<PositionProjection> positionResults = new ArrayList<>();
        private String lastUpdateSql;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Integer.class) {
                return requiredType.cast(countResult);
            }
            return requiredType.cast(balanceResult);
        }

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) positionResults;
            return casted;
        }
    }
}
