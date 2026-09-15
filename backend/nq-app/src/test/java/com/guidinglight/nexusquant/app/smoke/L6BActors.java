package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 逻辑身份与PID代际明确分离；控制操作必须匹配本对象登记的Child及OS启动身份。 */
final class L6BActors implements AutoCloseable {
    static final class Generation {
        final int actor, generation;
        final B0Processes.Child child;
        final String osStartedAt;
        final String reason;
        String state="STARTING", stoppedAt;
        long childClockOffset;
        Generation(int actor,int generation,B0Processes.Child child,String reason) {
            this.actor=actor;this.generation=generation;this.child=child;this.reason=reason;
            osStartedAt=child.process.info().startInstant().orElseThrow().toString();
        }
        ObjectNode evidence() {
            return new ObjectMapper().createObjectNode().put("logicalActor","NQ-"+actor).put("generation",generation)
                    .put("pid",child.process.pid()).put("startTimestamp",osStartedAt).put("stopTimestamp",stoppedAt)
                    .put("restartReason",reason).put("lifecycle",state).put("processAlive",child.process.isAlive());
        }
    }
    private final List<Generation> history=new ArrayList<>();
    private final Generation[] current=new Generation[2];
    private final String container, databaseIdentity, postgresStartedAt;
    private final ProcessHandle venue;
    private final String venueStartedAt;
    L6BActors(String container,String databaseIdentity,String postgresStartedAt,ProcessHandle venue) {
        this.container=container;this.databaseIdentity=databaseIdentity;this.postgresStartedAt=postgresStartedAt;
        this.venue=venue;this.venueStartedAt=venue.info().startInstant().orElseThrow().toString();
    }
    synchronized void continuity(String container,String databaseIdentity,String postgresStartedAt,long venuePid) {
        L6BContract.require(this.container.equals(container) && this.databaseIdentity.equals(databaseIdentity)
                && this.postgresStartedAt.equals(postgresStartedAt) && venue.pid()==venuePid && venue.isAlive()
                && venue.info().startInstant().orElseThrow().toString().equals(venueStartedAt));
    }
    Generation start(int actor,Path dir,Map<String,String> environment,String reason) throws Exception {
        int generation;
        synchronized(this) {
            L6BContract.require(actor>=0 && actor<2 && (current[actor]==null || "DOWN".equals(current[actor].state)));
            generation=current[actor]==null?0:current[actor].generation+1;
            L6BContract.require(history.size()<5);
        }
        var child=new B0Processes.Child(L6NqProcessMain.class,dir,"nq-"+actor+"-g"+generation,environment);
        var next=new Generation(actor,generation,child,reason);
        try { synchronized(this) { enroll(next); } }
        catch(RuntimeException failure) { child.close();throw failure; }
        try {
            child.awaitReady();
            long clock=Long.parseLong(child.send("L6_CLOCK"));
            synchronized(this) {
                assertOwned(next,child.process.pid()); next.childClockOffset=clock-System.nanoTime(); next.state="RUNNING";
            }
            return next;
        } catch (Exception | AssertionError failure) {
            child.close(); synchronized(this) { next.state="DOWN";next.stoppedAt=Instant.now().toString(); }
            throw failure;
        }
    }
    synchronized void enroll(Generation next) {
        L6BContract.require(next.actor>=0 && next.actor<2 && history.size()<5);
        var old=current[next.actor];
        L6BContract.require((old==null && next.generation==0) || (old!=null && "DOWN".equals(old.state) && next.generation==old.generation+1));
        L6BContract.require(history.stream().noneMatch(g -> g.child.process.pid()==next.child.process.pid()));
        current[next.actor]=next;history.add(next);
    }
    synchronized void assertOwned(Generation expected,long requestedPid) {
        L6BContract.require(expected!=null && expected.actor>=0 && expected.actor<2 && current[expected.actor]==expected
                && history.contains(expected) && expected.child.process.pid()==requestedPid && requestedPid!=venue.pid()
                && requestedPid!=ProcessHandle.current().pid() && expected.child.process.isAlive()
                && expected.osStartedAt.equals(expected.child.process.info().startInstant().orElseThrow().toString()));
    }
    void stop(int actor,long requestedPid,boolean forced) throws Exception {
        Generation target;
        synchronized(this) {
            L6BContract.require(actor>=0 && actor<2);
            target=current[actor];assertOwned(target,requestedPid);
            L6BContract.require("RUNNING".equals(target.state)); target.state="STOPPING";
        }
        if (forced) target.child.kill();
        else {
            target.child.startCommand("STOP");
            L6BContract.require(target.child.process.waitFor(10,TimeUnit.SECONDS) && target.child.process.exitValue()==0);
        }
        target.child.close();
        synchronized(this) { target.state="DOWN";target.stoppedAt=Instant.now().toString(); }
    }
    synchronized Generation get(int actor) { return current[actor]; }
    synchronized List<B0Processes.Child> runningChildren() {
        return java.util.Arrays.stream(current).filter(g -> g!=null && "RUNNING".equals(g.state)).map(g->g.child).toList();
    }
    synchronized List<ProcessHandle> liveProcesses() {
        return history.stream().filter(g->!"STOPPING".equals(g.state) && !"DOWN".equals(g.state) && g.child.process.isAlive()).map(g->g.child.process.toHandle()).toList();
    }
    synchronized void requireHealthy() {
        for(var g:current) if(g!=null && "RUNNING".equals(g.state)) assertOwned(g,g.child.process.pid());
    }
    synchronized com.fasterxml.jackson.databind.node.ArrayNode evidence() {
        var rows=new ObjectMapper().createArrayNode();history.forEach(g->rows.add(g.evidence()));return rows;
    }
    synchronized long survivors() { return history.stream().filter(g->g.child.process.isAlive()).count(); }
    @Override public void close() throws Exception {
        Exception first=null;
        for(var g:List.copyOf(history)) {
            try { g.child.close();synchronized(this) { g.state="DOWN";if(g.stoppedAt==null)g.stoppedAt=Instant.now().toString(); } }
            catch(Exception failure) { if(first==null)first=failure;else first.addSuppressed(failure); }
        }
        if(first!=null)throw first;
    }
}
