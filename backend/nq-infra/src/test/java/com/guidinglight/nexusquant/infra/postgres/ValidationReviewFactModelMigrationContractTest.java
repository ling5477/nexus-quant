package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ValidationReviewFactModelMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V33__gate_v_validation_review_fact_model.sql"
    );

    @Test
    void shouldUseNextMigrationVersionAndCreateOnlyReviewFactTables() throws IOException {
        String sql = Files.readString(MIGRATION);
        List<String> createTables = sql.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("CREATE TABLE "))
                .toList();

        assertEquals(List.of(
                "CREATE TABLE validation_review_cases (",
                "CREATE TABLE validation_review_events ("
        ), createTables);
        assertTrue(MIGRATION.getFileName().toString().startsWith("V33__"));
        assertFalse(sql.contains("ALTER TABLE shadow_"));
        assertFalse(sql.contains("ALTER TABLE orders"));
        assertFalse(sql.contains("ALTER TABLE accounts"));
        assertFalse(sql.contains("ALTER TABLE ledger"));
    }

    @Test
    void shouldDefineRequiredConstraintsIndexesAndComments() throws IOException {
        String sql = Files.readString(MIGRATION);

        for (String required : List.of(
                "chk_validation_review_cases_state",
                "chk_validation_review_cases_severity",
                "chk_validation_review_cases_version",
                "chk_validation_review_cases_state_times",
                "chk_validation_review_cases_time_order",
                "chk_validation_review_events_legal_transition",
                "fk_validation_review_cases_owner",
                "fk_validation_review_events_case_tenant",
                "ON DELETE RESTRICT",
                "uq_validation_review_events_case_idempotency",
                "idx_validation_review_cases_tenant_owner_state_updated",
                "idx_validation_review_cases_tenant_state_severity_updated",
                "idx_validation_review_cases_tenant_owner_updated",
                "idx_validation_review_cases_tenant_updated",
                "idx_validation_review_cases_evidence_type_source",
                "idx_validation_review_events_case_created",
                "idx_validation_review_events_tenant_actor_created",
                "idx_validation_review_events_trace_id",
                "COMMENT ON TABLE validation_review_cases",
                "COMMENT ON TABLE validation_review_events",
                "不代表交易授权",
                "不表示 LIVE ready",
                "不保存 credential material"
        )) {
            assertTrue(sql.contains(required), "missing migration fragment: " + required);
        }
    }
}
