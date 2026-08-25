package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OperatorPilotAuthorityMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V44__gate_y_operator_pilot_authority.sql");

    @Test
    void keepsForwardOnlyConditionalAuthorityAndOneShotHardGates() throws IOException {
        String sql = Files.readString(MIGRATION);
        for (String fragment : java.util.List.of(
                "CREATE TABLE operator_pilot_authorities",
                "authority_type VARCHAR(32)",
                "operator_pilot_authority_id UUID",
                "authority_type = 'STRATEGY'",
                "authority_type = 'OPERATOR_PILOT'",
                "max_notional > 0 AND max_notional <= 10.00000000",
                "max_place_count = 1 AND max_cancel_count = 1",
                "transfer_allowed = FALSE AND withdraw_allowed = FALSE",
                "credential.permission_probe_status = 'SUCCEEDED'",
                "credential.last_permission_probe_at + INTERVAL '1 minute' >= NEW.created_at",
                "best_ask = round(best_ask, 8)",
                "trg_gate_y44_operator_pilot_intent_count",
                "trg_gate_y44_close_operator_authority_with_lease",
                "UPDATE live_sessions SET authority_type = 'STRATEGY'")) {
            assertTrue(sql.contains(fragment), "missing V44 invariant: " + fragment);
        }
        assertFalse(sql.contains("INSERT INTO operator_pilot_authorities"),
                "migration must not materialize production authority rows");
        assertFalse(sql.contains("credential_reference_id = 2"),
                "migration must not hard-code the production credential identity");
        assertFalse(sql.contains("exchange_account_id = 1"),
                "migration must not hard-code the production account identity");
    }
}
