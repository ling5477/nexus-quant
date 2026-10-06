package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.PasswordChangeService;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

/** 在隔离 PG16 中验证创建竞态、事务失败、改密竞态与持久化事实。 */
@EnabledIfSystemProperty(named = "nq.strategy-sim.pg.required", matches = "true")
class B6AuthPostgresTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class AuthConfig {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean AuthUserRepository repository(DataSource source) { return new JdbcAuthUserRepository(new JdbcTemplate(source)); }
        @Bean PasswordChangeService passwordChangeService(AuthUserRepository repository) { return new PasswordChangeService(repository, new BCryptPasswordEncoder()); }
    }
    @Test
    void bootstrapAndPasswordChangeAreAtomicAndCreateOnce() throws Exception {
        String url = System.getProperty("nq.strategy-sim.pg.url");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:")) { throw new IllegalArgumentException("isolated loopback DB required"); }
        var source = new DriverManagerDataSource(url, System.getProperty("nq.strategy-sim.pg.user", "postgres"), System.getProperty("nq.strategy-sim.pg.password", "disposable"));
        var jdbc = new JdbcTemplate(source);
        String database = "auth_install_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            var scoped = new DriverManagerDataSource(url.substring(0, url.lastIndexOf('/') + 1) + database, source.getUsername(), source.getPassword());
            var flyway = Flyway.configure().dataSource(scoped).locations("classpath:db/migration").load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(DataSource.class, () -> scoped);
                context.register(AuthConfig.class); context.refresh();
                var repository = context.getBean(AuthUserRepository.class);
                var service = context.getBean(PasswordChangeService.class);
                var encoder = new BCryptPasswordEncoder();
                var command = new SeedUserCommand("admin", encoder.encode("123456"), List.of("ADMIN", "OPERATOR", "VIEWER"), true);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var a = executor.submit(() -> repository.createInitialAdminIfAbsent(command));
                    var b = executor.submit(() -> repository.createInitialAdminIfAbsent(command));
                    assertNotEquals(a.get(), b.get());
                }
                var initial = repository.findByUsername("admin").orElseThrow();
                assertNotEquals("123456", initial.passwordHash()); assertTrue(encoder.matches("123456", initial.passwordHash()));
                assertTrue(initial.mustChangePassword()); assertEquals(1, initial.authVersion());
                assertEquals(List.of("ADMIN", "OPERATOR", "VIEWER"), initial.roles());
                assertFalse(repository.createInitialAdminIfAbsent(command)); assertEquals(initial, repository.findByUsername("admin").orElseThrow());
                assertThrows(BadCredentialsException.class, () -> service.changePassword("admin", 1, "wrong", "ChangedPassword123"));
                for (String invalid : List.of("123456", "short")) {
                    assertThrows(IllegalArgumentException.class, () -> service.changePassword("admin", 1, "123456", invalid));
                }
                assertEquals(initial, repository.findByUsername("admin").orElseThrow());
                service.changePassword("admin", 1, "123456", "ChangedPassword123");
                var changed = repository.findByUsername("admin").orElseThrow();
                assertFalse(changed.mustChangePassword()); assertNotNull(changed.passwordChangedAt()); assertEquals(2, changed.authVersion());
                assertTrue(encoder.matches("ChangedPassword123", changed.passwordHash()));
                assertThrows(IllegalArgumentException.class, () -> service.changePassword("admin", 2, "ChangedPassword123", "ChangedPassword123"));
                assertThrows(BadCredentialsException.class, () -> service.changePassword("admin", 1, "ChangedPassword123", "AnotherPassword123"));
                assertFalse(repository.createInitialAdminIfAbsent(command)); assertEquals(changed, repository.findByUsername("admin").orElseThrow());
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var tasks = executor.invokeAll(List.of(
                        () -> { try { service.changePassword("admin", 2, "ChangedPassword123", "RacePasswordOne"); return true; } catch (BadCredentialsException e) { return false; } },
                        () -> { try { service.changePassword("admin", 2, "ChangedPassword123", "RacePasswordTwo"); return true; } catch (BadCredentialsException e) { return false; } }));
                    assertNotEquals(tasks.get(0).get(), tasks.get(1).get());
                }
                assertEquals(3, repository.findByUsername("admin").orElseThrow().authVersion());
            }
        } finally { jdbc.execute("DROP DATABASE " + database + " WITH (FORCE)"); }
    }
}
