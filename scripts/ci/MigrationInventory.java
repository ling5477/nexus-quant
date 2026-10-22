import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.flywaydb.core.api.MigrationVersion;

/** 共享 CI 迁移身份读取；开发清单额外核对实际 classpath SQL，正式清单仍只接受冻结路径。 */
public final class MigrationInventory {
    private MigrationInventory() {}

    public static TreeMap<MigrationVersion, String> load(String sourceManifest) throws Exception {
        var manifest = new ObjectMapper().readTree(Path.of(sourceManifest).toFile());
        var expected = new TreeMap<MigrationVersion, String>();
        var pattern = Pattern.compile("^backend/nq-infra/src/main/resources/db/migration/(V([0-9]+)__[^/]+\\.sql)$");
        for (var group : manifest.path("groups")) {
            if (!"INCLUDE_RUNTIME".equals(group.path("category").asText())) continue;
            for (var path : group.path("paths")) {
                var match = pattern.matcher(path.asText());
                if (match.matches() && expected.put(MigrationVersion.fromVersion(match.group(2)), match.group(1)) != null)
                    throw new IllegalStateException("Duplicate migration version");
            }
        }
        if (expected.isEmpty()) throw new IllegalStateException("Empty migration inventory");
        if ("DEVELOPMENT_ONLY".equals(manifest.path("contractKind").asText())) {
            var scripts = new java.util.HashSet<String>();
            for (var row : manifest.path("migrations")) {
                String script = row.path("script").asText();
                if (!scripts.add(script) || !script.equals(expected.get(MigrationVersion.fromVersion(row.path("version").asText()))))
                    throw new IllegalStateException("Development migration identity mismatch");
                try (var sql = MigrationInventory.class.getResourceAsStream("/db/migration/" + script)) {
                    if (sql == null) throw new IllegalStateException("Classpath migration is missing: " + script);
                    String normalized = new String(sql.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(normalized.getBytes(StandardCharsets.UTF_8)));
                    if (!digest.equals(row.path("sha256").asText()))
                        throw new IllegalStateException("Classpath migration source checksum mismatch: " + script);
                }
            }
            if (scripts.size() != expected.size()) throw new IllegalStateException("Incomplete development migration identity");
        }
        return expected;
    }
}
