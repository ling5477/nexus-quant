package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import java.time.Instant;
import java.util.HashMap;

/** 原服务进程关闭后，从现存 PG 收敛已受理订单，沿正式 HTTP 检查持久结果。 */
public final class BoundedContinuousSimRestartProbeMain {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("TASK_BOUNDED_DB_URL");
        if (args.length != 3 || url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/nq_baseline_test_[a-f0-9]{32}"))
            throw new IllegalArgumentException("isolated bounded fixture required");
        var owner = new TenantIdentity(TenantId.LOCAL,"NQ_LOCAL",TenantKind.LOCAL,Long.parseLong(args[0]));
        String run = args[1];
        Instant end = Instant.parse(args[2]);
        try (var app = ForwardSimEvaluationFixture.start(url, System.getenv("TASK_BOUNDED_DB_USER"), System.getenv("TASK_BOUNDED_DB_PASSWORD"));
                var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
            String token = http.login(BoundedContinuousSimPostgresIntegrationTest.USER);
            var initial = BoundedContinuousSimPostgresIntegrationTest.status(http, token, run);
            if (!"SETTLING".equals(initial.path("lifecycleStatus").asText()))
                throw new AssertionError("lost durable settlement " + initial);
            try (var scope = TenantContext.open(owner)) {
                var driver = app.getBean(ContinuousSimRunService.class);
                var saved = new HashMap<Instant, HistoricalBar>();
                app.getBean(ContinuousSimRepository.class).recentBars(run).forEach(bar -> saved.put(bar.openTime(),bar));
                BoundedContinuousSimPostgresIntegrationTest.configure(driver,saved,end,end,true);
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
