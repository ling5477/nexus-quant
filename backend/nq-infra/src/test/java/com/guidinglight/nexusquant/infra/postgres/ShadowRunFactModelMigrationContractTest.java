package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ShadowRunFactModelMigrationContractTest {

    @Test
    void shouldDefineShadowRunTablesConstraintsIndexesAndComments() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V32__gate_r_shadow_run_fact_model.sql"));

        assertContains(sql, "CREATE TABLE shadow_runs");
        assertContains(sql, "CREATE TABLE shadow_run_events");
        assertContains(sql, "CREATE TABLE shadow_run_snapshots");
        assertContains(sql, "CREATE TABLE shadow_consistency_reports");
        assertContains(sql, "CONSTRAINT chk_shadow_runs_status");
        assertContains(sql, "CONSTRAINT chk_shadow_run_events_event_type");
        assertContains(sql, "CONSTRAINT chk_shadow_run_snapshots_type");
        assertContains(sql, "CONSTRAINT chk_shadow_consistency_reports_status");
        assertContains(sql, "CREATE UNIQUE INDEX idx_shadow_runs_idempotency_key");
        assertContains(sql, "CREATE INDEX idx_shadow_runs_status_created_at");
        assertContains(sql, "CREATE INDEX idx_shadow_runs_strategy_dataset");
        assertContains(sql, "CREATE INDEX idx_shadow_runs_paper_run_id");
        assertContains(sql, "CREATE INDEX idx_shadow_run_events_run_created_at");
        assertContains(sql, "CREATE INDEX idx_shadow_run_snapshots_run_type_sequence");
        assertContains(sql, "CREATE INDEX idx_shadow_consistency_reports_run_generated");
        assertContains(sql, "CREATE INDEX idx_shadow_consistency_reports_paper_generated");
        assertContains(sql, "COMMENT ON TABLE shadow_runs");
        assertContains(sql, "COMMENT ON TABLE shadow_run_events");
        assertContains(sql, "COMMENT ON TABLE shadow_run_snapshots");
        assertContains(sql, "COMMENT ON TABLE shadow_consistency_reports");
        assertContains(sql, "不代表交易授权");
        assertContains(sql, "不保存 credential material");
        assertContains(sql, "不代表 LIVE ready");
    }

    private static void assertContains(String sql, String expected) {
        assertTrue(sql.contains(expected), "missing migration fragment: " + expected);
    }
}
