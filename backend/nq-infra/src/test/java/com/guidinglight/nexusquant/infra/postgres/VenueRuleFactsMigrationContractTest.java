package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class VenueRuleFactsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V34__gate_w3_venue_rule_facts.sql"
    );

    @Test
    void shouldUseV34AndOnlyExtendInstrumentCatalog() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(MIGRATION.getFileName().toString().startsWith("V34__"));
        assertTrue(sql.contains("ALTER TABLE instrument_catalog"));
        assertFalse(sql.contains("CREATE TABLE"));
        for (String forbidden : List.of(
                "ALTER TABLE orders",
                "ALTER TABLE accounts",
                "ALTER TABLE positions",
                "ALTER TABLE ledger",
                "api_key",
                "credential",
                "private_endpoint"
        )) {
            assertFalse(sql.toLowerCase().contains(forbidden.toLowerCase()), "forbidden migration fragment: " + forbidden);
        }
    }

    @Test
    void shouldDefinePrecisionFieldsConstraintsAndChineseComments() throws IOException {
        String sql = Files.readString(MIGRATION);

        for (String required : List.of(
                "tick_size TYPE NUMERIC(38, 18)",
                "step_size TYPE NUMERIC(38, 18)",
                "min_quantity TYPE NUMERIC(38, 18)",
                "max_limit_quantity NUMERIC(38, 18)",
                "max_market_size NUMERIC(38, 18)",
                "max_market_size_unit VARCHAR(16)",
                "max_limit_notional_usd NUMERIC(38, 18)",
                "max_market_notional_usd NUMERIC(38, 18)",
                "source_schema_version VARCHAR(64)",
                "observed_at TIMESTAMPTZ",
                "next_rule_effective_at TIMESTAMPTZ",
                "rule_checksum VARCHAR(64)",
                "chk_instrument_catalog_max_market_size_unit",
                "max_market_size_unit = 'USDT'",
                "chk_instrument_catalog_rule_checksum",
                "^[0-9a-f]{64}$",
                "chk_instrument_catalog_observed_before_synced",
                "observed_at <= synced_at",
                "chk_instrument_catalog_next_rule_after_observed",
                "next_rule_effective_at > observed_at",
                "COMMENT ON COLUMN instrument_catalog.rule_checksum",
                "COMMENT ON CONSTRAINT chk_instrument_catalog_rule_checksum"
        )) {
            assertTrue(sql.contains(required), "missing migration fragment: " + required);
        }
    }
}
