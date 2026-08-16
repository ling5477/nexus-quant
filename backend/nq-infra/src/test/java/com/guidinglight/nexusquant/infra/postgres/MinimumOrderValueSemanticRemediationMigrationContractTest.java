package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class MinimumOrderValueSemanticRemediationMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V41__gate_y6e_minimum_order_value_semantic_remediation.sql"
    );

    @Test
    void shouldUseMetadataSafeLegacyMarkingWithoutFakeBackfill() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("DEFAULT 'LEGACY_V40_REQUIRED'"));
        assertTrue(sql.contains("ALTER COLUMN minimum_order_value_evidence_class DROP DEFAULT"));
        assertFalse(sql.matches("(?s).*UPDATE\\s+pilot_instrument_observation_items.*"));
        assertFalse(sql.contains("VENUE_PUBLISHED' WHERE"));
        assertFalse(sql.contains("INSERT INTO pilot_instrument_observation_items SELECT"));
    }

    @Test
    void shouldEnforceV1V2CompatibilityAndEvidenceSemantics() throws IOException {
        String sql = Files.readString(MIGRATION);

        for (String fragment : List.of(
                "instrument-metadata-observation.v1",
                "instrument-metadata-observation.v2",
                "VENUE_PUBLISHED",
                "VENUE_NOT_PUBLISHED",
                "LEGACY_V40_REQUIRED",
                "minimum_order_value IS NULL",
                "minimum_order_value_currency IS NULL",
                "new instrument observations must use instrument-metadata-observation.v2",
                "v2 instrument items cannot use legacy evidence marking",
                "CREATE OR REPLACE FUNCTION gate_y6d_instrument_metadata_digest",
                "CREATE OR REPLACE FUNCTION gate_y6d_observation_payload_hash",
                "gate_y6e_instrument_items_canonical",
                "minimumOrderValueEvidenceClass"
        )) {
            assertTrue(sql.contains(fragment), "missing migration fragment: " + fragment);
        }
    }

    @Test
    void shouldKeepAppendOnlyAndCompleteSetGuardsInPlace() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertFalse(sql.contains("DROP TRIGGER trg_pilot_prerequisite_observations_append_only"));
        assertFalse(sql.contains("DROP TRIGGER trg_pilot_instrument_observation_items_append_only"));
        assertFalse(sql.contains("DROP TRIGGER trg_pilot_observation_set_complete"));
        assertFalse(sql.contains("DROP TRIGGER trg_pilot_instrument_items_complete"));
        assertTrue(sql.contains("SET LOCAL lock_timeout = '5s'"));
        assertTrue(sql.contains("SET LOCAL statement_timeout = '60s'"));
    }
}
