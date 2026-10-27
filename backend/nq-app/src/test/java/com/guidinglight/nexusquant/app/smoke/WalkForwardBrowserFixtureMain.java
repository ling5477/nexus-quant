package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.research.StrategyExperimentService;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** 浏览器通过真实Dispatcher验证；独立PG16数据库在结束时销毁，原公开样本只读复用。 */
public final class WalkForwardBrowserFixtureMain {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("ready and stop paths required");
        Path ready=Path.of(args[0]).toAbsolutePath(),stop=Path.of(args[1]).toAbsolutePath();
        if(Files.exists(ready)||Files.exists(stop)||!ready.getParent().equals(stop.getParent()))throw new IllegalArgumentException("fresh fixture identity required");
        try(var db=TenantIsolationTestDatabase.create(null,System.getenv("TASK_EXPERIMENT_PG_URL"),"postgres",System.getenv("TASK_EXPERIMENT_PG_PASSWORD"))) {
            var owner=db.local("experiment-owner");
            try(var app=StrategyExperimentPostgresIntegrationTest.start(db.url(),db.runtime,db.runtimePassword)) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                var templates=db.as(owner,()->WalkForwardPostgresIntegrationTest.seed(app));
                var experiments=app.getBean(StrategyExperimentService.class);var sources=new HashMap<String,String>();
                for(String type:templates.keySet()) {
                    var grid=WalkForwardPostgresIntegrationTest.JSON.readTree(type.contains("SMA")?"{\"window\":[4,20],\"investedExposure\":[\"1\"]}":"{\"entryLookbackBars\":[10,20],\"exitLookbackBars\":[4],\"investedExposure\":[\"1\"]}");
                    var view=db.as(owner,()->experiments.create(UUID.randomUUID(),new StrategyExperimentService.Request(templates.get(type),null,grid),owner.userId()));
                    WalkForwardPostgresIntegrationTest.advance(app,view.experimentId());WalkForwardPostgresIntegrationTest.advance(app,view.experimentId());
                    sources.put(type,view.experimentId().toString());
                }
                db.admin.update("UPDATE scheduled_job_controls SET enabled=true,next_run_at=clock_timestamp() WHERE job_key='STRATEGY_EXPERIMENTS'");
                Files.writeString(ready,WalkForwardPostgresIntegrationTest.JSON.writeValueAsString(Map.of("port",app.getWebServer().getPort(),"sources",sources,"templates",templates,"pid",ProcessHandle.current().pid())),StandardOpenOption.CREATE_NEW);
                long deadline=System.nanoTime()+Duration.ofMinutes(30).toNanos();
                while(!Files.exists(stop)&&System.nanoTime()<deadline)Thread.sleep(200);
            }
        }
    }
}
