/**
 * CI-only disposable PostgreSQL fixture. It migrates the supplied database and inserts one
 * PAPER account required by the complete backend regression; it never creates exchange account
 * or credential rows and reads only CI-scoped environment variables.
 */
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

public final class BackendCiLegacyAccountFixture {
    private static final String FIXTURE_ACCOUNT_CODE = "ci-backend-test-account";

    private BackendCiLegacyAccountFixture() {
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
                        INSERT INTO accounts (account_code, venue, status)
                        VALUES (?, 'PAPER', 'ACTIVE')
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
