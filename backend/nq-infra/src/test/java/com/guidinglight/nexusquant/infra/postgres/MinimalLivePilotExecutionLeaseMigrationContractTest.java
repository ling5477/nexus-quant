package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class MinimalLivePilotExecutionLeaseMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V42__gate_y_minimal_live_pilot_execution_lease.sql");

    @Test
    void enforcesDurableLeaseAndDatabaseOneShotActions() throws IOException {
        String sql = Files.readString(MIGRATION);
        for (String required : List.of(
                "CREATE TABLE pilot_execution_leases",
                "CREATE TABLE pilot_execution_lease_intents",
                "CREATE TABLE pilot_execution_lease_events",
                "uq_pilot_execution_leases_single_pilot",
                "ON pilot_execution_leases ((1));",
                "PRIMARY KEY (lease_id, action)",
                "action IN ('PLACE','CANCEL')",
                "expires_at > valid_from",
                "max_notional > 0",
                "status IN ('CREATED','ACTIVE','CONSUMED','EXPIRED','CLOSED','FAILED')",
                "trg_pilot_execution_lease_intents_append_only",
                "trg_pilot_execution_lease_events_append_only",
                "gate_y_minimal_pilot_guard_lease_update",
                "SET LOCAL lock_timeout = '5s'",
                "SET LOCAL statement_timeout = '60s'"
        )) {
            assertTrue(sql.contains(required), "missing V42 contract: " + required);
        }
        assertFalse(sql.matches("(?s).*ALTER\\s+TABLE\\s+(orders|trades|ledger_entries).*"));
        assertFalse(sql.toLowerCase().contains("api_key"));
        assertFalse(sql.toLowerCase().contains("passphrase"));
    }
}
