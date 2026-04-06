package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcMarketdataBarRepositoryTest {

    @Test
    void shouldUpsertBarsWithConflictUpdateAndReturnInsertUpdateCounts() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of(true, false));
        JdbcMarketdataBarRepository repository = new JdbcMarketdataBarRepository(jdbcTemplate);

        var stats = repository.upsertBars(List.of(
                new HistoricalBar(
                        "BINANCE",
                        "BTCUSDT",
                        BarInterval.ONE_MINUTE,
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2025-01-01T00:00:59Z"),
                        new BigDecimal("43000.00"),
                        new BigDecimal("43020.00"),
                        new BigDecimal("42980.00"),
                        new BigDecimal("43010.00"),
                        new BigDecimal("12.50")
                ),
                new HistoricalBar(
                        "BINANCE",
                        "BTCUSDT",
                        BarInterval.ONE_MINUTE,
                        Instant.parse("2025-01-01T00:01:00Z"),
                        Instant.parse("2025-01-01T00:01:59Z"),
                        new BigDecimal("43010.00"),
                        new BigDecimal("43050.00"),
                        new BigDecimal("43005.00"),
                        new BigDecimal("43040.00"),
                        new BigDecimal("10.20")
                )
        ), "FIXTURE_SYNC", Instant.parse("2026-04-06T00:00:00Z"));

        assertEquals(1, stats.insertedCount());
        assertEquals(1, stats.updatedCount());
        assertTrue(jdbcTemplate.sqls.getFirst().contains("ON CONFLICT (exchange_code, symbol, \"interval\", open_time) DO UPDATE"));
        assertTrue(jdbcTemplate.sqls.getFirst().contains("close_price = EXCLUDED.close_price"));
        assertTrue(jdbcTemplate.sqls.getFirst().contains("ingested_at = EXCLUDED.ingested_at"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<Boolean> queryResults;
        private final List<String> sqls = new ArrayList<>();
        private int index = 0;

        private RecordingJdbcTemplate(List<Boolean> queryResults) {
            this.queryResults = queryResults;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            sqls.add(sql);
            @SuppressWarnings("unchecked")
            T result = (T) queryResults.get(index++);
            return result;
        }
    }
}
