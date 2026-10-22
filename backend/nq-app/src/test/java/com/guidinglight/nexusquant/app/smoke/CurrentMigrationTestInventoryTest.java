package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CurrentMigrationTestInventoryTest {
    @TempDir Path directory;

    @Test void discoversNextMigrationWithoutEditingExpectedVersion() throws Exception {
        Files.writeString(directory.resolve("V1__baseline.sql"),"SELECT 1;");
        assertEquals(MigrationVersion.fromVersion("1"),CurrentMigrationTestInventory.scripts(directory).lastKey());
        Files.writeString(directory.resolve("V2__extension.sql"),"SELECT 2;");
        var current=CurrentMigrationTestInventory.scripts(directory);
        assertEquals(2,current.size());
        assertEquals("V2__extension.sql",current.get(MigrationVersion.fromVersion("2")));
        assertThrows(UnsupportedOperationException.class,()->current.clear());
    }

    @Test void rejectsEmptyDuplicateGapAndUnexpectedPath() throws Exception {
        assertThrows(IllegalStateException.class,()->CurrentMigrationTestInventory.scripts(directory));
        Files.writeString(directory.resolve("V1__baseline.sql"),"SELECT 1;");
        for(String name:new String[]{"V1__duplicate.sql","V3__gap.sql","unexpected.sql"}) {
            Path file=directory.resolve(name);Files.writeString(file,"SELECT 1;");
            assertThrows(IllegalStateException.class,()->CurrentMigrationTestInventory.scripts(directory),name);
            Files.delete(file);
        }
    }
}
