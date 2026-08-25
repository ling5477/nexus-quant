package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class CurrentMarketSnapshotMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V43__gate_y_current_market_snapshot.sql");

    @Test
    void shouldAddOnlyForwardMarketSnapshotColumnsWithBoundedLocks() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("SET LOCAL lock_timeout = '5s'"));
        assertTrue(sql.contains("SET LOCAL statement_timeout = '60s'"));
        for (String column : List.of("market_snapshot_digest", "market_instrument", "best_ask")) {
            assertTrue(sql.contains("ADD COLUMN " + column), "missing V43 column: " + column);
            assertTrue(sql.contains("COMMENT ON COLUMN pilot_prerequisite_observations." + column),
                    "missing V43 column comment: " + column);
        }
        assertFalse(sql.matches("(?s).*UPDATE\\s+pilot_prerequisite_observations.*"));
        assertFalse(sql.contains("ALTER FUNCTION gate_y6d_observation_payload_hash(UUID) RENAME"));
    }

    @Test
    void shouldPreserveAllV41VariantsAndRequireExactlyFiveFacts() throws IOException {
        String sql = Files.readString(MIGRATION);

        for (String fragment : List.of(
                "instrument-metadata-observation.v1",
                "instrument-metadata-observation.v2",
                "fee-schedule-observation.v1",
                "balance-snapshot-observation.v1",
                "clock-sync-observation.v1",
                "market-snapshot-observation.v1",
                "chk_pilot_observation_variant",
                "v_count <> 5",
                "exactly five typed observations",
                "CREATE OR REPLACE FUNCTION gate_y6d_observation_payload_hash",
                "CREATE OR REPLACE FUNCTION gate_y6d_validate_observation_set",
                "OKX_MARKET_TICKER",
                "okx-market-ticker.v5",
                "gate_y43_market_snapshot_digest",
                "scale(best_ask) <= 8"
        )) {
            assertTrue(sql.contains(fragment), "missing V43 invariant: " + fragment);
        }
        assertFalse(sql.contains("chk_pilot_observation_market_variant"));
        assertFalse(sql.contains("observation_type<>'MARKET_SNAPSHOT'"));
    }

    @Test
    void shouldKeepAppendOnlyTriggersAndCommentNewFunctions() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertFalse(sql.contains("DROP TRIGGER trg_pilot_prerequisite_observations_append_only"));
        assertFalse(sql.contains("DROP TRIGGER trg_pilot_instrument_observation_items_append_only"));
        assertTrue(sql.contains("COMMENT ON FUNCTION gate_y43_market_snapshot_digest"));
        assertTrue(sql.contains("COMMENT ON FUNCTION gate_y43_guard_market_snapshot_insert"));
        assertTrue(sql.contains("COMMENT ON FUNCTION gate_y6d_observation_payload_hash"));
        assertTrue(sql.contains("COMMENT ON FUNCTION gate_y6d_validate_observation_set"));
        assertTrue(sql.contains("COMMENT ON TRIGGER trg_gate_y43_market_snapshot_insert"));
    }
}
