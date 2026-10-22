package com.guidinglight.nexusquant.app.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.flywaydb.core.api.MigrationVersion;

/** 开发集成测试从当前 SQL 源码取得预期，不从已迁移数据库反推，也不改变正式发行快照。 */
public final class CurrentMigrationTestInventory {
    private CurrentMigrationTestInventory() {}

    public static NavigableMap<MigrationVersion, String> scripts() throws IOException {
        return scripts(B0Processes.root().resolve("backend/nq-infra/src/main/resources/db/migration"));
    }

    static NavigableMap<MigrationVersion, String> scripts(Path directory) throws IOException {
        var expected = new TreeMap<MigrationVersion, String>();
        var pattern = Pattern.compile("V([1-9][0-9]*)__[A-Za-z0-9_]+\\.sql");
        try (var paths = Files.list(directory)) {
            for (var path : paths.toList()) {
                var match = pattern.matcher(path.getFileName().toString());
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || !match.matches())
                    throw new IllegalStateException("Unexpected migration path: " + path.getFileName());
                if (expected.put(MigrationVersion.fromVersion(match.group(1)), path.getFileName().toString()) != null)
                    throw new IllegalStateException("Duplicate migration version");
            }
        }
        if (expected.isEmpty() || expected.size() > 1000) throw new IllegalStateException("Invalid migration inventory size");
        int next = 1;
        for (var version : expected.keySet()) {
            if (!version.equals(MigrationVersion.fromVersion(Integer.toString(next++))))
                throw new IllegalStateException("Migration version gap");
        }
        return Collections.unmodifiableNavigableMap(expected);
    }
}
