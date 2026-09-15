package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 仅对本测试新建PG加锁/撤销读权限，证明真实Spring命令的超时和异常仍闭锁。 */
@EnabledIfSystemProperty(named="nq.l6b.timingRegression",matches="true")
class L6BReconcileFailureTest {
    @Test void realDatabaseStallAndReconcileExceptionRemainBlocking() throws Exception {
        var dir=B0Processes.root().resolve("backend/nq-app/target/l6-b-timing-failure/"+UUID.randomUUID());
        Files.createDirectories(dir);
        var contract=new L6BContract(true);
        var parameters=contract.identity().put("runOrderBudget",contract.orders).put("formalTimerStarted",false);
        parameters.set("manifestEntry",contract.manifest.identity());
        Files.writeString(dir.resolve("parameters.json"),parameters.toString());
        System.out.println("L6_TIMING_FAILURE_ROOT "+dir);
        var proof=new ObjectMapper().createObjectNode();
        try(var pg=B0Processes.Pg.startBounded();var fixture=B0Fixture.create(pg);
            var venue=new B0Processes.Child(L6BVenueProcessMain.class,dir,"venue",B0Processes.cleanEnvironment()).awaitReady()) {
            String endpoint="http://127.0.0.1:"+venue.ready();
            var env=B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB",fixture.url());env.put("NQ_B0_VENUE",endpoint);env.put("NQ_B0_PROFILE","b0-test");
            L6PgBaselinePreflight.initialize(fixture,endpoint,env);
            try(var child=new B0Processes.Child(L6NqProcessMain.class,dir,"stall",env).awaitReady();
                var lock=DriverManager.getConnection(fixture.url(),"postgres", "")) {
                child.sendBounded("L6_CLOCK");
                lock.setAutoCommit(false);
                try(var statement=lock.createStatement()){statement.execute("LOCK TABLE orders IN ACCESS EXCLUSIVE MODE");}
                long start=System.nanoTime();
                assertThrows(AssertionError.class,()->child.sendBounded("L6_RECONCILE 1"));
                long elapsed=System.nanoTime()-start;
                assertTrue(elapsed>=TimeUnit.SECONDS.toNanos(9));
                assertTrue(elapsed<TimeUnit.SECONDS.toNanos(L6CommandExecution.EXECUTION_SECONDS));
                lock.rollback();
                assertThrows(IllegalStateException.class,()->child.sendBounded("L6_RECONCILE 2"));
                child.close();
                assertTrue(Files.readString(child.log).contains("QueryTimeoutException"));
                proof.put("databaseStallMillis",elapsed/1_000_000).put("lateResponseCannotBeConsumed",true);
            }
            try(var child=new B0Processes.Child(L6NqProcessMain.class,dir,"exception",env).awaitReady();
                var admin=DriverManager.getConnection(fixture.url(),"postgres", "")) {
                child.sendBounded("L6_CLOCK");
                try(var statement=admin.createStatement()){statement.execute("REVOKE SELECT ON orders FROM nq_b0_app");}
                assertThrows(AssertionError.class,()->child.sendBounded("L6_RECONCILE 1"));
                assertThrows(IllegalStateException.class,()->child.sendBounded("L6_RECONCILE 2"));
                child.close();
                assertTrue(Files.readString(child.log).contains("permission denied for table orders"));
                proof.put("realReconcileExceptionBlocking",true);
            }
        }
        proof.put("result","PASS").put("ownedSurvivors",0);
        Files.writeString(dir.resolve("proof.json"),proof.toPrettyString());
    }
}
