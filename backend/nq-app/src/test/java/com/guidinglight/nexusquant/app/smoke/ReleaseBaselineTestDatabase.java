package com.guidinglight.nexusquant.app.smoke;

import java.net.URI;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 固定 public 的 canonical 函数只在本夹具创建的独立数据库内验证。 */
final class ReleaseBaselineTestDatabase implements AutoCloseable {
    private final String maintenanceUrl;
    private final String name;
    private final String user;
    private final String password;
    private final String url;

    private ReleaseBaselineTestDatabase(String maintenanceUrl, String name, String user, String password) {
        this.maintenanceUrl = maintenanceUrl;
        this.name = name;
        this.user = user;
        this.password = password;
        this.url = maintenanceUrl.substring(0, maintenanceUrl.lastIndexOf('/') + 1) + name;
    }

    static ReleaseBaselineTestDatabase create() throws Exception {
        return create("nq.postgres.smoke");
    }

    static ReleaseBaselineTestDatabase create(String prefix) throws Exception {
        return create(System.getProperty(prefix + ".url", ""), System.getProperty(prefix + ".user", ""),
                System.getProperty(prefix + ".password", ""));
    }

    static ReleaseBaselineTestDatabase create(String base, String user, String password) throws Exception {
        if (!base.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException("PostgreSQL URL required");
        URI uri = URI.create(base.substring("jdbc:".length()));
        if (!java.util.Set.of("127.0.0.1", "localhost", "::1").contains(uri.getHost()) || uri.getPort() <= 0
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Explicit loopback disposable PostgreSQL required");
        }
        String name = "nq_baseline_test_" + UUID.randomUUID().toString().replace("-", "");
        String maintenance = base.substring(0, base.lastIndexOf('/') + 1) + "postgres";
        try (var connection = DriverManager.getConnection(maintenance, user, password);
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            statement.execute("CREATE DATABASE " + name);
        }
        return new ReleaseBaselineTestDatabase(maintenance, name, user, password);
    }

    String url() { return url; }

    DriverManagerDataSource source() { return new DriverManagerDataSource(url, user, password); }

    Flyway flyway() {
        return Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
                .baselineOnMigrate(false).cleanDisabled(true).target("1").load();
    }

    @Override public void close() throws Exception {
        // 名称仅来自私有创建结果；不接受外部数据库身份，也不清理共享 schema。
        if (!name.matches("nq_baseline_test_[a-f0-9]{32}")) throw new IllegalStateException("Database identity drift");
        try (var connection = DriverManager.getConnection(maintenanceUrl, user, password);
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            statement.execute("DROP DATABASE " + name + " WITH (FORCE)");
        }
    }
}
