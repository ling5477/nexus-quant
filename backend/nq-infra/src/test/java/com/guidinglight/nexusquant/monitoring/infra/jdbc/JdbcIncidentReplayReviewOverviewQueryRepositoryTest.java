package com.guidinglight.nexusquant.monitoring.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewFacts;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewFacts.ReviewEvidenceFact;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcIncidentReplayReviewOverviewQueryRepositoryTest {

    @Test
    void shouldLoadReviewFactsWithSelectOnlyQueryFromAllowedLocalFactTables() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ReviewEvidenceFact fact = new ReviewEvidenceFact(
                "PAPER_ALERT",
                "alt-1",
                "OPEN",
                "HIGH",
                "paper-alert:alt-1",
                null,
                null,
                "paper-1",
                null,
                "High paper alert",
                Instant.parse("2026-07-09T09:59:00Z"),
                null
        );
        jdbcTemplate.queryResults.add(List.of(fact));
        JdbcIncidentReplayReviewOverviewQueryRepository repository =
                new JdbcIncidentReplayReviewOverviewQueryRepository(jdbcTemplate);

        IncidentReplayReviewOverviewFacts facts = repository.loadOverviewFacts();

        assertEquals(List.of(fact), facts.evidence());
        assertTrue(jdbcTemplate.updateSqls.isEmpty(), "incident replay review repository must not execute writes");

        String sql = String.join("\n", jdbcTemplate.querySqls);
        String normalized = sql.toLowerCase(Locale.ROOT);
        assertTrue(normalized.contains("from shadow_run_events"));
        assertTrue(normalized.contains("left join shadow_runs"));
        assertTrue(normalized.contains("from shadow_consistency_reports"));
        assertTrue(normalized.contains("from paper_run_alerts"));
        assertTrue(normalized.contains("from paper_run_recovery_events"));
        assertTrue(normalized.contains("from trade_replay_records"));
        assertTrue(normalized.contains("jsonb_array_length"));
        assertTrue(normalized.contains("limit 50"));
        assertFalse(normalized.contains("insert "));
        assertFalse(normalized.contains("update "));
        assertFalse(normalized.contains("delete "));
        for (String forbiddenTable : List.of(
                "from credential",
                "from exchange_account_credentials",
                "from trading_accounts",
                "from real_account",
                "from live_order",
                "from orders",
                "from order_",
                "from ledger",
                "from account_balance",
                "from real_position",
                "from private_trading",
                "from provider_secret",
                "from paper_trading_orders",
                "from paper_trading_trades",
                "from paper_trading_positions",
                "from paper_risk_check_results"
        )) {
            assertFalse(normalized.contains(forbiddenTable), "review SQL must not read " + forbiddenTable + ": " + sql);
        }
        for (String forbiddenJsonPayload : List.of(
                "event_snapshot_json",
                "request_json",
                "result_json",
                "decision_snapshot_json",
                "risk_snapshot_json",
                "market_snapshot_json",
                "metric_delta::text",
                "payload"
        )) {
            assertFalse(normalized.contains(forbiddenJsonPayload), "review SQL must not expose raw JSON payload: " + sql);
        }
    }

    @Test
    void shouldReturnStableEmptyFactsWhenOptionalSourcesAreAbsent() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryResults.add(List.of());
        JdbcIncidentReplayReviewOverviewQueryRepository repository =
                new JdbcIncidentReplayReviewOverviewQueryRepository(jdbcTemplate);

        IncidentReplayReviewOverviewFacts facts = repository.loadOverviewFacts();

        assertTrue(facts.evidence().isEmpty());
        assertTrue(jdbcTemplate.updateSqls.isEmpty());
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final Queue<List<?>> queryResults = new ArrayDeque<>();
        private final List<String> querySqls = new ArrayList<>();
        private final List<Object[]> queryArgs = new ArrayList<>();
        private final List<String> updateSqls = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            querySqls.add(sql);
            queryArgs.add(args);
            List<?> rows = queryResults.isEmpty() ? List.of() : queryResults.remove();
            if (rows.isEmpty()) {
                return List.of();
            }
            return (List<T>) rows;
        }

        @Override
        public int update(String sql, Object... args) {
            updateSqls.add(sql);
            return 1;
        }
    }
}
