package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TerminalLeaseRegenerationMigrationContractTest {

    @Test
    void generalizesLineageWithoutIncreasingAttemptPlaceAllowance() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V46__gate_y_attempt_level_terminal_lease_regeneration.sql"));

        assertTrue(sql.contains("DROP INDEX uq_pilot_execution_leases_single_replacement"));
        assertTrue(sql.contains("uq_pilot_execution_leases_single_origin"));
        assertTrue(sql.contains("uq_pilot_execution_leases_recovery_decision"));
        assertTrue(sql.contains("NEW.replacement_ordinal <> v_predecessor.replacement_ordinal + 1"));
        assertTrue(sql.contains("PRE_PLACE_REGENERATION_ALLOWED"));
        assertTrue(sql.contains("PRE_PLACE_TERMINAL_REGENERATION"));
        assertTrue(sql.contains("gate_y46_attempt_execution_boundary_zero"));
        assertTrue(sql.contains("predecessor_lease_id=v_predecessor.lease_id"));
        assertFalse(sql.contains("replacement_ordinal <= 2"));
        assertFalse(sql.contains("replacement_ordinal = 2"));
        assertFalse(sql.contains("DROP INDEX uq_pilot_execution_lease_intents_global_place"));
        assertFalse(sql.contains("DROP INDEX uq_pilot_execution_lease_intents_global_cancel"));
    }
}
