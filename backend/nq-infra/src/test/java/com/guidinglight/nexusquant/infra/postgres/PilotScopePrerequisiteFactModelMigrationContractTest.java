package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

class PilotScopePrerequisiteFactModelMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V40__gate_y6d_pilot_scope_prerequisite_fact_model.sql"
    );

    @Test
    void shouldCreateOnlyFrozenThreeTableModelWithoutFakeBackfill() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertEquals(List.of(
                "CREATE TABLE pilot_scope_bindings (",
                "CREATE TABLE pilot_prerequisite_observations (",
                "CREATE TABLE pilot_instrument_observation_items ("
        ), sql.lines().map(String::trim).filter(line -> line.startsWith("CREATE TABLE ")).toList());
        assertFalse(sql.matches("(?s).*UPDATE\\s+operator_approvals.*"));
        assertFalse(sql.contains("INSERT INTO pilot_scope_bindings SELECT"));
        assertFalse(sql.contains("INSERT INTO pilot_prerequisite_observations SELECT"));
        assertFalse(sql.contains("INSERT INTO execution_intents"));
        assertFalse(sql.contains("INSERT INTO execution_receipts"));
        assertTrue(sql.contains("ADD COLUMN scope_schema_version VARCHAR(64) NOT NULL DEFAULT 'approval-scope.v1'"));
        assertTrue(sql.contains("ALTER COLUMN scope_schema_version DROP DEFAULT"));
        assertTrue(sql.contains("ADD COLUMN pilot_scope_id UUID"));
    }

    @Test
    void shouldEnforceFrozenDatabaseInvariantsAndCanonicalReconstruction() throws IOException {
        String sql = Files.readString(MIGRATION);
        for (String fragment : List.of(
                "SET LOCAL lock_timeout = '5s'",
                "SET LOCAL statement_timeout = '60s'",
                "uq_pilot_scope_bindings_approval",
                "uq_pilot_observation_source_identity",
                "uq_pilot_observation_set_type",
                "trg_pilot_scope_bindings_immutable",
                "trg_pilot_prerequisite_observations_append_only",
                "trg_pilot_instrument_observation_items_append_only",
                "trg_pilot_observation_set_complete",
                "DEFERRABLE INITIALLY DEFERRED",
                "gate_y6d_pilot_scope_canonical_payload",
                "gate_y6d_reconstruct_pilot_scope_hash",
                "pilot scope hash does not match canonical reconstruction",
                "instrument metadata digest does not match item reconstruction",
                "observation payload hash does not match typed fact reconstruction",
                "legacy approval cannot authorize a materialized pilot scope",
                "fk_operator_approvals_pilot_scope",
                "created_by",
                "expires_at > v_session.execution_window_end"
        )) {
            assertTrue(sql.contains(fragment), "missing migration fragment: " + fragment);
        }
        assertFalse(sql.contains("jsonb::text"));
        assertFalse(sql.contains("SELECT MAX("));
    }

    @Test
    void shouldCommentEveryNewTableAndColumn() throws IOException {
        String sql = Files.readString(MIGRATION);
        for (String table : List.of(
                "pilot_scope_bindings",
                "pilot_prerequisite_observations",
                "pilot_instrument_observation_items"
        )) {
            assertTrue(sql.contains("COMMENT ON TABLE " + table), "missing table comment: " + table);
        }
        for (String line : sql.lines().map(String::trim).toList()) {
            if (!line.matches("[a-z][a-z0-9_]* .*[ ,]$")) {
                continue;
            }
            String column = line.substring(0, line.indexOf(' '));
            if (List.of("CONSTRAINT", "CREATE", "SELECT", "FROM", "WHERE", "ALTER", "COMMENT").contains(column)) {
                continue;
            }
            if (line.startsWith("pilot_scope_id ") || line.startsWith("observation_id ")
                    || line.startsWith("session_id ") || line.startsWith("scope_schema_version ")
                    || line.startsWith("instrument_") || line.startsWith("fee_")
                    || line.startsWith("balance_") || line.startsWith("clock_")
                    || line.startsWith("source_") || line.startsWith("observation_")
                    || line.startsWith("recorded_at ") || line.startsWith("observed_at ")
                    || line.startsWith("recorder_identity ") || line.startsWith("signed_timestamp_source ")
                    || line.startsWith("maximum_tolerated_skew_ms ") || line.startsWith("endpoint_")
                    || line.startsWith("provider_") || line.startsWith("worker_")
                    || line.startsWith("created_") || line.startsWith("symbol ")
                    || line.startsWith("trading_status ") || line.startsWith("tick_size ")
                    || line.startsWith("lot_size ") || line.startsWith("minimum_order_")
                    || line.startsWith("maker_fee_rate ") || line.startsWith("taker_fee_rate ")
                    || line.startsWith("available_balance ") || line.startsWith("observed_skew_ms ")) {
                assertTrue(sql.contains("COMMENT ON COLUMN ") && sql.contains("." + column + " IS "),
                        "missing column comment: " + column);
            }
        }
    }

    @Test
    void shouldKeepReviewedMigrationChecksumStable() throws IOException, NoSuchAlgorithmException {
        byte[] migrationBytes = Files.readAllBytes(MIGRATION);
        String migrationSql = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(migrationBytes))
                .toString();
        assertFalse(migrationSql.startsWith("\uFEFF"), "migration must not contain a UTF-8 BOM");

        // The reviewed identity is the canonical Git UTF-8/LF blob. Normalize checkout CRLF only;
        // preserve every other character and the trailing-newline count, and reject bare CR input.
        String canonicalSql = migrationSql.replace("\r\n", "\n");
        assertFalse(canonicalSql.contains("\r"), "migration must use LF or checkout CRLF line endings");
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(canonicalSql.getBytes(StandardCharsets.UTF_8))
        );
        assertEquals("1c0e486db0f3db4cdf250cb99ab0ed1e289f42d1ed522981272ee8b4c4da25e3", checksum);
    }
}
