package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;

/** 浏览器使用任务专属PG16和正式已完成研究/发布/SIM身份；退出时仅销毁自身随机数据库。 */
public final class ResearchSimComparisonBrowserFixtureMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("ready and stop paths required");
        Path ready=Path.of(args[0]).toAbsolutePath(), stop=Path.of(args[1]).toAbsolutePath();
        if (Files.exists(ready)||Files.exists(stop)||!ready.getParent().equals(stop.getParent()))
            throw new IllegalArgumentException("fresh fixture identity required");
        try (var db=TenantIsolationTestDatabase.create(null, System.getenv("TASK_EXPERIMENT_PG_URL"), "postgres", System.getenv("TASK_EXPERIMENT_PG_PASSWORD"))) {
            var owner=db.local("experiment-owner");
            try (var app=ResearchSimComparisonFixture.start(db)) {
                var binding=ResearchSimComparisonFixture.seed(app,db,owner);
                Files.writeString(ready, ResearchSimComparisonFixture.JSON.writeValueAsString(Map.of(
                        "port",app.getWebServer().getPort(),"experimentId",binding.sourceExperimentId(),
                        "validationId",binding.validationId(),"selectedTrialId",binding.selectedTrialId(),
                        "strategyVersionId",binding.strategyVersionId(),"publishId",binding.publishId(),
                        "paperRunId",binding.paperRunId(),"pid",ProcessHandle.current().pid())),StandardOpenOption.CREATE_NEW);
                long deadline=System.nanoTime()+Duration.ofMinutes(30).toNanos();
                while(!Files.exists(stop)&&System.nanoTime()<deadline)Thread.sleep(200);
            }
        }
    }
}
