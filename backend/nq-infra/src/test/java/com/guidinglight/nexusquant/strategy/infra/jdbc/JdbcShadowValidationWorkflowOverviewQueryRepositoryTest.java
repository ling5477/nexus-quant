package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewFacts.OperatorEvidenceFact;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcShadowValidationWorkflowOverviewQueryRepositoryTest {

    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHADOW_RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CONSISTENCY_REPORT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void shouldLoadOperatorFactsWithSelectOnlyQueriesFromAllowedLocalFactTables() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        OperatorEvidenceFact fact = new OperatorEvidenceFact(
                "STRATEGY_VALIDATION",
                "sv-1",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "paper-1",
                SHADOW_RUN_ID,
                CONSISTENCY_REPORT_ID,
                null,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                "COMPLETED",
                "CONSISTENT",
                null,
                null,
                Instant.parse("2026-07-08T08:59:00Z"),
                "trace-workflow"
        );
        jdbcTemplate.queryResults.add(List.of(fact));
        JdbcShadowValidationWorkflowOverviewQueryRepository repository =
                new JdbcShadowValidationWorkflowOverviewQueryRepository(jdbcTemplate);

        ShadowValidationWorkflowOverviewFacts facts = repository.loadOverviewFacts();

        assertEquals(List.of(fact), facts.operatorEvidence());
        assertTrue(jdbcTemplate.updateSqls.isEmpty(), "workflow repository must not execute writes");

        String sql = String.join("\n", jdbcTemplate.querySqls);
        String normalized = sql.toLowerCase(Locale.ROOT);
        for (String allowedTable : List.of(
                "from strategy_versions",
                "join backtest_eval_reports",
                "from backtest_publish_records",
                "from paper_trading_runs",
                "from shadow_runs",
                "from shadow_run_events",
                "from shadow_consistency_reports",
                "from paper_run_alerts",
                "from paper_run_recovery_events",
                "from trade_replay_records"
        )) {
            assertTrue(normalized.contains(allowedTable), "expected allowed table in SQL: " + allowedTable);
        }
        assertTrue(normalized.contains("limit 20"));
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
                "from paper_trading_trades"
        )) {
            assertFalse(normalized.contains(forbiddenTable), "workflow SQL must not read " + forbiddenTable + ": " + sql);
        }
        for (String rawJsonColumn : List.of(
                "event_snapshot_json",
                "request_json",
                "result_json",
                "decision_snapshot_json",
                "risk_snapshot_json",
                "market_snapshot_json",
                "metric_delta",
                "divergence_reasons",
                "limitations"
        )) {
            assertFalse(normalized.contains(rawJsonColumn), "workflow SQL must not expose raw JSONB payload: " + sql);
        }
    }

    @Test
    void shouldReturnStableEmptyFactsWhenOptionalSourcesHaveNoRows() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryResults.add(List.of());
        JdbcShadowValidationWorkflowOverviewQueryRepository repository =
                new JdbcShadowValidationWorkflowOverviewQueryRepository(jdbcTemplate);

        ShadowValidationWorkflowOverviewFacts facts = repository.loadOverviewFacts();

        assertTrue(facts.operatorEvidence().isEmpty());
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
