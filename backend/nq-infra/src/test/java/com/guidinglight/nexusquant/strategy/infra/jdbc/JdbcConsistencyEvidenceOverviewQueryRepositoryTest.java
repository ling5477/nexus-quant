package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewFacts.ConsistencyReportFact;

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

class JdbcConsistencyEvidenceOverviewQueryRepositoryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UUID REPORT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHADOW_RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DATASET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-07-08T10:00:00Z");

    @Test
    void shouldLoadEvidenceFactsWithSelectOnlyQueryFromAllowedLocalFactTables() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ConsistencyReportFact fact = fact();
        jdbcTemplate.queryResults.add(List.of(fact));
        JdbcConsistencyEvidenceOverviewQueryRepository repository =
                new JdbcConsistencyEvidenceOverviewQueryRepository(jdbcTemplate, OBJECT_MAPPER);

        ConsistencyEvidenceOverviewFacts facts = repository.loadOverviewFacts();

        assertEquals(List.of(fact), facts.reports());
        assertTrue(jdbcTemplate.updateSqls.isEmpty(), "consistency evidence repository must not execute writes");

        String sql = String.join("\n", jdbcTemplate.querySqls);
        String normalized = sql.toLowerCase(Locale.ROOT);
        assertTrue(normalized.contains("from shadow_consistency_reports"));
        assertTrue(normalized.contains("join shadow_runs"));
        assertTrue(normalized.contains("from shadow_run_snapshots"));
        assertTrue(normalized.contains("from shadow_run_events"));
        assertTrue(normalized.contains("metric_delta::text"));
        assertTrue(normalized.contains("divergence_reasons::text"));
        assertTrue(normalized.contains("limitations::text"));
        assertTrue(normalized.contains("limit 50"));
        assertFalse(normalized.contains("insert "));
        assertFalse(normalized.contains("update "));
        assertFalse(normalized.contains("delete "));
        assertFalse(normalized.contains("snapshot.payload"));
        assertFalse(normalized.contains(" payload"));
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
            assertFalse(normalized.contains(forbiddenTable), "overview SQL must not read " + forbiddenTable + ": " + sql);
        }
    }

    @Test
    void shouldReturnStableEmptyFactsWhenReportsAreAbsent() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryResults.add(List.of());
        JdbcConsistencyEvidenceOverviewQueryRepository repository =
                new JdbcConsistencyEvidenceOverviewQueryRepository(jdbcTemplate, OBJECT_MAPPER);

        ConsistencyEvidenceOverviewFacts facts = repository.loadOverviewFacts();

        assertTrue(facts.reports().isEmpty());
        assertTrue(jdbcTemplate.updateSqls.isEmpty());
    }

    private ConsistencyReportFact fact() {
        return new ConsistencyReportFact(
                REPORT_ID,
                SHADOW_RUN_ID,
                "paper-1",
                "sv-1",
                DATASET_ID,
                "DIVERGED",
                JsonNodeFactory.instance.objectNode().put("fillDelta", 2.0),
                JsonNodeFactory.instance.arrayNode().add("fill mismatch"),
                JsonNodeFactory.instance.arrayNode().add("diagnostic only"),
                NOW,
                "trace-report",
                "snapshot-1",
                "ORDER_INTENT_PREVIEW",
                "shadow-order-intent-preview.v1",
                "sha256-demo",
                NOW.minusSeconds(30),
                "event-1",
                "COMPLETED",
                "COMPLETED",
                NOW.minusSeconds(20)
        );
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
