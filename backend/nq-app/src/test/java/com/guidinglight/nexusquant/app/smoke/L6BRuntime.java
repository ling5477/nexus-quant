package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** B专属运行器：绝对时间、相同PG、单次计划故障；正式计时后没有配置或预算修改入口。 */
final class L6BRuntime {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final L6BContract contract;
    private final ObjectNode proof=JSON.createObjectNode();
    private final List<L6DeterministicPacer.Slot> slots=new ArrayList<>();
    private final List<ObjectNode> restarts=new ArrayList<>();
    private L6BActors actors;
    private L6BFingerprint fingerprint;
    private L6BBudgets budgets;
    private L6BProjectionGuard projection;
    private L6BAdmission admission;
    private Path dir;
    private long start, reconciliationCalls;
    private int checks;
    private String container, databaseIdentity, postgresStarted;
    private long venuePid;
    L6BRuntime(boolean probe) throws Exception { contract=new L6BContract(probe); }

    void run() throws Exception {
        dir=B0Processes.root().resolve("backend/nq-app/target/l6-b/"+UUID.randomUUID());Files.createDirectories(dir);
        proof.setAll(contract.identity());proof.set("manifestEntry",contract.manifest.identity());
        proof.put("runId",dir.getFileName().toString()).put("runOrderBudget",contract.orders)
                .put("shortSmoke",false).put("formalTimerStarted",false)
                .put("HEAD",B0Processes.command("git","rev-parse","HEAD").trim())
                .put("controllerPid",ProcessHandle.current().pid());
        write("parameters.json",proof);System.out.println("L6_B_ROOT "+dir);
        admission = new L6BAdmission(row -> append("admission.ndjson", row));
        try {
            fingerprint=new L6BFingerprint(!contract.probe);write("candidate-entry.json",fingerprint.evidence());
            var preparation=L6PgBaselinePreflight.measure(contract.base,dir.resolve("baseline-preparation"));
            long baseline=preparation.evidence(contract.base).path("preparationBaselineBytes").asLong();
            long capacity=contract.deriveCapacity(baseline);
            proof.set("preStartupMemory",L6HostMemoryPreflight.verify(contract.base,capacity,L6HostMemoryPreflight.observe()));
            try(var pg=B0Processes.Pg.startL6B(contract,baseline);var fixture=B0Fixture.create(pg);
                var venue=new B0Processes.Child(L6BVenueProcessMain.class,dir,"venue",B0Processes.cleanEnvironment())) {
                container=pg.ownedContainerId();venuePid=venue.process.pid();
                String endpoint="http://127.0.0.1:"+venue.ready();
                var env=B0Processes.cleanEnvironment();env.put("NQ_B0_DB",fixture.url());env.put("NQ_B0_VENUE",endpoint);env.put("NQ_B0_PROFILE","b0-test");
                L6PgBaselinePreflight.initialize(fixture,endpoint,env);
                try(var reader=L6TransactionAccounting.wrap(fixture.checker(),"CONTROLLER_HELPER");
                    var resourceReader=L6TransactionAccounting.wrap(fixture.checker(),"SAMPLER_PG")) {
                    databaseIdentity=value(reader,"SELECT identity FROM b0_fixture_identity");
                    postgresStarted=value(reader,"SELECT pg_postmaster_start_time()::text");
                    actors=new L6BActors(container,databaseIdentity,postgresStarted,venue.process.toHandle());
                    try(var owned=actors) {
                        actors.start(0,dir,env,"INITIAL");actors.start(1,dir,env,"INITIAL");
                        proof.set("paper",JSON.readTree(command(0,"L6_PAPER")));http(endpoint,"L5_OPEN");
                        var initial=L6PgStorageObservation.collect(resourceReader,container,B0Processes::command);
                        assertEquals(capacity,initial.path("pgTmpfsCapacityBytes").asLong());
                        proof.set("runCapacity",contract.capacityEvidence(baseline,initial.path("pgTmpfsUsedBytes").asLong(),capacity));
                        proof.set("formalEntryMemory",L6HostMemoryPreflight.verify(contract.base,capacity,L6HostMemoryPreflight.observe())
                                .put("pgStarted",true).put("venueStarted",true).put("nqStarted",true));
                        proof.put("container",container).put("venuePid",venuePid).put("databaseIdentity",databaseIdentity).put("postgresStartedAt",postgresStarted);
                        var entryActors=JSON.createArrayNode();for(int i=0;i<2;i++)entryActors.add(JSON.readTree(command(i,"L6_METRICS")));
                        long transactionBaseline=number(reader,"SELECT xact_commit+xact_rollback FROM pg_stat_database WHERE datname=current_database()");
                        budgets=new L6BBudgets(contract,transactionBaseline,Files.getFileStore(dir).getUsableSpace(),entryActors);
                        projection=new L6BProjectionGuard(contract,capacity);
                        proof.set("hardBudgetPreflight",budgets.evidence());write("preflight.json",proof);
                        reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);reader.setAutoCommit(false);
                        try(var statement=reader.createStatement()){statement.execute("SET statement_timeout='2000ms'");reader.commit();}
                        try(var resources=new L6BResources(resourceReader,dir,actors,venue,endpoint,container,contract,capacity,proof.path("preStartupMemory").path("availableHostMemoryAtEntry").asLong())) {
                            fingerprint.verify();
                            start=System.nanoTime();proof.put("startedAt",Instant.now().toString()).put("formalTimerStarted",!contract.probe);
                            try(var sampler=resources.sampler(contract.timing,start,dir.resolve("resources.ndjson"))) {
                                sampler.observeWith(row->{budgets.observe(row);projection.observe(row,elapsed());});
                                sampler.sample();sampler.start();drive(reader,endpoint,env,pg,venue,sampler);
                                if(!projection.stopped())sampler.requireComplete();
                                if(projection.stopped())throw new IllegalStateException(L6BProjectionGuard.RESULT);
                                var end=checkpoint(reader,endpoint,"FINAL");reader.commit();proof.set("final",end);
                                budgets.checkFinal(number(reader,"SELECT xact_commit+xact_rollback FROM pg_stat_database WHERE datname=current_database()"));reader.commit();
                                assertEquals(0,end.path("backlog").asLong());assertEquals(0,end.path("idleInTransaction").asLong());
                                assertEquals(3,restarts.size());
                                var finalMetrics=proof.putArray("finalActors");
                                for(int i=0;i<2;i++) {
                                    var metric=JSON.readTree(command(i,"L6_METRICS"));finalMetrics.add(metric);
                                    assertEquals(0,metric.path("pending").asInt());assertEquals(0,metric.path("commandQueue").path("queueSize").asInt());
                                    assertEquals(0,metric.path("candidateAge").path("eligibleCandidateCount").asInt());
                                    assertTrue(metric.path("candidateAge").path("oldestCandidateAgeMillis").isNull());
                                }
                                var venueFinal=http(endpoint,null);assertEquals(0,venueFinal.path("executorQueue").asInt());
                                assertEquals(1,venueFinal.path("boundedDelayApplied").asInt());
                                proof.set("venueFinal",venueFinal);proof.set("sampling",sampler.summary());
                                for(var event:restarts)assertTrue(event.path("freshChainAfterRecovery").isObject(),"new active chain missing after restart");
                            }
                        }
                    }
                }
            }
            assertFalse(ProcessHandle.of(venuePid).map(ProcessHandle::isAlive).orElse(false));
            assertTrue(B0Processes.command("docker","ps","-a","--filter","id="+container,"--format","{{.ID}}").isBlank());
            proof.put("cleanup","PASS").put("ownedSurvivors",0)
                    .put("result",contract.probe?"L6_B_SHORT_PROBE_MEASURED":"L6_B_180MIN_MEASURED_PENDING_QUALIFICATION");
        } catch(Exception | AssertionError failure) {
            proof.put("result","FAILED").put("failure",failure.toString());throw failure;
        } finally {
            if(actors!=null){proof.set("generations",actors.evidence());proof.put("ownedNqSurvivors",actors.survivors());}
            if(budgets!=null)proof.set("hardBudgets",budgets.evidence());
            if(projection!=null)proof.set("projection",projection.evidence());
            if(admission!=null)proof.set("admission",admission.summary());
            proof.set("restarts",JSON.valueToTree(restarts));proof.put("reconciliationCalls",reconciliationCalls);
            proof.put("completedAt",Instant.now().toString());
            try{contract.verifyUnchanged();if(fingerprint!=null){fingerprint.verify();proof.put("candidateUnchanged",true);}}catch(Exception drift){proof.put("result","FROZEN_INPUT_DRIFT");throw drift;}
            finally{
                write("proof.json",proof);
                if(budgets!=null) {
                    try {
                        var files=budgets.finalFiles(dir);write("evidence-exit.json",files);
                    } catch(Exception failure) {
                        proof.put("result","FAILED").put("evidenceExitFailure",failure.toString());write("proof.json",proof);throw failure;
                    }
                }
            }
        }
    }

    private void drive(Connection reader,String endpoint,Map<String,String> env,B0Processes.Pg pg,
                       B0Processes.Child venue,L6ResourceSampler sampler) throws Exception {
        var pacer=new L6DeterministicPacer(System::nanoTime,start,contract.manifest.intervalNanos(),contract.timing,projection::producerAllowed);
        long observer=0,nextCheck=0,drainOrders=-1;int restart=0;boolean delayArmed=false;
        String phase="";long phaseStarted=0;
        while(elapsed()<contract.timing.total()) {
            sampler.checkHealthy();actors.requireHealthy();
            long now=elapsed();String current=projection.stopped()?"DRAIN":contract.timing.samplePhase(now);
            if(!current.equals(phase)) {
                if(!phase.isEmpty())phase(phase,phaseStarted,now);
                phase=current;phaseStarted=now;
                if("DRAIN".equals(phase)){drainOrders=number(reader,"SELECT count(*) FROM orders");reader.commit();proof.put("ordersAtDrainStart",drainOrders);}
            }
            if(projection.drainExpired(now))break;
            boolean restartDue=restart<3 && now>=contract.restartSeconds[restart]*L6BContract.SECOND && !projection.stopped();
            if(restartDue && restart==2 && !delayArmed){http(endpoint,"L6B_DELAY_NEXT_PLACE");delayArmed=true;}
            long orders=number(reader,"SELECT count(*) FROM orders");
            boolean blocked=admission.observe(reader,sample(reader),elapsed());reader.commit();
            int before=slots.size();final int target=restartDue?new int[]{0,1,0}[restart]:-1;
            pacer.poll(blocked,slot->{
                contract.capacity().reserve(orders,1);
                int actor=target>=0?target:(int)(slot%2);
                var generation=actors.get(actor);
                String reply=command(actor,"L6_EMIT "+slot+" "+(start+contract.timing.activeEnd()+generation.childClockOffset));
                var result=JSON.readTree(reply);assertEquals(slot,result.path("slotIndex").asLong());
                if ("SKIPPED_BUSY".equals(result.path("outcome").asText())) {
                    assertTrue(result.path("admissionRolledBack").asBoolean());
                    assertEquals("strategy_run_active", result.path("reason").asText());
                    append("admission-rejections.ndjson",JSON.createObjectNode().put("elapsedNanos",elapsed()).set("response",result));
                    throw new L6DeterministicPacer.AdmissionBusy();
                }
                return result.path("logicalOrderId").asText();
            },slot->{slots.add(slot);append("pacing.ndjson",JSON.valueToTree(slot));});
            boolean emitted=slots.subList(before,slots.size()).stream().anyMatch(s->s.decision().equals("EMITTED"));
            if(restartDue && emitted) {
                assertTrue(elapsed()<(contract.restartSeconds[restart]+45)*L6BContract.SECOND,"planned restart point missed");
                restart(reader,endpoint,env,pg,venue,sampler,restart,target);restart++;
                observer=elapsed()+5*L6BContract.SECOND;
            }
            if(elapsed()>=observer) {
                for(int i=0;i<2;i++) {
                    var observation=JSON.createObjectNode().put("elapsedNanos",elapsed());observation.set("generation",actors.get(i).evidence());
                    observation.set("scan",JSON.readTree(command(i,"L6_OBSERVER_SCAN")));append("scheduler.ndjson",observation);
                }
                http(endpoint,"FILL");reconcile();observer=(elapsed()/(5*L6BContract.SECOND)+1)*(5*L6BContract.SECOND);
            }
            if(elapsed()>=nextCheck && orders>0 && !projection.stopped()) {
                try{var point=checkpoint(reader,endpoint,current);reader.commit();append("progress.ndjson",point);
                    for(var event:restarts)if(!event.has("freshChainAfterRecovery") && point.path("oracle").path("orders").asLong()>event.path("ordersBefore").asLong())event.set("freshChainAfterRecovery",point.deepCopy());
                }catch(L6BCheckpoint.StoragePending pending){reader.rollback();}
                nextCheck=elapsed()+60*L6BContract.SECOND;
            }
            if(drainOrders>=0){assertEquals(drainOrders,number(reader,"SELECT count(*) FROM orders"));reader.commit();}
            long next=Math.min(contract.timing.total(),Math.min(observer,Math.min(nextCheck>0?nextCheck:Long.MAX_VALUE,pacer.nextElapsed())));
            next=Math.min(next,now<contract.timing.warmupNanos()?contract.timing.warmupNanos():now<contract.timing.activeEnd()?contract.timing.activeEnd():contract.timing.total());
            if(start+next>System.nanoTime())TimeUnit.NANOSECONDS.sleep(start+next-System.nanoTime());
        }
        phase(phase,phaseStarted,elapsed());proof.put("actualEndElapsedNanos",elapsed());
        proof.put("newOrdersDuringDrain",number(reader,"SELECT count(*) FROM orders")-drainOrders);reader.commit();
        if(!projection.stopped()) {
            assertEquals(3,restart);assertEquals(0,proof.path("newOrdersDuringDrain").asLong());
            var emitted=slots.stream().filter(s->s.decision().equals("EMITTED")).toList();
            for(int i=1;i<emitted.size();i++)assertTrue(emitted.get(i).actualElapsed()-emitted.get(i-1).actualElapsed()>=contract.manifest.intervalNanos());
            proof.put("emittedCount",emitted.size()).put("noCatchUp",true);
        }
    }

    private void restart(Connection reader,String endpoint,Map<String,String> env,B0Processes.Pg pg,
                         B0Processes.Child venue,L6ResourceSampler sampler,int index,int actor) throws Exception {
        continuity(reader,pg,venue);
        var before=sample(reader);long orders=number(reader,"SELECT count(*) FROM orders");reader.commit();
        assertTrue(before.path("backlog").asLong()>0,"restart must exercise durable pending work");
        var old=actors.get(actor);
        var event=JSON.createObjectNode().put("index",index).put("plannedSeconds",contract.restartSeconds[index])
                .put("restartTimestamp",Instant.now().toString()).put("startedElapsedNanos",elapsed()).put("oldPid",old.child.process.pid())
                .put("logicalActor",actor).put("ordersBefore",orders).put("backlogBefore",before.path("backlog").asLong())
                .put("reason",index==0?"GRACEFUL":index==1?"FORCED_DEATH":"DELAY_AND_RECOVERY_RESTART");
        event.set("activeRunsBefore",L6BAdmission.activeRuns(reader));reader.commit();
        event.set("beforeActorMetrics",JSON.readTree(command(actor,"L6_METRICS")));
        var durable=JSON.createObjectNode();durable.set("facts",facts(reader));reader.commit();durable.set("venue",http(endpoint,null));
        Path snapshot=dir.resolve("restart-"+index+"-before.json.gz");
        try(var out=new GZIPOutputStream(Files.newOutputStream(snapshot,StandardOpenOption.CREATE_NEW))){JSON.writeValue(out,durable);}
        event.put("beforeSnapshot",snapshot.getFileName().toString()).put("beforeSnapshotSha256",L6PgCapacityContract.hash(Files.readAllBytes(snapshot)));
        restarts.add(event);write("restart-"+index+"-started.json",event);
        long from=System.nanoTime();actors.stop(actor,old.child.process.pid(),index!=0);
        continuity(reader,pg,venue);
        var next=actors.start(actor,dir,env,event.path("reason").asText());
        event.put("newPid",next.child.process.pid()).put("generation",next.generation)
                .put("readyElapsedNanos",elapsed()).put("downtimeMillis",(System.nanoTime()-from)/1_000_000);
        assertTrue(System.nanoTime()-from<85*L6BContract.SECOND);
        continuity(reader,pg,venue);
        long revision=number(reader,"SELECT coalesce(max(revision),0) FROM reconciliation_scan_cursors WHERE venue='OKX'");reader.commit();
        long requiredRounds=2*Math.ceilDiv(orders,100);
        long deadline=System.nanoTime()+L6BAdmission.recoveryBoundNanos(orders);
        event.put("requiredCursorAdvances",requiredRounds).put("cursorRevisionBeforeRecovery",revision);
        boolean recovered=false;
        while(System.nanoTime()<deadline) {
            sampler.checkHealthy();assertFalse(projection.stopped());http(endpoint,"FILL");reconcile();
            var sample=sample(reader);long after=number(reader,"SELECT coalesce(max(revision),0) FROM reconciliation_scan_cursors WHERE venue='OKX'");
            var active=L6BAdmission.activeRuns(reader);reader.commit();
            var metric=JSON.readTree(command(actor,"L6_METRICS"));
            if(active.isEmpty() && sample.path("backlog").asLong()==0 && after-revision>=requiredRounds && metric.path("tickCompleted").asLong()>0) {
                var complete=checkpoint(reader,endpoint,"RESTART_"+index+"_RECOVERED");reader.commit();
                assertEquals(orders,complete.path("oracle").path("orders").asLong());
                event.set("recoveredFullChain",complete);event.set("afterActorMetrics",metric);
                event.set("activeRunsAfter",active);event.put("strategyRunBarrier","CONVERGED");
                event.put("cursorRevisionAfterRecovery",after).put("backlogAfter",0).put("recoveryCompletedElapsedNanos",elapsed())
                        .put("recoveryDurationMillis",(System.nanoTime()-from)/1_000_000).put("schedulerResumed",true).put("reconciliationResumed",true).put("result","RECOVERED");
                recovered=true;break;
            }
            TimeUnit.SECONDS.sleep(5);
        }
        requireRecovered(recovered,event);continuity(reader,pg,venue);write("restart-"+index+"-result.json",event);
    }
    static void requireRecovered(boolean recovered,JsonNode evidence) {
        assertTrue(recovered && evidence.path("schedulerResumed").asBoolean() && evidence.path("reconciliationResumed").asBoolean()
                && evidence.path("recoveredFullChain").path("oracle").path("orders").asLong()>0,"L6_B_RESTART_WITHOUT_BUSINESS_RECOVERY");
    }
    private void continuity(Connection reader,B0Processes.Pg pg,B0Processes.Child venue) throws Exception {
        String identity=value(reader,"SELECT identity FROM b0_fixture_identity");
        String postmaster=value(reader,"SELECT pg_postmaster_start_time()::text");
        actors.continuity(pg.ownedContainerId(),identity,postmaster,venue.process.pid());reader.commit();
        append("continuity.ndjson",JSON.createObjectNode().put("elapsedNanos",elapsed()).put("container",pg.ownedContainerId())
                .put("databaseIdentity",identity).put("postgresStartedAt",postmaster).put("venuePid",venue.process.pid()).put("status","PRESERVED"));
    }
    private ObjectNode checkpoint(Connection reader,String endpoint,String phase) throws Exception {
        fingerprint.verify();budgets.checkpoint();
        var point=L6BCheckpoint.verify(reader,endpoint,actors.runningChildren(),dir,phase,++checks,false,contract.orders,slots,L6BContract.MODE,projection::stopped);
        point.put("completedObservedElapsedNanos",elapsed()).put("reconciliationCalls",reconciliationCalls);
        append("business-progress.ndjson",point);return point;
    }
    private String command(int actor,String command) throws Exception {
        var generation=actors.get(actor);actors.assertOwned(generation,generation.child.process.pid());
        generation.child.startCommand(command);return generation.child.resultBefore(System.nanoTime()+5*L6BContract.SECOND);
    }
    private void reconcile() throws Exception { for(int i=0;i<2;i++){budgets.reconciliation(reconciliationCalls+1);command(i,"L6_RECONCILE");reconciliationCalls++;} }
    private long elapsed(){return System.nanoTime()-start;}
    private void phase(String name,long from,long to){proof.withArray("phases").addObject().put("phase",name).put("fromNanos",from).put("toNanos",to);}
    private void write(String name,JsonNode value)throws Exception{Files.writeString(dir.resolve(name),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value));}
    private void append(String name,JsonNode value)throws Exception{Files.writeString(dir.resolve(name),JSON.writeValueAsString(value)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
}
