package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

/** 新随机副本验证V18→V19、旧控制保持、非法回执与身份/终态保护，不改已执行SQL。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class ScheduledJobOperationMigrationPostgresIntegrationTest {
    @Test
    void upgradesV18AndPreservesHistoryWhileRejectingIncompleteOrRewrittenReceipts() {
        String url = System.getProperty("nq.postgres.smoke.url", "");
        URI uri = URI.create(url.substring(5));
        assertTrue(
                Set.of("127.0.0.1", "localhost").contains(uri.getHost())
                        && uri.getPort() == 54466
                        && uri.getRawQuery() == null);
        String user = System.getProperty("nq.postgres.smoke.user"),
                password = System.getProperty("nq.postgres.smoke.password");
        var admin =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                url + "?connectTimeout=5&socketTimeout=30", user, password));
        admin.setQueryTimeout(20);
        String database =
                "scheduled_operation_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE \"" + database + "\"");
        var source =
                new DriverManagerDataSource(
                        url.substring(0, url.lastIndexOf('/') + 1)
                                + database
                                + "?connectTimeout=5&socketTimeout=30",
                        user,
                        password);
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(20);
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .target("18")
                .load()
                .migrate();
        String fingerprint =
                jdbc.queryForObject(
                        "SELECT md5(string_agg(to_jsonb(x)::text,'' ORDER BY job_key)) FROM scheduled_job_controls x",
                        String.class);
        var latest =
                Flyway.configure()
                        .dataSource(source)
                        .locations("classpath:db/migration")
                        .cleanDisabled(true)
                        .target("19")
                        .load();
        assertEquals(1, latest.migrate().migrationsExecuted);
        latest.validate();
        assertEquals(0, latest.migrate().migrationsExecuted);
        assertEquals("19", latest.info().current().getVersion().toString());
        assertEquals(
                fingerprint,
                jdbc.queryForObject(
                        "SELECT md5(string_agg(to_jsonb(x)::text,'' ORDER BY job_key)) FROM scheduled_job_controls x",
                        String.class));
        assertEquals(
                0L,
                jdbc.queryForObject("SELECT count(*) FROM scheduled_job_operations", Long.class));
        long owner =
                jdbc.queryForObject(
                        "INSERT INTO users(username,password_hash,enabled) VALUES ('scheduler-migration-owner','synthetic-unused-hash',true) RETURNING id",
                        Long.class);
        UUID id = UUID.randomUUID();
        String insert =
                "INSERT INTO scheduled_job_operations(operation_id,owner_user_id,actor,operation_type,job_key,input_sha256,trace_id,status,created_at) VALUES (?,?,?,'RUN_ONCE','PAPER_MATCHING',?,'migration-scheduler-fixture','STARTING',now())";
        jdbc.update(insert, id, owner, "scheduler-migration-owner", "a".repeat(64));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                insert,
                                UUID.randomUUID(),
                                owner,
                                "scheduler-migration-owner",
                                "b".repeat(64)));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE scheduled_job_operations SET input_sha256=? WHERE operation_id=?",
                                "c".repeat(64),
                                id));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE scheduled_job_operations SET status='RUNNING' WHERE operation_id=?",
                                id));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE scheduled_job_operations SET status='COMPLETED',completed_at=now() WHERE operation_id=?",
                                id));
        jdbc.update(
                "UPDATE scheduled_job_operations SET status='COMPLETED',execution_result='SUCCESS',completed_at=now() WHERE operation_id=?",
                id);
        String completed =
                jdbc.queryForObject(
                        "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_operations x WHERE operation_id=?",
                        String.class,
                        id);
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE scheduled_job_operations SET execution_result='FAILED' WHERE operation_id=?",
                                id));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update("DELETE FROM scheduled_job_operations WHERE operation_id=?", id));
        assertEquals(
                completed,
                jdbc.queryForObject(
                        "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_operations x WHERE operation_id=?",
                        String.class,
                        id));
        UUID incomplete = UUID.randomUUID();
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO scheduled_job_operations(operation_id,owner_user_id,actor,operation_type,job_key,input_sha256,trace_id,status,created_at,completed_at,control_json) VALUES (?,?,?,'UPDATE_CONTROL','PAPER_MATCHING',?,'migration-fixture','COMPLETED',now(),now(),'{}')",
                                incomplete,
                                owner,
                                "scheduler-migration-owner",
                                "d".repeat(64)));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM orders", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM trades", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
    }
}
