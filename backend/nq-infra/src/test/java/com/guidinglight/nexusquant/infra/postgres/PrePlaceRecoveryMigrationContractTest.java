package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PrePlaceRecoveryMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V45__gate_y_pre_place_zero_intent_recovery.sql");

    @Test
    void keepsAttemptExactlyOnceWhileAllowingOneZeroIntentReplacement() throws IOException {
        String sql = Files.readString(MIGRATION);
        for (String fragment : java.util.List.of(
                "CREATE TABLE pilot_pre_place_recovery_decisions",
                "REPLACEMENT_ALLOWED_ZERO_INTENT",
                "PRE_PLACE_ZERO_INTENT_FAILURE",
                "replacement_ordinal = 1",
                "uq_pilot_execution_leases_single_open",
                "uq_pilot_execution_leases_single_replacement",
                "uq_pilot_execution_leases_predecessor_successor",
                "uq_pilot_execution_lease_intents_global_place",
                "uq_pilot_execution_lease_intents_global_cancel",
                "REPLACEMENT_FORBIDDEN_SIDE_EFFECT_STARTED",
                "v_predecessor.status NOT IN ('EXPIRED','FAILED')",
                "v_old_session.state <> 'LIVE_RECONCILED'",
                "pilot pre-place recovery decision is immutable",
                "canonical legacy account bridge is immutable",
                "fk_exchange_accounts_canonical_legacy_account",
                "trg_gate_y45_canonical_legacy_bridge_insert",
                "gate_y45_canonical_legacy_account_code")) {
            assertTrue(sql.contains(fragment), "missing V45 invariant: " + fragment);
        }
        assertFalse(sql.contains("EXPIRED','ACTIVE"), "V45 must not re-activate an expired lease");
        assertFalse(sql.contains("UPDATE pilot_execution_leases SET status='ACTIVE'"),
                "V45 must not mutate historical lease lifecycle");
        assertFalse(sql.contains("exchange_account_id = 1"),
                "V45 must not hard-code the production account identity");
    }
}
