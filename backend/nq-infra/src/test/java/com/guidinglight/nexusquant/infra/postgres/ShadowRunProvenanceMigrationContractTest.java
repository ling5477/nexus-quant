package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ShadowRunProvenanceMigrationContractTest {

    @Test
    void shouldAddNullableDigestConstraintsAndCommentWithoutBackfillOrUniqueness() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V36__gate_x2_shadow_run_provenance.sql")
        );

        assertContains(sql, "ADD COLUMN artifact_digest VARCHAR(64)");
        assertContains(sql, "CONSTRAINT chk_shadow_runs_artifact_digest_sha256");
        assertContains(sql, "artifact_digest IS NULL OR artifact_digest ~ '^[0-9a-f]{64}$'");
        assertContains(sql, "CONSTRAINT chk_shadow_runs_artifact_requires_publish");
        assertContains(sql, "artifact_digest IS NULL OR publish_id IS NOT NULL");
        assertContains(sql, "NOT VALID");
        assertContains(sql, "VALIDATE CONSTRAINT chk_shadow_runs_artifact_digest_sha256");
        assertContains(sql, "VALIDATE CONSTRAINT chk_shadow_runs_artifact_requires_publish");
        assertContains(sql, "COMMENT ON COLUMN shadow_runs.artifact_digest");
        assertContains(sql, "不做推测或回填");
        assertFalse(sql.toUpperCase().contains("UPDATE SHADOW_RUNS"), "migration must not backfill legacy rows");
        assertFalse(sql.toUpperCase().contains("UNIQUE"), "publish/digest must not be globally unique");
    }

    private static void assertContains(String sql, String expected) {
        assertTrue(sql.contains(expected), "missing migration fragment: " + expected);
    }
}
