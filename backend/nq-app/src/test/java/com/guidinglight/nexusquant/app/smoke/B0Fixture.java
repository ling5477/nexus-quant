package com.guidinglight.nexusquant.app.smoke;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Set;

/** 仅持有本轮新建 DB 的 bootstrap capability；启动前一次性封存，场景不持有写侧连接。 */
final class B0Fixture implements AutoCloseable {
    static final String PROFILE = "b0-test";
    static final String APP = "nq_b0_app";
    static final String READER = "nq_b0_reader";
    private final TradingRestartRecoveryPostgresIntegrationTest.RestartDatabase database;
    private boolean sealed;

    private B0Fixture(TradingRestartRecoveryPostgresIntegrationTest.RestartDatabase database) {
        this.database = database;
    }

    static B0Fixture create(B0Processes.Pg ownedPostgres) throws Exception {
        require(ownedPostgres != null);
        String ownedPostgresUrl = ownedPostgres.ownedUrl();
        // 调用者只能使用自身刚创建的容器映射；永不读取 SPRING_DATASOURCE_*。
        requireLoopbackDatabase(ownedPostgresUrl, "postgres");
        return new B0Fixture(TradingRestartRecoveryPostgresIntegrationTest.RestartDatabase.create(
                "b0", ownedPostgresUrl, "postgres", ""));
    }

    static void requireLoopbackDatabase(String url, String database) {
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            require(url.startsWith("jdbc:postgresql://") && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() > 0 && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && ("/" + database).equals(uri.getPath()));
        } catch (RuntimeException error) {
            throw unsafe();
        }
    }

    static void validate(String url, String database, String profile, String venue,
                         Map<String, String> environment, boolean started) {
        require(!started && PROFILE.equals(profile) && database != null
                && database.matches("nq_f002_b0_[a-f0-9]{32}") && environment != null);
        requireLoopbackDatabase(url, database);
        requireVenue(venue);
        // 不读取或打印秘密值：任何不在允许清单的进程环境键均拒绝，包括 credential/endpoint 注入。
        Set<String> allowed = Set.of("SYSTEMROOT", "WINDIR", "PATH", "TEMP", "TMP", "COMSPEC",
                "NQ_B0_DB", "NQ_B0_VENUE", "NQ_B0_PROFILE");
        require(environment.keySet().stream().allMatch(key -> allowed.contains(key.toUpperCase(java.util.Locale.ROOT))));
        require(!environment.containsKey("NQ_B0_DB") || url.equals(environment.get("NQ_B0_DB")));
        require(!environment.containsKey("NQ_B0_PROFILE") || profile.equals(environment.get("NQ_B0_PROFILE")));
        require(!environment.containsKey("NQ_B0_VENUE") || venue.equals(environment.get("NQ_B0_VENUE")));
    }

    static void requireVenue(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            require("http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() > 0 && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && uri.getPath().isEmpty());
        } catch (RuntimeException error) {
            throw unsafe();
        }
    }

    void initialize(boolean success, String venue, Map<String, String> childEnvironment) throws Exception {
        initialize(success, venue, childEnvironment, false);
    }

    /** B3 仅在封存前给真实 repository 最小 ENGAGE 权限；场景与 checker 不持有写连接。 */
    void initialize(boolean success, String venue, Map<String, String> childEnvironment, boolean canonicalEngage) throws Exception {
        validate(url(), name(), PROFILE, venue, childEnvironment, sealed);
        try (Connection connection = DriverManager.getConnection(url(), "postgres", "");
             var statement = connection.createStatement()) {
            var identity = statement.executeQuery("SELECT current_database()");
            require(identity.next() && name().equals(identity.getString(1)));
            identity.close();
            // CREATE DATABASE 的私有返回对象提供所有权；拒绝已有业务事实或已初始化标记。
            var fresh = statement.executeQuery("SELECT (SELECT count(*) FROM orders),"
                    + "(SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING')");
            require(fresh.next() && fresh.getLong(1) == 0 && "ENGAGED".equals(fresh.getString(2)));
            fresh.close();
            connection.setAutoCommit(false);
            statement.execute("CREATE TABLE b0_fixture_identity (identity text PRIMARY KEY, initial_kill text NOT NULL)");
            try (var seed = connection.prepareStatement("INSERT INTO b0_fixture_identity VALUES (?,?)")) {
                seed.setString(1, name());
                seed.setString(2, success ? "DISENGAGED" : "ENGAGED");
                seed.executeUpdate();
            }
            statement.execute("INSERT INTO accounts(account_code,venue,status) VALUES('b0-account','OKX','ACTIVE')");
            if (success) {
                require(statement.executeUpdate("UPDATE kill_switch_states SET status='DISENGAGED',version=version+1,"
                        + "reason_code='B0_INITIAL_FIXTURE',source='TEST_BOOTSTRAP',updated_by='B0',"
                        + "trace_id='b0-fixture',updated_at=CURRENT_TIMESTAMP-INTERVAL '1 second' "
                        + "WHERE scope='GLOBAL_TRADING' AND status='ENGAGED'") == 1);
            }
            // 角色仅在本轮容器存在；第二个 DB 复用相同角色，不授予 owner/superuser 能力。
            statement.execute("DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='nq_b0_app') THEN "
                    + "CREATE ROLE nq_b0_app LOGIN; CREATE ROLE nq_b0_reader LOGIN; END IF; END $$");
            statement.execute("GRANT USAGE ON SCHEMA public TO nq_b0_app,nq_b0_reader");
            statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO nq_b0_app,nq_b0_reader");
            statement.execute("GRANT INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO nq_b0_app");
            statement.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO nq_b0_app");
            statement.execute("REVOKE INSERT,UPDATE,DELETE ON kill_switch_states,kill_switch_events,"
                    + "b0_fixture_identity,accounts FROM nq_b0_app");
            statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            if (canonicalEngage) {
                statement.execute("GRANT UPDATE ON kill_switch_states TO nq_b0_app");
                statement.execute("GRANT INSERT ON kill_switch_events TO nq_b0_app");
            }
            connection.commit();
            sealed = true;
        }
    }

    String url() { return database.databaseUrl(); }
    String name() { return database.databaseName(); }

    Connection checker() throws Exception {
        require(sealed);
        Connection connection = DriverManager.getConnection(url(), READER, "");
        connection.setReadOnly(true);
        return connection;
    }

    static void require(boolean condition) { if (!condition) throw unsafe(); }
    private static IllegalStateException unsafe() {
        return new IllegalStateException("BLOCKED / UNSAFE_TEST_FIXTURE_TARGET");
    }

    @Override public void close() throws Exception { database.close(); }
}
