import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

/** 从显式来源清单推导当前版本；缺省保留正式发行清单，不允许遗漏或重名。 */
public final class ReleaseMigrationSmoke {
    private ReleaseMigrationSmoke() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 1) throw new IllegalArgumentException("Expected at most one source manifest path");
        var sourceManifest = args.length == 1 ? args[0] : "release/source-manifest.json";
        var manifest = new ObjectMapper().readTree(Path.of(sourceManifest).toFile());
        var expected = new TreeMap<MigrationVersion, String>();
        var pattern = Pattern.compile("^backend/nq-infra/src/main/resources/db/migration/(V([0-9]+)__[^/]+\\.sql)$");
        for (var group : manifest.path("groups")) {
            if (!"INCLUDE_RUNTIME".equals(group.path("category").asText())) continue;
            for (var path : group.path("paths")) {
                var match = pattern.matcher(path.asText());
                if (match.matches() && expected.put(MigrationVersion.fromVersion(match.group(2)), match.group(1)) != null)
                    throw new IllegalStateException("Duplicate formal migration version");
            }
        }
        if (expected.isEmpty()) throw new IllegalStateException("Empty formal migration inventory");
        var flyway = Flyway.configure()
                .dataSource(requireEnv("NQ_FLYWAY_DB_URL"), requireEnv("NQ_FLYWAY_DB_USER"),
                        requireEnv("NQ_FLYWAY_DB_PASSWORD"))
                .locations("classpath:db/migration").baselineOnMigrate(false)
                .cleanDisabled(true).outOfOrder(false).load();
        if (flyway.migrate().migrationsExecuted != expected.size())
            throw new IllegalStateException("Fresh migration count differs from reviewed formal inventory");
        flyway.validate();
        var actual = new TreeMap<MigrationVersion, String>();
        for (var info : flyway.info().applied()) {
            if (info.getVersion() == null || !"SUCCESS".equals(info.getState().name())
                    || actual.put(info.getVersion(), info.getScript()) != null)
                throw new IllegalStateException("Invalid applied migration history");
        }
        if (!expected.equals(actual) || flyway.info().pending().length != 0
                || !expected.lastKey().equals(flyway.info().current().getVersion()))
            throw new IllegalStateException("Applied migration identity differs from formal inventory");
        if (flyway.migrate().migrationsExecuted != 0)
            throw new IllegalStateException("Repeated migration must be a no-op");
        flyway.validate();
        System.out.println("RELEASE_MIGRATION_SMOKE_PASS count=" + expected.size() + " current=" + expected.lastKey());
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment: " + name);
        return value;
    }
}
