package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BacktestPublishArtifactLocatorMigrationContractTest {

    @Test
    void shouldAddNullablePairedOpaqueKeysWithoutBackfillOrUnprovenUniqueness() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V37__gate_x4b_persistent_artifact_locator.sql")
        );

        assertContains(sql, "ADD COLUMN artifact_storage_key VARCHAR(128)");
        assertContains(sql, "ADD COLUMN manifest_storage_key VARCHAR(128)");
        assertContains(sql, "CONSTRAINT chk_backtest_publish_artifact_keys_pair");
        assertContains(sql, "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
        assertContains(sql, "POSITION('..' IN artifact_storage_key) = 0");
        assertContains(sql, "POSITION('..' IN manifest_storage_key) = 0");
        assertContains(sql, "NOT VALID");
        assertContains(sql, "VALIDATE CONSTRAINT chk_backtest_publish_artifact_keys_pair");
        assertContains(sql, "CREATE TRIGGER trg_backtest_publish_artifact_locator_immutable");
        assertContains(sql, "OLD.publish_status <> 'FAILED'");
        assertContains(sql, "NEW.publish_status <> 'SUCCEEDED'");
        assertContains(sql, "LEGACY_ARTIFACT_UNBOUND");
        assertFalse(sql.toUpperCase().contains("UPDATE BACKTEST_PUBLISH_RECORDS"),
                "migration must not backfill historical publish rows");
        assertFalse(sql.toUpperCase().contains("CREATE UNIQUE"),
                "storage-key uniqueness is not proven by the current provider contract");
    }

    private static void assertContains(String sql, String expected) {
        assertTrue(sql.contains(expected), "missing migration fragment: " + expected);
    }
}
