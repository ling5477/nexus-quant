package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import java.util.Map;

/** 新 JVM 重新装配完整 Servlet 与原后台恢复入口，不复用上个进程的安全上下文。 */
public final class PlatformAdminRestartProbeMain {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("TASK_DEMO_URL");
        if(args.length!=0 || url==null || !url.startsWith("jdbc:postgresql://127.0.0.1:"))throw new IllegalArgumentException("isolated database required");
        try(var app=PublicDemoTestHttp.start(url,System.getenv("TASK_DEMO_USER"),System.getenv("TASK_DEMO_PASSWORD"),
                "--nq.demo.registration-enabled=false","--nq.demo.mail-dispatch-enabled=false");
                var http=new PublicDemoTestHttp(app.getWebServer().getPort())) {
            if(http.send("POST","/api/auth/login",null,Map.of("username","platform-user-a","password",PublicDemoTestHttp.PASSWORD)).statusCode()!=401)
                throw new AssertionError("disabled login survived process restart");
            PublicDemoTwoUserFlowIntegrationTest.registerDriver(app);
            var denied=org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                    ()->app.getBean("demoFixtureContinuousDriver",ContinuousSimRunService.class).pollOnce(System.getenv("TASK_PLATFORM_DISABLED_RUN")));
            if(!denied.getMostSpecificCause().getMessage().contains("TENANT_SUSPENDED"))throw denied;
            if(app.getBean(PaperMatchingService.class).matchOnce(64)!=0)throw new AssertionError("disabled matching recovered after restart");
            System.out.println("PLATFORM_DISABLE_RESTART_VERIFIED pid="+ProcessHandle.current().pid());
        }
    }
}
