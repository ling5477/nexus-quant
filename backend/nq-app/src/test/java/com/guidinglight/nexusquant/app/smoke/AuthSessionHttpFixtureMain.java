package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 仅供真实浏览器验证短会话；新建独立数据库，不改变用户正在体验的18488环境。 */
public class AuthSessionHttpFixtureMain {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("TASK_AUTH_PG_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:3523/nq_product_[a-f0-9]{16}"))
            throw new IllegalArgumentException("owned product cluster required");
        Path ready = Path.of(args[0]); Path stop = Path.of(args[1]);
        if (Files.exists(ready) || Files.exists(stop)) throw new IllegalArgumentException("fresh fixture paths required");
        try (var db = ReleaseBaselineTestDatabase.create(url, "nq_product", "isolated-product-only");
             var context = new SpringApplication(NexusQuantApplication.class).run(
                     "--server.address=127.0.0.1", "--server.port=18489", "--spring.profiles.active=local",
                     "--spring.datasource.url=" + db.url(), "--spring.datasource.username=nq_product",
                     "--spring.datasource.password=isolated-product-only", "--nq.env-safety.no-outbound=true",
                     "--nq.env-safety.live-enabled=false", "--nq.strategy-sim.enabled=true",
                     "--nq.continuous-sim.enabled=false", "--nq.instrument.catalog-sync.enabled=false",
                     "--nq.okx.ws.enabled=false", "--nq.binance.ws.enabled=false", "--nq.okx.recovery.enabled=false",
                     "--nq.auth.bootstrap-admin.enabled=false", "--nq.public-news.enabled=false",
                     "--nq.account.performance.sampling-enabled=false", "--nq.security.access-token-ttl=PT30S",
                     "--nq.security.session-idle-timeout=PT20S", "--nq.security.session-max-lifetime=PT5M")) {
            context.getBean(AuthSeedService.class).seedUsers(List.of(new SeedUserCommand("session-review",
                    context.getBean(PasswordEncoder.class).encode("SyntheticSession123"), List.of("ADMIN", "OPERATOR", "VIEWER"), true)));
            Files.writeString(ready, "{\"port\":18489,\"database\":\"" + db.url() + "\",\"schema\":34}");
            long deadline = System.nanoTime() + java.time.Duration.ofMinutes(15).toNanos();
            while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(250);
        }
    }
}
