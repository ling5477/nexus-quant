package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.OverviewCounts;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcStrategyValidationOverviewQueryRepositoryTest {

    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHADOW_RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-07-08T09:00:00Z");

    @Test
    void shouldLoadOverviewFactsWithSelectOnlyQueriesFromAllowedFactTables() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        LatestDecisionFact latestDecision = new LatestDecisionFact(
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "paper-1",
                SHADOW_RUN_ID,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                "COMPLETED",
                "CONSISTENT",
                NOW,
                NOW
        );
        jdbcTemplate.queryResults.add(List.of(new OverviewCounts(4, 3, 1, 1, 1, 1)));
        jdbcTemplate.queryResults.add(List.of(latestDecision));
        JdbcStrategyValidationOverviewQueryRepository repository = new JdbcStrategyValidationOverviewQueryRepository(jdbcTemplate);

        StrategyValidationOverviewFacts facts = repository.loadOverviewFacts();

        assertEquals(4, facts.totalStrategyVersions());
        assertEquals(3, facts.evaluatedStrategyVersions());
        assertEquals(1, facts.approvedForValidation());
        assertEquals(1, facts.rejectedForValidation());
        assertEquals(1, facts.needsReview());
        assertEquals(1, facts.blocked());
        assertEquals(Optional.of(latestDecision), facts.latestDecision());
        assertTrue(jdbcTemplate.updateSqls.isEmpty(), "validation overview repository must not execute writes");

        String sql = String.join("\n", jdbcTemplate.querySqls);
        String normalized = sql.toLowerCase(Locale.ROOT);
        assertTrue(normalized.contains("from strategy_versions"));
        assertTrue(normalized.contains("join backtest_eval_reports"));
        assertTrue(normalized.contains("from backtest_publish_records"));
        assertTrue(normalized.contains("from paper_trading_runs"));
        assertTrue(normalized.contains("from shadow_runs"));
        assertTrue(normalized.contains("join shadow_consistency_reports"));
        assertTrue(normalized.contains("limit 1"));
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
                "from provider_secret"
        )) {
            assertFalse(normalized.contains(forbiddenTable), "validation overview SQL must not read " + forbiddenTable + ": " + sql);
        }
    }

    @Test
    void shouldReturnStableEmptyFactsWhenNoStrategyVersionExists() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryResults.add(List.of(OverviewCounts.empty()));
        jdbcTemplate.queryResults.add(List.of());
        JdbcStrategyValidationOverviewQueryRepository repository = new JdbcStrategyValidationOverviewQueryRepository(jdbcTemplate);

        StrategyValidationOverviewFacts facts = repository.loadOverviewFacts();

        assertEquals(0, facts.totalStrategyVersions());
        assertEquals(0, facts.evaluatedStrategyVersions());
        assertTrue(facts.latestDecision().isEmpty());
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
