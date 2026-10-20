package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

/** 证明预期来自正式清单，并拒绝缺失、额外、同版本脚本漂移；不替代真实PG恢复证明。 */
class TradingRestartRecoverySchemaContractTest {
    private static final String PREFIX = "backend/nq-infra/src/main/resources/db/migration/";

    @Test
    void usesNumericVersionsFromIncludedFormalMigrations() throws Exception {
        var expected = scripts("V9__nine.sql", "V10__ten.sql");
        assertEquals(MigrationVersion.fromVersion("10"), expected.lastKey());
        assertEquals("V9__nine.sql", expected.firstEntry().getValue());
        TradingRestartRecoveryPostgresIntegrationTest.assertReleaseMigrationScripts(
                expected, new MigrationInfo[] {info("10", "V10__ten.sql"), info("9", "V9__nine.sql")});
    }

    @Test
    void rejectsMissingExtraOrRenamedMigrationInsteadOfTrustingCurrentVersion() throws Exception {
        var expected = scripts("V9__nine.sql", "V10__ten.sql");
        assertThrows(AssertionError.class, () ->
                TradingRestartRecoveryPostgresIntegrationTest.assertReleaseMigrationScripts(
                        expected, new MigrationInfo[] {info("10", "V10__ten.sql")}));
        assertThrows(AssertionError.class, () ->
                TradingRestartRecoveryPostgresIntegrationTest.assertReleaseMigrationScripts(
                        expected, new MigrationInfo[] {info("9", "V9__nine.sql"), info("10", "V10__ten.sql"), info("11", "V11__extra.sql")}));
        assertThrows(AssertionError.class, () ->
                TradingRestartRecoveryPostgresIntegrationTest.assertReleaseMigrationScripts(
                        expected, new MigrationInfo[] {info("9", "V9__renamed.sql"), info("10", "V10__ten.sql")}));
    }

    @Test
    void rejectsEmptyOrDuplicateFormalInventory() {
        assertThrows(AssertionError.class, () -> scripts());
        assertThrows(AssertionError.class, () -> scripts("V9__one.sql", "V9__other.sql"));
    }

    private static java.util.NavigableMap<MigrationVersion, String> scripts(String... names)
            throws Exception {
        var mapper = new ObjectMapper();
        var manifest = mapper.createObjectNode();
        var group = manifest.putArray("groups").addObject();
        group.put("category", "INCLUDE_RUNTIME");
        var paths = group.putArray("paths");
        for (String name : names) paths.add(PREFIX + name);
        var excluded = manifest.withArray("groups").addObject();
        excluded.put("category", "EXCLUDE_TEST");
        excluded.putArray("paths").add(PREFIX + "V999__not_runtime.sql");
        return TradingRestartRecoveryPostgresIntegrationTest.releaseMigrationScripts(manifest);
    }

    private static MigrationInfo info(String version, String script) {
        var info = mock(MigrationInfo.class);
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        when(info.getScript()).thenReturn(script);
        return info;
    }
}
