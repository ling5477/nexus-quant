package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreationSpec;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyScheduleRepository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 独立库中的DDL与旧数据证明；直接SQL样本只验证约束，不冒充正式创建或执行资格。 */
@EnabledIfSystemProperty(named = "nq.schedule.creation.pg.required", matches = "true")
class StrategyScheduleCreationMigrationPostgresIntegrationTest {
    @Test
    void v22HistorySurvivesLockRollbackUpgradeAndImmutableReceiptGuards() throws Exception {
        String maintenance = System.getenv("TASK_SCHEDULE_CREATION_MAINTENANCE_URL"),
                user = System.getenv("TASK_SCHEDULE_CREATION_DB_USER"),
                password = System.getenv("TASK_SCHEDULE_CREATION_DB_PASSWORD");
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
        assertNotNull(user);
        assertNotNull(password);
        String name =
                "schedule_creation_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = maintenance.substring(0, maintenance.lastIndexOf('/') + 1) + name;
        Path evidence =
                Path.of(System.getProperty("nq.schedule.creation.pg.evidence"))
                        .toAbsolutePath()
                        .resolve(name);
        Files.createDirectories(evidence);
        var control = new JdbcTemplate(new DriverManagerDataSource(maintenance, user, password));
        control.setQueryTimeout(10);
        control.execute("CREATE DATABASE " + name);
        boolean passed = false;
        try {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
            jdbc.setQueryTimeout(10);
            flyway(url, user, password, "22").migrate();
            Long owner =
                    jdbc.queryForObject(
                            "INSERT INTO users(username,password_hash,enabled) VALUES('synthetic-migration-owner','synthetic-not-a-login',true) RETURNING id",
                            Long.class);
            Long account =
                    jdbc.queryForObject(
                            "INSERT INTO accounts(account_code,venue,status) VALUES('synthetic-schedule-migration','BINANCE','ACTIVE') RETURNING account_id",
                            Long.class);
            jdbc.update(
                    "INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version) VALUES('migration-strategy','migration-code','Explicit synthetic legacy source','GRID','BINANCE',?,'SIM',false,'{}',1)",
                    account);
            jdbc.update(
                    "INSERT INTO strategy_schedules(schedule_job_id,strategy_id,schedule_type,cron_expr,timezone,enabled,window_config,dedup_scope,exchange_code,account_id,trade_env) VALUES('migration-plan','migration-strategy','CRON','0 * * * * *','UTC',false,'{\"value\":9007199254740993.123456789012345678}','REQUEST','BINANCE',?,'SIM')",
                    account);
            Map<String, String> before = hashes(jdbc);
            Files.writeString(
                    evidence.resolve("legacy-before.json"),
                    new ObjectMapper().writeValueAsString(before));
            try (var lock = DriverManager.getConnection(url, user, password)) {
                lock.setAutoCommit(false);
                try (var statement = lock.createStatement()) {
                    statement.setQueryTimeout(10);
                    statement.execute("LOCK TABLE strategy_schedules IN ACCESS EXCLUSIVE MODE");
                }
                FlywayException blocked =
                        assertThrows(
                                FlywayException.class,
                                () -> flyway(url, user, password, "23").migrate());
                boolean lockTimeout = false;
                for (Throwable cause = blocked; cause != null; cause = cause.getCause())
                    if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState()))
                        lockTimeout = true;
                assertTrue(
                        lockTimeout, "expected lock timeout rather than another migration failure");
                lock.rollback();
            }
            assertEquals("22", version(jdbc));
            assertTrue(
                    jdbc.queryForObject(
                            "SELECT to_regclass('public.strategy_schedule_creations') IS NULL",
                            Boolean.class));
            assertEquals(before, hashes(jdbc));
            flyway(url, user, password, "23").migrate();
            flyway(url, user, password, "23").validate();
            assertEquals("23", version(jdbc));
            assertEquals(before, hashes(jdbc));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM strategy_schedule_creations", Integer.class));
            var mapper =
                    new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            var plan =
                    new JdbcStrategyScheduleRepository(jdbc)
                            .findByScheduleJobId("migration-plan")
                            .orElseThrow();
            var spec =
                    new StrategyScheduleCreationSpec(
                            plan.strategyId(),
                            plan.scheduleType(),
                            plan.cronExpr(),
                            plan.timezone(),
                            plan.enabled(),
                            plan.windowConfig(),
                            plan.dedupScope());
            UUID id = UUID.randomUUID();
            String input = mapper.writeValueAsString(spec),
                    result = mapper.writeValueAsString(plan);
            String insert =
                    "INSERT INTO strategy_schedule_creations(creation_id,owner_user_id,actor,request_version,input_sha256,request_json,schedule_job_id,result_json,trace_id,completed_at) VALUES(?,?,?,1,?,?::jsonb,?,?::jsonb,?,?)";
            jdbc.update(
                    insert,
                    id,
                    owner,
                    "synthetic-migration-owner",
                    spec.fingerprint(owner),
                    input,
                    "migration-plan",
                    result,
                    "synthetic-migration-trace",
                    Timestamp.from(Instant.now()));
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE strategy_schedule_creations SET actor='changed' WHERE creation_id=?",
                                    id));
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "DELETE FROM strategy_schedule_creations WHERE creation_id=?",
                                    id));
            var wrong = mapper.readTree(result);
            ((ObjectNode) wrong).put("accountId", account + 1);
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    insert,
                                    UUID.randomUUID(),
                                    owner,
                                    "synthetic-migration-owner",
                                    spec.fingerprint(owner),
                                    input,
                                    "migration-plan",
                                    wrong.toString(),
                                    "synthetic-migration-trace",
                                    Timestamp.from(Instant.now())));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM strategy_schedule_creations", Integer.class));
            assertEquals(before, hashes(jdbc));
            Files.writeString(
                    evidence.resolve("result.json"),
                    "{\"status\":\"PASS_V22_UPGRADE_LOCK_ROLLBACK_AND_V23_IMMUTABILITY\",\"legacyTables\":"
                            + before.size()
                            + "}");
            passed = true;
        } finally {
            if (passed) {
                assertTrue(name.matches("schedule_creation_migration_[a-f0-9]{32}"));
                control.execute("DROP DATABASE " + name);
            } else Files.writeString(evidence.resolve("failed-database-retained.txt"), name);
        }
    }

    private static Flyway flyway(String url, String user, String password, String target) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .target(target)
                .load();
    }

    private static String version(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    private static Map<String, String> hashes(JdbcTemplate jdbc) {
        var result = new LinkedHashMap<String, String>();
        for (String table :
                jdbc.queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('flyway_schema_history','strategy_schedule_creations') ORDER BY tablename",
                        String.class)) {
            assertTrue(table.matches("[a-z_][a-z_0-9]*"));
            result.put(
                    table,
                    jdbc.queryForObject(
                            "SELECT coalesce(md5(string_agg(to_jsonb(t)::text,',' ORDER BY to_jsonb(t)::text)),'EMPTY') FROM "
                                    + table
                                    + " t",
                            String.class));
        }
        return result;
    }
}
