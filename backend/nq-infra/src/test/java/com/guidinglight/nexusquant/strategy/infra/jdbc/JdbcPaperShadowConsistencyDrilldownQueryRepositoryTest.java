package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.LatestEventFact;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.LatestSnapshotFact;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.SnapshotFacts;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

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

class JdbcPaperShadowConsistencyDrilldownQueryRepositoryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UUID RUN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DATASET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-07-07T08:00:00Z");

    @Test
    void shouldLoadDrilldownFactsWithSelectOnlyQueriesFromAllowedShadowTables() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ShadowRun run = run();
        ShadowConsistencyReport report = report();
        SnapshotFacts snapshotFacts = new SnapshotFacts(
                4,
                1,
                1,
                1,
                1,
                NOW.plusSeconds(10),
                List.of()
        );
        LatestEventFact event = new LatestEventFact("evt-1", "COMPLETED", "COMPLETED", NOW.plusSeconds(5));
        LatestSnapshotFact snapshot = new LatestSnapshotFact(
                "snp-1",
                "ORDER_INTENT_PREVIEW",
                "shadow-order-intent-preview.v1",
                NOW.plusSeconds(10),
                "sha256-demo"
        );
        jdbcTemplate.queryResults.add(List.of(run));
        jdbcTemplate.queryResults.add(List.of(report));
        jdbcTemplate.queryResults.add(List.of(snapshotFacts));
        jdbcTemplate.queryResults.add(List.of("ORDER_INTENT_PREVIEW"));
        jdbcTemplate.queryForObjectResults.add(3L);
        jdbcTemplate.queryResults.add(List.of(event));
        jdbcTemplate.queryResults.add(List.of(snapshot));
        JdbcPaperShadowConsistencyDrilldownQueryRepository repository = new JdbcPaperShadowConsistencyDrilldownQueryRepository(
                jdbcTemplate,
                OBJECT_MAPPER
        );

        PaperShadowConsistencyDrilldownFacts facts = repository.loadDrilldownFacts(RUN_ID);

        assertEquals(Optional.of(run), facts.shadowRun());
        assertEquals(Optional.of(report), facts.latestConsistency());
        assertEquals(4, facts.snapshotFacts().totalSnapshots());
        assertEquals(1, facts.snapshotFacts().inputMarketdataSnapshots());
        assertEquals(1, facts.snapshotFacts().strategyDecisionSnapshots());
        assertEquals(1, facts.snapshotFacts().riskPreflightSnapshots());
        assertEquals(1, facts.snapshotFacts().orderIntentPreviewSnapshots());
        assertEquals(List.of("ORDER_INTENT_PREVIEW"), facts.snapshotFacts().latestSnapshotTypes());
        assertEquals(3, facts.totalEvents());
        assertEquals(Optional.of(event), facts.latestEvent());
        assertEquals(Optional.of(snapshot), facts.latestSnapshot());
        assertTrue(jdbcTemplate.updateSqls.isEmpty(), "drilldown repository must not execute writes");

        String sql = String.join("\n", jdbcTemplate.querySqls)
                + "\n"
                + String.join("\n", jdbcTemplate.queryForObjectSqls);
        String normalized = sql.toLowerCase(Locale.ROOT);
        assertTrue(normalized.contains("from shadow_runs"));
        assertTrue(normalized.contains("from shadow_consistency_reports"));
        assertTrue(normalized.contains("from shadow_run_snapshots"));
        assertTrue(normalized.contains("from shadow_run_events"));
        assertTrue(normalized.contains("where id = ?"));
        assertTrue(normalized.contains("where shadow_run_id = ?"));
        assertTrue(normalized.contains("case when snapshot_type = 'input_marketdata'"));
        assertTrue(normalized.contains("case when snapshot_type = 'strategy_decision'"));
        assertTrue(normalized.contains("case when snapshot_type = 'risk_preflight'"));
        assertTrue(normalized.contains("case when snapshot_type = 'order_intent_preview'"));
        assertTrue(normalized.contains("order by generated_at desc, created_at desc, id desc limit 1"));
        assertTrue(normalized.contains("order by captured_at desc, created_at desc, id desc"));
        assertFalse(normalized.contains("insert "));
        assertFalse(normalized.contains("update "));
        assertFalse(normalized.contains("delete "));
        for (String forbiddenTable : List.of(
                "from credential",
                "from exchange_account_credentials",
                "from account",
                "from orders",
                "from order_",
                "from ledger",
                "from paper_trading_runs",
                "from strategy_versions",
                "from marketdata_",
                "from risk_",
                "from paper_run_alerts",
                "from trade_replay_records"
        )) {
            assertFalse(normalized.contains(forbiddenTable), "drilldown SQL must not read " + forbiddenTable + ": " + sql);
        }
    }

    @Test
    void shouldReturnMissingRunFactsWithoutQueryingOtherTables() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        jdbcTemplate.queryResults.add(List.of());
        JdbcPaperShadowConsistencyDrilldownQueryRepository repository = new JdbcPaperShadowConsistencyDrilldownQueryRepository(
                jdbcTemplate,
                OBJECT_MAPPER
        );

        PaperShadowConsistencyDrilldownFacts facts = repository.loadDrilldownFacts(RUN_ID);

        assertTrue(facts.shadowRun().isEmpty());
        assertTrue(facts.latestConsistency().isEmpty());
        assertEquals(0, facts.snapshotFacts().totalSnapshots());
        assertEquals(0, facts.totalEvents());
        assertTrue(facts.latestEvent().isEmpty());
        assertTrue(facts.latestSnapshot().isEmpty());
        assertEquals(1, jdbcTemplate.querySqls.size());
        assertTrue(jdbcTemplate.queryForObjectSqls.isEmpty());
        assertTrue(jdbcTemplate.updateSqls.isEmpty());
    }

    private ShadowRun run() {
        return new ShadowRun(
                RUN_ID,
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "paper-1",
                ShadowRunStatus.COMPLETED,
                NOW.minusSeconds(3600),
                NOW,
                JsonNodeFactory.instance.objectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "req-1",
                "idem-1",
                "trace-1",
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(),
                7,
                NOW.minusSeconds(3600),
                NOW,
                NOW.minusSeconds(3500),
                null,
                NOW
        );
    }

    private ShadowConsistencyReport report() {
        return new ShadowConsistencyReport(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                RUN_ID,
                "paper-1",
                ShadowConsistencyComparisonStatus.DIVERGED,
                JsonNodeFactory.instance.objectNode().put("returnDelta", 0.12),
                JsonNodeFactory.instance.arrayNode().add("paper-shadow-diverged"),
                JsonNodeFactory.instance.arrayNode().add("diagnostic report only"),
                NOW.plusSeconds(30),
                "trace-report",
                NOW.plusSeconds(30)
        );
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final Queue<List<?>> queryResults = new ArrayDeque<>();
        private final Queue<Long> queryForObjectResults = new ArrayDeque<>();
        private final List<String> querySqls = new ArrayList<>();
        private final List<Object[]> queryArgs = new ArrayList<>();
        private final List<String> queryForObjectSqls = new ArrayList<>();
        private final List<Object[]> queryForObjectArgs = new ArrayList<>();
        private final List<String> updateSqls = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            querySqls.add(sql);
            queryArgs.add(args);
            @SuppressWarnings("unchecked")
            List<T> rows = (List<T>) (queryResults.isEmpty() ? List.of() : queryResults.remove());
            return rows;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryForObjectSqls.add(sql);
            queryForObjectArgs.add(args);
            Long value = queryForObjectResults.isEmpty() ? 0L : queryForObjectResults.remove();
            return requiredType.cast(value);
        }

        @Override
        public int update(String sql, Object... args) {
            updateSqls.add(sql);
            return 1;
        }
    }
}
