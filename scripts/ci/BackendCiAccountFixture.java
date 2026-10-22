/**
 * 仅用于 CI 的一次性 PostgreSQL fixture：迁移所提供的隔离库，并创建全量后端回归所需的
 * 一个 PAPER account。只读取 CI 范围的环境变量，不创建 exchange account 或 credential。
 */
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class BackendCiAccountFixture {
    private static final String FIXTURE_ACCOUNT_CODE = "ci-backend-test-account";

    private BackendCiAccountFixture() {
    }

    public static void main(String[] args) throws Exception {
        String url = requireEnv("NQ_DB_URL");
        String user = requireEnv("NQ_DB_USER");
        String password = requireEnv("NQ_DB_PASSWORD");

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .outOfOrder(false)
                .load();

        flyway.migrate();
        flyway.validate();
        MigrationInfo current = flyway.info().current();
        if (current == null || current.getVersion() == null) {
            throw new IllegalStateException(
                    "Flyway did not produce a current schema version");
        }

        MigrationInfo[] pending = flyway.info().pending();
        if (pending.length != 0) {
            throw new IllegalStateException(
                    "Flyway still has " + pending.length + " pending migrations");
        }

        String currentVersion = current.getVersion().getVersion();
        // CI 可显式选择开发来源清单；缺省仍核对原正式清单，禁止自动发现并接受新增迁移。
        var expected = new TreeMap<MigrationVersion, String>();
        if (args.length > 1) throw new IllegalArgumentException("Expected at most one source manifest path");
        var sourceManifest = args.length == 1 ? args[0] : "release/source-manifest.json";
        var manifest = new ObjectMapper().readTree(Path.of(sourceManifest).toFile());
        var migration = Pattern.compile("^backend/nq-infra/src/main/resources/db/migration/(V([0-9]+)__[^/]+\\.sql)$");
        for (var group : manifest.path("groups")) {
            if (!"INCLUDE_RUNTIME".equals(group.path("category").asText())) continue;
            for (var path : group.path("paths")) {
                var match = migration.matcher(path.asText());
                if (match.matches() && expected.put(MigrationVersion.fromVersion(match.group(2)), match.group(1)) != null)
                    throw new IllegalStateException("Duplicate formal migration version");
            }
        }
        var actual = new TreeMap<MigrationVersion, String>();
        for (var info : flyway.info().applied()) {
            if (info.getVersion() == null || !"SUCCESS".equals(info.getState().name())
                    || actual.put(info.getVersion(), info.getScript()) != null)
                throw new IllegalStateException("Invalid applied migration history");
        }
        if (expected.isEmpty() || !expected.equals(actual) || !expected.lastKey().equals(current.getVersion())) {
            throw new IllegalStateException("Backend CI fixture differs from formal migration inventory");
        }

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            insertLegacyAccount(connection);
            long legacyAccountRows = count(connection,
                    "SELECT COUNT(*) FROM accounts WHERE account_code = ? AND venue = 'PAPER' AND status = 'ACTIVE'",
                    FIXTURE_ACCOUNT_CODE);
            long exchangeAccountRows = count(connection,
                    """
                            SELECT COUNT(*)
                            FROM exchange_accounts
                            WHERE account_alias = ?
                               OR legacy_account_id IN (
                                   SELECT account_id FROM accounts WHERE account_code = ?
                               )
                            """,
                    FIXTURE_ACCOUNT_CODE,
                    FIXTURE_ACCOUNT_CODE);
            long credentialRows = count(connection, "SELECT COUNT(*) FROM exchange_account_credentials");

            if (legacyAccountRows != 1) {
                throw new IllegalStateException("Expected exactly one backend CI legacy account fixture row");
            }
            if (exchangeAccountRows != 0) {
                throw new IllegalStateException("Backend CI fixture must not create exchange_accounts rows");
            }
            if (credentialRows != 0) {
                throw new IllegalStateException("Backend CI fixture must not create credential rows");
            }
        }

        System.out.println(
                "Prepared backend CI legacy account fixture after Flyway V" + currentVersion);
    }

    private static void insertLegacyAccount(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                        INSERT INTO accounts (account_code, venue, status, tenant_id)
                        VALUES (?, 'PAPER', 'ACTIVE', '00000000-0000-4000-8000-000000000001'::uuid)
                        ON CONFLICT (account_code) DO NOTHING
                        """)) {
            statement.setString(1, FIXTURE_ACCOUNT_CODE);
            statement.executeUpdate();
        }
    }

    private static long count(Connection connection, String sql, String... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setString(i + 1, args[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
