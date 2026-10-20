package com.guidinglight.nexusquant.app.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

/** 只验证创建类型约束的fresh/upgrade兼容性；SQL回执样本不是正式业务创建证据。 */
@EnabledIfSystemProperty(named = "nq.paper.creation.pg.required", matches = "true")
class PaperRunCreationMigrationPostgresIntegrationTest {
    @Test
    void freshAndUpgradePreserveExistingReceiptsAndRejectUnsupportedKinds() throws Exception {
        String maintenance = System.getenv("TASK_PAPER_CREATION_MAINTENANCE_URL");
        String user = System.getenv("TASK_PAPER_CREATION_DB_USER");
        String password = System.getenv("TASK_PAPER_CREATION_DB_PASSWORD");
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
        assertNotNull(user);
        assertNotNull(password);
        Path evidence = Path.of(System.getProperty("nq.paper.creation.pg.evidence"));
        Files.createDirectories(evidence);
        var control = new JdbcTemplate(new DriverManagerDataSource(maintenance, user, password));
        control.setQueryTimeout(10);
        for (boolean upgrade : new boolean[] {true, false}) {
            String name =
                    "paper_creation_migration_" + UUID.randomUUID().toString().replace("-", "");
            String url = maintenance.substring(0, maintenance.lastIndexOf('/') + 1) + name;
            control.execute("CREATE DATABASE " + name);
            boolean passed = false;
            try {
                var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
                jdbc.setQueryTimeout(10);
                migration(url, user, password, upgrade ? "30" : "31").migrate();
                Long owner =
                        jdbc.queryForObject(
                                "INSERT INTO users(username,password_hash,enabled) VALUES('synthetic-paper-owner','synthetic-not-a-login',true) RETURNING id",
                                Long.class);
                UUID prior = UUID.randomUUID();
                insert(jdbc, prior, owner, "STRATEGY");
                String before = digest(jdbc, prior);
                if (upgrade) {
                    rejectKind(jdbc, owner, "PAPER_RUN");
                    migration(url, user, password, "31").migrate();
                }
                migration(url, user, password, "31").validate();
                assertEquals(before, digest(jdbc, prior));
                insert(jdbc, UUID.randomUUID(), owner, "PAPER_RUN");
                insert(jdbc, UUID.randomUUID(), owner, "SIM_RUN");
                rejectKind(jdbc, owner, "LIVE_ORDER");
                assertEquals(
                        3,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM research_creation_operations",
                                Integer.class));
                assertEquals(
                        0,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM paper_trading_runs", Integer.class));
                assertEquals(
                        0,
                        jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class));
                Files.writeString(
                        evidence.resolve((upgrade ? "upgrade" : "fresh") + ".json"),
                        "{\"status\":\"PASS\",\"version\":31,\"priorReceiptUnchanged\":true,\"paperRuns\":0,\"ledgerEntries\":0}");
                passed = true;
            } finally {
                // 只删除本测试刚创建且全部通过的独立库；失败库保留以供定位。
                if (passed) {
                    assertTrue(name.matches("paper_creation_migration_[a-f0-9]{32}"));
                    control.execute("DROP DATABASE " + name);
                } else Files.writeString(evidence.resolve(name + "-retained.txt"), name);
            }
        }
    }

    private static Flyway migration(String url, String user, String password, String target) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .target(target)
                .load();
    }

    private static void insert(JdbcTemplate jdbc, UUID id, Long owner, String kind) {
        jdbc.update(
                "INSERT INTO research_creation_operations(operation_id,owner_user_id,kind,request_json,object_id,lookup_key) VALUES(?,?,?,'{}'::jsonb,'synthetic-object','synthetic-lookup')",
                id,
                owner,
                kind);
    }

    private static String digest(JdbcTemplate jdbc, UUID id) {
        return jdbc.queryForObject(
                "SELECT md5(to_jsonb(t)::text) FROM research_creation_operations t WHERE operation_id=?",
                String.class,
                id);
    }

    private static void rejectKind(JdbcTemplate jdbc, Long owner, String kind) {
        var failure =
                assertThrows(
                        DataAccessException.class,
                        () -> insert(jdbc, UUID.randomUUID(), owner, kind));
        assertTrue(
                failure.getMostSpecificCause() instanceof SQLException sql
                        && "23514".equals(sql.getSQLState()),
                "应由类型CHECK拒绝，而不是其他数据库错误");
    }
}
