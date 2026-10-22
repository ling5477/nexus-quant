package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.strategy.application.StrategyRunRecoveryService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 新 JVM 只持有非 owner 运行身份；从数据库恢复任务，暂停租户不能自动恢复。 */
public final class TenantIsolationRestartProbeMain {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("TASK_TENANT_URL");
        if(url==null || !url.startsWith("jdbc:postgresql://127.0.0.1:"))throw new IllegalArgumentException("isolated loopback database required");
        try(var app=TenantIsolationHttpIntegrationTest.start(url,System.getenv("TASK_TENANT_USER"),System.getenv("TASK_TENANT_PASSWORD"));
                var http=new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
            String runA=System.getenv("TASK_TENANT_RUN_A"),runB=System.getenv("TASK_TENANT_RUN_B");
            try {
                app.getBean(TenantWorkAccess.class).restore(TenantWorkAccess.Kind.PAPER,runA);
                throw new AssertionError("paused work restored after process restart");
            } catch(DataAccessException rejected) {
                if(!rejected.getMostSpecificCause().getMessage().contains("TENANT_SUSPENDED"))throw rejected;
            }
            int matched=app.getBean(PaperMatchingService.class).matchOnce(64);
            if(matched!=Integer.parseInt(System.getenv("TASK_TENANT_MATCHES")))throw new AssertionError("restart matching count differs");
            if(app.getBean(PaperMatchingService.class).matchOnce(64)!=0)throw new AssertionError("duplicate matching after restart");
            app.getBean(StrategyRunRecoveryService.class).recoverAll();
            if(app.getBean(com.guidinglight.nexusquant.scheduler.scheduling.LedgerReconcileScheduler.class).reconcileOnce()!=0)
                throw new AssertionError("tenant canonical reconciliation differs after restart");
            String a=http.login("http-a"),b=http.login("http-b");
            for(String[] item:new String[][]{{a,runA},{b,runB}}) {
                var facts=http.ok("GET","/api/paper-trading/strategy-sim/runs/"+item[1]+"/facts",item[0],null);
                if(facts.path("orders").size()!=1 || facts.path("trades").size()!=1)throw new AssertionError("restart canonical facts differ");
                http.ok("GET","/api/paper-trading/strategy-sim/analysis/runs/"+item[1]+"/performance",item[0],null);
            }
            if(app.getBean(JdbcTemplate.class).queryForObject("SELECT count(*) FROM orders",Integer.class)!=0
                    || TenantContext.currentTenantId().isPresent())throw new AssertionError("restart worker leaked tenant context");
            System.out.println("TENANT_RESTART_VERIFIED pid="+ProcessHandle.current().pid()+" matched="+matched);
        }
    }
}
