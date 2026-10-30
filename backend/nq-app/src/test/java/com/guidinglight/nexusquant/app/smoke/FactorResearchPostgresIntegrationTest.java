package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;
import com.guidinglight.nexusquant.app.research.FactorResearchService;
import com.guidinglight.nexusquant.marketdata.domain.port.ResearchDatasetVersionPort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 真 PG 的导出归属、冻结字节、损坏拒绝与零业务写入；样本明确为合成数据。 */
@EnabledIfSystemProperty(named = "nq.tenant.pg.required", matches = "true")
class FactorResearchPostgresIntegrationTest {
    @Test @Timeout(180)
    void frozenExportPreservesObservationIdentityRejectsOwnersAndCorruptionWithoutWrites() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            var other = db.local("factor-other");
            var tenant = db.demo("FACTOR_OTHER_TENANT", "factor-other-tenant");
            try (var app = LongHistoricalResearchBrowserFixtureMain.start(db)) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                var service = app.getBean(FactorResearchService.class);
                for (int count : List.of(721, 2160, 4320)) {
                    UUID dataset = db.as(owner, () -> FactorResearchBrowserFixtureMain.seed(app, "BTC-USDT", count));
                    String before = fingerprint(db);
                    long started = System.nanoTime();
                    var export = db.as(owner, () -> service.export(dataset));
                    long elapsed = System.nanoTime() - started;
                    assertEquals(count, export.path("dataset").path("barCount").asInt());
                    assertEquals(export, db.as(owner, () -> service.export(dataset)));
                    assertThrows(RuntimeException.class, () -> db.as(other, () -> service.export(dataset)));
                    assertThrows(RuntimeException.class, () -> db.as(tenant, () -> service.export(dataset)));
                    assertThrows(RuntimeException.class, () -> db.as(owner, () -> service.export(UUID.randomUUID())));
                    assertEquals(before, fingerprint(db));
                    var chunk = LongHistoricalResearchBrowserFixtureMain.JSON.readTree(export.path("chunkJson").get(0).asText());
                    assertNotEquals(chunk.path("bars").get(0).path("availableAt").asText(), chunk.path("sourceObservedAt").get(0).asText());
                    assertEquals("2026-10-29T00:00:00Z", chunk.path("sourceObservedAt").get(0).asText());
                    var directory = Path.of(System.getProperty("nq.factor.evidence", "target/factor-research-evidence"));
                    Files.createDirectories(directory);
                    Files.writeString(directory.resolve("java-export-" + count + ".json"), export.toString());
                    Files.writeString(directory.resolve("export-resources-" + count + ".json"),
                            "{\"bars\":" + count + ",\"elapsedNanos\":" + elapsed + ",\"bytes\":"
                                    + export.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + "}");
                    UUID version = UUID.fromString(export.path("dataset").path("frozenInput").path("datasetVersionId").asText());
                    String original = db.admin.queryForObject("SELECT canonical_json FROM research_dataset_chunks WHERE dataset_version_id=? AND ordinal=0", String.class, version);
                    db.admin.execute("ALTER TABLE research_dataset_chunks DISABLE TRIGGER USER");
                    try {
                        db.admin.update("UPDATE research_dataset_chunks SET canonical_json=? WHERE dataset_version_id=? AND ordinal=0",
                                original.replace("\"qualityStatus\":\"OK\"", "\"qualityStatus\":\"NO\""), version);
                        assertThrows(RuntimeException.class, () -> db.as(owner, () -> service.export(dataset)));
                    } finally {
                        db.admin.update("UPDATE research_dataset_chunks SET canonical_json=? WHERE dataset_version_id=? AND ordinal=0", original, version);
                        db.admin.execute("ALTER TABLE research_dataset_chunks ENABLE TRIGGER USER");
                    }
                    assertEquals(before, fingerprint(db));
                }
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM orders", Integer.class));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class));
                assertNotNull(app.getBean(ResearchDatasetVersionPort.class));
            }
            db.migration(null).validate();
        }
    }

    static String fingerprint(TenantIsolationTestDatabase db) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        for (String table : db.admin.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename", String.class)) {
            if (!(table.startsWith("marketdata_") || table.startsWith("research_") || table.startsWith("sim_")
                    || table.startsWith("strategy_") || table.startsWith("backtest_") || table.startsWith("paper_")
                    || List.of("orders", "trades", "ledger_entries", "accounts", "positions").contains(table))) continue;
            if (!table.matches("[a-z_]+")) throw new IllegalStateException("unexpected isolated table name");
            result.put(table, db.admin.queryForObject("SELECT md5(coalesce(string_agg(row::text,'|' ORDER BY row::text),'')) FROM (SELECT to_jsonb(t) AS row FROM " + table + " t) s", String.class));
        }
        return LongHistoricalResearchBrowserFixtureMain.JSON.writeValueAsString(result);
    }
}
