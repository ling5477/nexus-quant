package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.config.auth.SecurityRuntimeProperties;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantTransactionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** CI 服务容器内的一次性本地应用夹具；安装身份与应用身份分离，不用于正式安装。 */
public final class LocalTenantRuntimeFixture {
    private LocalTenantRuntimeFixture() {}

    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("CI")) || args.length != 0) {
            throw new IllegalStateException("CI-only disposable fixture required");
        }
        Path output = Path.of(required("GITHUB_ENV"));
        if (!Files.isRegularFile(output)) throw new IllegalStateException("Existing CI environment file required");
        var db = TenantIsolationTestDatabase.create(null, required("NQ_DB_URL"),
                required("NQ_DB_USER"), required("NQ_DB_PASSWORD"));
        try {
            var environment = new StandardEnvironment();
            for (var source : new YamlPropertySourceLoader().load("local-fixture", new ClassPathResource("application-local.yml"))) {
                environment.getPropertySources().addLast(source);
            }
            var properties = Binder.get(environment).bind("nq.security", SecurityRuntimeProperties.class)
                    .orElseThrow(() -> new IllegalStateException("Local identity configuration required"));
            var users = new JdbcAuthUserRepository(db.admin);
            for (var user : properties.getUsers()) {
                users.upsertSeedUser(new SeedUserCommand(user.getUsername(), user.getPasswordHash(),
                        List.copyOf(user.getRoles()), user.isEnabled()));
            }
            TenantTransactionManager.verifyRuntime(db.runtimeDataSource());
            System.out.println("::add-mask::" + db.runtime);
            System.out.println("::add-mask::" + db.runtimePassword);
            System.out.println("::add-mask::" + db.url());
            Files.writeString(output, "NQ_DB_URL=" + db.url() + "\nNQ_DB_USER=" + db.runtime
                    + "\nNQ_DB_PASSWORD=" + db.runtimePassword + "\nSPRING_FLYWAY_ENABLED=false\n",
                    StandardOpenOption.APPEND);
            // 数据库随 CI 服务容器销毁；仅关闭本进程的连接池，让后续应用使用独立连接。
            db.runtimeDataSource().unwrap(com.zaxxer.hikari.HikariDataSource.class).close();
            System.out.println("Prepared latest-schema local runtime with restricted role");
        } catch (Exception failure) {
            try { db.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r")) {
            throw new IllegalStateException("Missing or invalid fixture input: " + name);
        }
        return value;
    }
}
