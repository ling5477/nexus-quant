package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.test.util.ReflectionTestUtils;

/** 原服务进程关闭后，从现存 PG 收敛已受理订单，沿正式 HTTP 检查持久结果。 */
public final class BoundedContinuousSimRestartProbeMain {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("TASK_BOUNDED_DB_URL");
        if ((args.length != 3 && args.length != 4) || url == null
                || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/nq_baseline_test_[a-f0-9]{32}"))
            throw new IllegalArgumentException("isolated bounded fixture required");
        var owner = new TenantIdentity(TenantId.LOCAL,"NQ_LOCAL",TenantKind.LOCAL,Long.parseLong(args[0]));
        String run = args[1];
        Instant end = Instant.parse(args[2]);
        try (var app = ForwardSimEvaluationFixture.start(url, System.getenv("TASK_BOUNDED_DB_USER"), System.getenv("TASK_BOUNDED_DB_PASSWORD"));
                var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
            String token = http.login(BoundedContinuousSimPostgresIntegrationTest.USER);
            if (args.length == 4) {
                var before = BoundedContinuousSimPostgresIntegrationTest.status(http, token, run);
                if ("BLOCKED".equals(args[3]) && before.hasNonNull("terminatedAt"))
                    throw new AssertionError("revision failure unexpectedly committed a terminal status " + before);
                try (var scope = TenantContext.open(owner)) {
                    var driver = app.getBean(ContinuousSimRunService.class);
                    ReflectionTestUtils.setField(driver, "clock", Clock.fixed(end.plusSeconds(30), ZoneOffset.UTC));
                    ReflectionTestUtils.setField(driver, "feed", (ClosedBarMarketFeed) (first, limit) -> {
                        throw new AssertionError("terminal recovery attempted market collection");
                    });
                    driver.pollOnce(run);
                    var committed = BoundedContinuousSimPostgresIntegrationTest.status(http, token, run);
                    driver.pollOnce(run);
                    if (!committed.equals(BoundedContinuousSimPostgresIntegrationTest.status(http, token, run)))
                        throw new AssertionError("terminal poll changed persisted history " + committed);
                }
                var terminal = BoundedContinuousSimPostgresIntegrationTest.status(http, token, run);
                boolean accountingBlocked = args[3].startsWith("SIM_");
                boolean recovered = accountingBlocked
                        ? "BLOCKED".equals(terminal.path("lifecycleStatus").asText())
                                && args[3].equals(terminal.path("reason").asText())
                                && !terminal.hasNonNull("terminatedAt")
                        : args[3].equals(terminal.path("lifecycleStatus").asText()) && terminal.hasNonNull("terminatedAt");
                if (!recovered)
                    throw new AssertionError("unexpected recovered lifecycle " + terminal);
                System.out.println("BOUNDED_RESTART_" + args[3] + " pid=" + ProcessHandle.current().pid());
                return;
            }
            var initial = BoundedContinuousSimPostgresIntegrationTest.status(http, token, run);
            if (!"ADMISSION_CLOSED".equals(initial.path("lifecycleStatus").asText()))
                throw new AssertionError("unexpected state before first recovery " + initial);
            try (var scope = TenantContext.open(owner)) {
                var driver = app.getBean(ContinuousSimRunService.class);
                var saved = new HashMap<Instant, HistoricalBar>();
                app.getBean(ContinuousSimRepository.class).recentBars(run).forEach(bar -> saved.put(bar.openTime(),bar));
                BoundedContinuousSimPostgresIntegrationTest.configure(driver,saved,end,end,true);
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var first = pool.submit(() -> driver.pollOnce(run));
                    var second = pool.submit(() -> driver.pollOnce(run));
                    first.get(20, TimeUnit.SECONDS);
                    second.get(20, TimeUnit.SECONDS);
                }
                var pending = BoundedContinuousSimPostgresIntegrationTest.status(http, token, run);
                if (!"SETTLING".equals(pending.path("lifecycleStatus").asText()) || pending.path("pendingOrders").asInt() != 1)
                    throw new AssertionError("recovery lost original pending order " + pending);
                app.getBean(PaperMatchingService.class).matchOnce(100);
                driver.pollOnce(run);
                driver.pollOnce(run);
            }
            var terminal = BoundedContinuousSimPostgresIntegrationTest.status(http,token,run);
            if (!"COMPLETED".equals(terminal.path("lifecycleStatus").asText()))
                throw new AssertionError("restarted service did not finalize " + terminal);
            System.out.println("BOUNDED_RESTART_SETTLED pid=" + ProcessHandle.current().pid());
        }
    }
}
