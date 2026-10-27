package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.research.StrategyExperimentWorker;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import java.util.UUID;

/** 新进程仅查询持久身份并继续原Run；不改写原失败、策略或数据输入。 */
public final class StrategyExperimentRestartProbeMain {
    public static void main(String[] args) {
        String url=System.getenv("TASK_TENANT_URL");
        if(url==null || !url.startsWith("jdbc:postgresql://127.0.0.1:")) throw new IllegalArgumentException("isolated loopback database required");
        UUID id=UUID.fromString(args[0]);
        try(var app=StrategyExperimentPostgresIntegrationTest.start(url,System.getenv("TASK_TENANT_USER"),System.getenv("TASK_TENANT_PASSWORD"))) {
            var worker=app.getBean(StrategyExperimentWorker.class);var work=app.getBean(TenantWorkAccess.class);
            for(int n=0;n<4;n++) if(!work.execute(TenantWorkAccess.Kind.EXPERIMENT,id.toString(),()->worker.advance(id))) throw new AssertionError("original trial did not resume");
            if(work.execute(TenantWorkAccess.Kind.EXPERIMENT,id.toString(),()->worker.advance(id)))throw new AssertionError("completed trial executed again");
            System.out.println("EXPERIMENT_RESTART_VERIFIED pid="+ProcessHandle.current().pid());
        }
    }
}
