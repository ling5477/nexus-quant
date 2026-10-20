package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 完整Servlet的有界新库夹具；调度与权限使用正式代码，凭据和provider全部关闭。 */
public final class ScheduledJobOperationHttpFixtureMain {
    public static final String ADMIN = "scheduler-ui-admin";
    public static final String PASSWORD = "synthetic-scheduler-ui-password";

    private ScheduledJobOperationHttpFixtureMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("create", "reopen").contains(args[0]))
            throw new IllegalArgumentException("bounded scheduler fixture mode required");
        String url = System.getenv("TASK_SCHEDULER_DB_URL");
        if (url == null
                || !url.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:54466/scheduled_operation_gui_[a-f0-9]{32}"))
            throw new IllegalArgumentException("owned scheduler GUI database required");
        Path ready = Path.of(args[1]).toAbsolutePath(), stop = Path.of(args[2]).toAbsolutePath();
        if (!ready.getParent().equals(stop.getParent())
                || Files.exists(ready)
                || Files.exists(stop))
            throw new IllegalArgumentException("fresh fixture paths required");
        var application = new SpringApplication(NexusQuantApplication.class);
        application.setAdditionalProfiles("local");
        application.setDefaultProperties(
                Map.of(
                        "spring.datasource.url",
                        url,
                        "spring.datasource.username",
                        System.getenv("TASK_SCHEDULER_DB_USER"),
                        "spring.datasource.password",
                        System.getenv("TASK_SCHEDULER_DB_PASSWORD")));
        // 命令属性优先于local profile的默认连接，避免夹具意外落到其他本地库。
        try (var context =
                application.run(
                        "--spring.profiles.active=local",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + System.getenv("TASK_SCHEDULER_DB_USER"),
                        "--spring.datasource.password="
                                + System.getenv("TASK_SCHEDULER_DB_PASSWORD"),
                        "--server.address=127.0.0.1",
                        "--server.port=0",
                        "--nq.strategy-sim.enabled=true",
                        "--nq.continuous-sim.enabled=false",
                        "--nq.env-safety.no-outbound=true",
                        "--nq.env-safety.live-enabled=false",
                        "--nq.env-safety.real-provider-enabled=false",
                        "--nq.env-safety.real-client-enabled=false",
                        "--nq.env-safety.real-exchange-enabled=false",
                        "--nq.instrument.catalog-sync.enabled=false",
                        "--nq.okx.recovery.enabled=false",
                        "--nq.okx.ws.enabled=false",
                        "--nq.binance.ws.enabled=false",
                        "--nq.account.performance.sampling-enabled=false",
                        "--nq.okx.adapter.stub-on-bootstrap-failure=false")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            if (args[0].equals("create")) {
                var encoder = context.getBean(PasswordEncoder.class);
                context.getBean(AuthSeedService.class)
                        .seedUsers(
                                List.of(
                                        new SeedUserCommand(
                                                ADMIN,
                                                encoder.encode(PASSWORD),
                                                List.of("ADMIN"),
                                                true),
                                        new SeedUserCommand(
                                                "scheduler-ui-operator",
                                                encoder.encode(PASSWORD),
                                                List.of("OPERATOR"),
                                                true),
                                        new SeedUserCommand(
                                                "scheduler-ui-viewer",
                                                encoder.encode(PASSWORD),
                                                List.of("VIEWER"),
                                                true),
                                        new SeedUserCommand(
                                                "scheduler-ui-other-admin",
                                                encoder.encode(PASSWORD),
                                                List.of("ADMIN"),
                                                true)));
            }
            String schema =
                    jdbc.queryForObject(
                            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                            String.class);
            if (!"19".equals(schema))
                throw new IllegalStateException("expected owned scheduler V19 schema");
            var mapper = context.getBean(ObjectMapper.class);
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            Files.writeString(
                    ready,
                    mapper.writeValueAsString(
                            Map.of(
                                    "port",
                                    port,
                                    "pid",
                                    ProcessHandle.current().pid(),
                                    "database",
                                    url.substring(url.lastIndexOf('/') + 1),
                                    "schema",
                                    schema,
                                    "admin",
                                    ADMIN,
                                    "mode",
                                    args[0])));
            long deadline = System.nanoTime() + Duration.ofMinutes(12).toNanos();
            while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(100);
            if (!Files.exists(stop))
                throw new IllegalStateException("bounded scheduler fixture wait expired");
        }
    }
}
