package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.research.StrategyExperimentWorker;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 重启进程只继续原步骤与Run；强杀切点仍使用原worker的真实经济事实提交。 */
public final class WalkForwardRestartProbeMain {
    public static void main(String[] args) {
        String url=System.getenv("TASK_TENANT_URL");
        if(url==null || !url.startsWith("jdbc:postgresql://127.0.0.1:"))throw new IllegalArgumentException("isolated loopback database required");
        UUID source=UUID.fromString(args[0]),validation=UUID.fromString(args[1]);
        try(var app=StrategyExperimentPostgresIntegrationTest.start(url,System.getenv("TASK_TENANT_USER"),System.getenv("TASK_TENANT_PASSWORD"))) {
            var work=app.getBean(TenantWorkAccess.class);var worker=app.getBean(StrategyExperimentWorker.class);var jdbc=app.getBean(JdbcTemplate.class);
            for(int n=0;n<80;n++) {
                boolean finished=work.execute(TenantWorkAccess.Kind.EXPERIMENT,source.toString(),()-> {
                    for(UUID child:jdbc.queryForList("SELECT experiment_id FROM experiment_validation_steps WHERE validation_id=? AND experiment_id IS NOT NULL",UUID.class,validation)) {
                        for(int attempt=0;attempt<2;attempt++)worker.advance(child);
                    }
                    worker.advance(source);
                    return !"RUNNING".equals(jdbc.queryForObject("SELECT status FROM experiment_validations WHERE validation_id=?",String.class,validation));
                });
                if(finished){System.out.println("WALK_FORWARD_RESTART_VERIFIED pid="+ProcessHandle.current().pid());return;}
            }
            throw new AssertionError("validation did not complete in restart process");
        }
    }
}
