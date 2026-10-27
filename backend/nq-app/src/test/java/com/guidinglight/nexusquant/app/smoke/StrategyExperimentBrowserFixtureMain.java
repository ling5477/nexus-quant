package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.*;
import java.time.Duration;

/** 浏览器使用真实Servlet和专属PG数据库；结束标记关闭应用并销毁本夹具数据库。 */
public final class StrategyExperimentBrowserFixtureMain {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("ready and stop paths required");
        Path ready=Path.of(args[0]).toAbsolutePath(),stop=Path.of(args[1]).toAbsolutePath();
        if(Files.exists(ready)||Files.exists(stop)||!ready.getParent().equals(stop.getParent()))throw new IllegalArgumentException("fresh fixture identity required");
        try(var db=TenantIsolationTestDatabase.create(null,System.getenv("TASK_EXPERIMENT_PG_URL"),"postgres",System.getenv("TASK_EXPERIMENT_PG_PASSWORD"))) {
            var owner=db.local("experiment-owner");db.admin.update("UPDATE scheduled_job_controls SET enabled=false WHERE job_key<>'STRATEGY_EXPERIMENTS'");
            try(var app=StrategyExperimentPostgresIntegrationTest.start(db.url(),db.runtime,db.runtimePassword)) {
                var templates=db.as(owner,()->StrategyExperimentPostgresIntegrationTest.seed(app));
                Files.writeString(ready,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("port",app.getWebServer().getPort(),"templates",templates,"pid",ProcessHandle.current().pid())),StandardOpenOption.CREATE_NEW);
                long deadline=System.nanoTime()+Duration.ofMinutes(15).toNanos();
                while(!Files.exists(stop)&&System.nanoTime()<deadline)Thread.sleep(200);
            }
        }
    }
}
