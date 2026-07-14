package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * V35 durable kill-switch migration 的静态安全合同。
 */
class KillSwitchStateMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V35__gate_w4_durable_kill_switch.sql"
    );

    @Test
    void migrationDefinesSafeDefaultOptimisticLockAuditAndComments() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        for (String required : new String[]{
                "CREATE TABLE kill_switch_states",
                "CREATE TABLE kill_switch_events",
                "scope VARCHAR(64) PRIMARY KEY",
                "status VARCHAR(16) NOT NULL DEFAULT 'ENGAGED'",
                "CHECK (scope = 'GLOBAL_TRADING')",
                "CHECK (status IN ('ENGAGED', 'DISENGAGED'))",
                "version BIGINT NOT NULL DEFAULT 1",
                "UNIQUE (scope, state_version)",
                "ON DELETE RESTRICT",
                "'GLOBAL_TRADING', 'ENGAGED', 1, 'DEFAULT_SAFE_BOOTSTRAP'",
                "COMMENT ON TABLE kill_switch_states",
                "COMMENT ON TABLE kill_switch_events",
                "COMMENT ON COLUMN kill_switch_states.status",
                "COMMENT ON COLUMN kill_switch_events.trace_id"
        }) {
            assertTrue(sql.contains(required), "missing migration fragment: " + required);
        }
    }
}
