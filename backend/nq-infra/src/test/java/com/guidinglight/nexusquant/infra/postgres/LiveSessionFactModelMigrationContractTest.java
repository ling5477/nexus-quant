package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class LiveSessionFactModelMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V39__gate_y2_live_session_fact_model.sql"
    );

    @Test
    void shouldCreateExactlySixTablesWithoutBackfillOrHistoricalMutation() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertEquals(List.of(
                "CREATE TABLE risk_limit_sets (",
                "CREATE TABLE live_sessions (",
                "CREATE TABLE live_session_events (",
                "CREATE TABLE operator_approvals (",
                "CREATE TABLE execution_intents (",
                "CREATE TABLE execution_receipts ("
        ), sql.lines().map(String::trim).filter(line -> line.startsWith("CREATE TABLE ")).toList());
        assertFalse(sql.contains("UPDATE orders"));
        assertFalse(sql.contains("ALTER TABLE orders"));
        assertFalse(sql.contains("ALTER TABLE exchange_accounts"));
        assertFalse(sql.contains("INSERT INTO risk_limit_sets SELECT"));
    }

    @Test
    void shouldPreserveTimeoutConstraintAndAppendOnlyContracts() throws IOException {
        String sql = Files.readString(MIGRATION);
        for (String fragment : List.of(
                "SET LOCAL lock_timeout = '5s'",
                "SET LOCAL statement_timeout = '60s'",
                "uq_live_sessions_single_non_terminal",
                "uq_live_session_events_sequence",
                "uq_execution_receipts_attempt",
                "uq_execution_intents_business_id",
                "uq_live_session_events_idempotency",
                "trg_risk_limit_sets_immutable",
                "trg_live_session_events_append_only",
                "trg_operator_approvals_append_only",
                "trg_execution_receipts_append_only",
                "trg_live_sessions_guard",
                "trg_execution_intents_guard",
                "ON UPDATE RESTRICT ON DELETE RESTRICT",
                "COMMENT ON TABLE risk_limit_sets",
                "COMMENT ON TABLE live_sessions",
                "禁止保存 raw request/response"
        )) {
            assertTrue(sql.contains(fragment), "missing migration fragment: " + fragment);
        }
        assertFalse(sql.contains("SELECT MAX("));
    }
}
