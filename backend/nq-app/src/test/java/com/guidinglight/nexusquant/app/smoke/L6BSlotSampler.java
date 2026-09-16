package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** B按绝对槽截止验收；漏槽只有缺失记录，禁止历史补拍或复用旧值。 */
final class L6BSlotSampler implements AutoCloseable {
    static final long INTERVAL = 10_000_000_000L;
    static final long JITTER_OBSERVATION = 2_000_000_000L;
    // 单次overrun可占本槽及紧邻槽；第三个连续无效槽已超过这个孤立扰动包络。
    static final int SUSTAINED_INVALID_SLOTS = 3;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Map<String,L6ResourceSampler.Collector> collectors;
    private final Map<String,Set<String>> required;
    private final LongSupplier clock;
    private final Supplier<Instant> wall;
    private final long origin, cap, controllerThread = Thread.currentThread().threadId();
    private final L6SamplingSchedule duration;
    private final Path output;
    private final Object lifecycleLock;
    private final String identity = UUID.randomUUID().toString();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r,"l6-b-resource-sampler"));
    private final ObjectNode summary = JSON.createObjectNode();
    private Consumer<ObjectNode> observer = row -> { };
    private LongConsumer injection = slot -> { };
    private long index, measured, jitter, overruns, missed, maximumLag, maximumCollection;
    private long minimumSlack = Long.MAX_VALUE;
    private int invalidStreak;
    private boolean sustained;
    private boolean coverageClosed;
    private volatile RuntimeException failure;
    private ObjectNode latest;

    L6BSlotSampler(Map<String,L6ResourceSampler.Collector> collectors, Map<String,Set<String>> required,
                   LongSupplier clock, Supplier<Instant> wall, long origin, L6SamplingSchedule duration,
                   Path output, Object lifecycleLock) {
        if (collectors.isEmpty() || !collectors.keySet().equals(required.keySet())) throw new IllegalArgumentException("mandatory collector topology");
        this.collectors=new LinkedHashMap<>(collectors);this.required=Map.copyOf(required);
        this.clock=clock;this.wall=wall;this.origin=origin;this.duration=duration;this.output=output;
        this.lifecycleLock=lifecycleLock;cap=Math.ceilDiv(duration.total(),INTERVAL);
        if(cap<=0 || cap>1080)throw new IllegalArgumentException("sampling duration bound");
        summary.set("contract",contract());summary.putArray("samples");
    }

    static ObjectNode contract() {
        return JSON.createObjectNode().put("schemaVersion",1).put("intervalNanos",INTERVAL)
                .put("validity","TARGET_LE_START_LT_DEADLINE_AND_COMPLETION_LE_DEADLINE")
                .put("jitterObservationNanos",JITTER_OBSERVATION).put("sustainedInvalidSlots",SUSTAINED_INVALID_SLOTS)
                .put("isolatedGapDisposition","PENDING_FINAL_EVALUATION").put("acceptanceRequiresAllSlotsValid",true);
    }

    synchronized void observeWith(Consumer<ObjectNode> observer) { this.observer=observer; }
    synchronized void probeInjection(LongConsumer injection) { this.injection=injection; }
    void start() { scheduleNext(); }
    private synchronized void scheduleNext() {
        if(executor.isShutdown() || coverageClosed || failure!=null || index>=cap)return;
        long target=index*INTERVAL;
        executor.schedule(() -> {
            try { injection.accept(index);sample(true); } catch(RuntimeException error) { failure=error; }
            scheduleNext();
        },Math.max(0,origin+target-clock.getAsLong()),TimeUnit.NANOSECONDS);
    }

    void sample() {
        sample(false);
    }
    private void sample(boolean scheduled) {
        long attempted=clock.getAsLong();
        synchronized(this) {
            // 结束补记与已排队回调串行封闭；直接重复采样仍走原拒绝路径。
            if(scheduled && coverageClosed)return;
            long samplerAcquired=clock.getAsLong();
            synchronized(lifecycleLock) { collect(attempted,samplerAcquired); }
        }
    }

    private void collect(long attempted,long samplerAcquired) {
        checkHealthy();
        if(index>=cap)throw latch("L6_RESOURCE_SAMPLE_CAP",null);
        long begin=clock.getAsLong(), at=begin-origin;
        if(at<index*INTERVAL)throw latch("L6_RESOURCE_EARLY_SLOT",null);
        while(index<cap && (index+1)*INTERVAL<=at) missing(at,"SCHEDULER_PASSED_COMPLETE_SLOT");
        if(index>=cap) { if(sustained)throw latch("SUSTAINED_SCHEDULER_DEGRADATION",null);return; }
        long slot=index, target=slot*INTERVAL, deadline=target+INTERVAL;
        var stamp=new L6ResourceSampler.Stamp(slot,wall.get().toString(),at/1_000_000,
                duration.samplePhase(at),identity+":"+slot);
        ObjectNode row=stamp.json().put("sampleType","PERIODIC").put("scheduledElapsedNanos",target)
                .put("scheduledElapsedMillis",target/1_000_000).put("actualStartElapsedNanos",at)
                .put("actualStartElapsedMillis",at/1_000_000.0).put("startLagMillis",(at-target)/1_000_000.0)
                .put("periodicSamplerLatenessMillis",(at-target)/1_000_000.0)
                .put("slotDeadlineNanos",deadline).put("slotDeadlineMillis",deadline/1_000_000)
                .put("schedulerAttemptElapsedNanos",attempted-origin)
                .put("samplerMonitorWaitNanos",samplerAcquired-attempted).put("lifecycleLockWaitNanos",begin-samplerAcquired);
        var sources=row.putObject("sources");
        Exception problem=null;
        try {
            row.set("controllerBefore",diagnostics());
            // collector失败仍尝试其余安全只读诊断；总操作预算由本次10秒采集期限限制。
            L6BCollectionBudget.begin(begin+INTERVAL,clock);
            for(var entry:collectors.entrySet()) {
                var source=sources.putObject(entry.getKey());source.setAll(stamp.json());
                long from=clock.getAsLong();source.put("collectionStartElapsedNanos",from-origin);
                try {
                    L6BCollectionBudget.remaining();
                    var observation=entry.getValue().collect(stamp);
                    if(observation==null || !stamp.equals(observation.stamp()))throw new IllegalStateException("L6_STALE_RESOURCE_OBSERVATION");
                    source.set("values",observation.values());source.set("availability",observation.availability());
                    for(String key:required.get(entry.getKey())) {
                        String state=observation.availability().path(key).asText();
                        if(!observation.values().hasNonNull(key) || !("MEASURED".equals(state)
                                || "NOT_APPLICABLE".equals(state) && (key.equals("fd") || key.equals("handles"))))
                            throw new IllegalStateException("L6_MANDATORY_MEASUREMENT_UNAVAILABLE: "+key);
                    }
                    source.put("status","MEASURED");
                } catch(Exception error) {
                    source.put("status","UNAVAILABLE").put("error",error.toString());
                    failure=new IllegalStateException("L6_RESOURCE_QUALIFICATION_REJECTED",error);
                    if(problem==null)problem=error;else problem.addSuppressed(error);
                } finally {
                    long end=clock.getAsLong();source.put("collectionEndElapsedNanos",end-origin)
                            .put("collectionMillis",(end-from)/1_000_000.0);
                }
            }
            row.set("controllerAfter",diagnostics());
        } catch(Exception error) { problem=error; }
        finally { L6BCollectionBudget.end(); }
        long end=clock.getAsLong()-origin, slack=deadline-end;
        row.put("completionElapsedNanos",end).put("completionElapsedMillis",end/1_000_000.0)
                .put("collectionMillis",(end-at)/1_000_000.0).put("slotSlackMillis",slack/1_000_000.0);
        maximumLag=Math.max(maximumLag,at-target);maximumCollection=Math.max(maximumCollection,end-at);minimumSlack=Math.min(minimumSlack,slack);
        if(problem==null) {
            row.put("status","MEASURED");measured++;
            if(slack<0) { overruns++;invalid();row.put("slotStatus","SLOT_OVERRUN").put("acceptanceImpact","PENDING_FINAL_EVALUATION"); }
            else {
                invalidStreak=0;
                boolean late=at-target>JITTER_OBSERVATION;if(late)jitter++;
                row.put("slotStatus",late?"VALID_WITH_JITTER":"VALID").put("acceptanceImpact",late?"OBSERVATION":"NONE");
            }
            try { observer.accept(row.deepCopy());latest=row.deepCopy(); }
            catch(Exception error) { problem=error;row.put("observerError",error.toString()); }
        } else row.put("status","UNAVAILABLE").put("slotStatus","COLLECTION_FAILED").put("error",problem.toString());
        index++;persist(row);
        // overrun后的已到期target只记缺失；下一次真实采集必须等待未来target。
        if(slack<0)while(index<cap && index*INTERVAL<=end)missing(end,"PREVIOUS_SLOT_OVERRUN");
        if(problem!=null)throw latch("L6_RESOURCE_QUALIFICATION_REJECTED",problem);
        if(sustained)throw latch("SUSTAINED_SCHEDULER_DEGRADATION",null);
    }

    private void invalid() { if(++invalidStreak>=SUSTAINED_INVALID_SLOTS)sustained=true; }
    private void missing(long observed,String reason) {
        long target=index*INTERVAL;
        var row=JSON.createObjectNode().put("sampleIndex",index).put("sampleType","PERIODIC")
                .put("sampleToken",identity+":"+index).put("status","MISSED_SLOT").put("slotStatus","MISSED_SLOT")
                .put("scheduledElapsedNanos",target).put("scheduledElapsedMillis",target/1_000_000)
                .put("slotDeadlineNanos",target+INTERVAL).put("slotDeadlineMillis",(target+INTERVAL)/1_000_000)
                .put("detectedElapsedNanos",observed).put("detectedAt",wall.get().toString())
                .put("phase",duration.samplePhase(target)).put("reason",reason).put("acceptanceImpact","PENDING_FINAL_EVALUATION");
        for(String key:new String[]{"actualStartElapsedNanos","actualStartElapsedMillis","completionElapsedNanos","completionElapsedMillis","collectionMillis","sampledAt"})row.putNull(key);
        row.putObject("sources");missingDiagnostic(row);index++;missed++;invalid();persist(row);
    }
    private void missingDiagnostic(ObjectNode row) { row.put("measurementAvailability","NOT_COLLECTED_NO_SUBSTITUTION"); }
    private void persist(ObjectNode row) {
        summary.withArray("samples").add(row.deepCopy());
        try { Files.writeString(output,JSON.writeValueAsString(row)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
        catch(Exception error) { throw latch("L6_RESOURCE_EVIDENCE_WRITE_FAILED",error); }
    }
    private RuntimeException latch(String reason,Throwable cause) { failure=new IllegalStateException(reason,cause);return failure; }
    void checkHealthy() { if(failure!=null)throw failure; }
    synchronized ObjectNode latest() { checkHealthy();if(latest==null)throw new IllegalStateException("L6_NO_RESOURCE_SAMPLE");return latest.deepCopy(); }
    synchronized void requireComplete() {
        checkHealthy();long now=clock.getAsLong()-origin;
        if(now>=duration.total()) {
            coverageClosed=true;
            while(index<cap)missing(now,"RUN_ENDED_WITHOUT_SLOT");
        }
        if(sustained)throw latch("SUSTAINED_SCHEDULER_DEGRADATION",null);
        if(index!=cap)throw latch("L6_RESOURCE_SAMPLE_COVERAGE_INCOMPLETE",null);
    }
    synchronized ObjectNode summary() {
        var result=summary.deepCopy().put("sampleCount",index).put("measuredCount",measured).put("jitterCount",jitter)
                .put("overrunCount",overruns).put("missedSlotCount",missed).put("maxStartLagMillis",maximumLag/1_000_000.0)
                .put("maxCollectionMillis",maximumCollection/1_000_000.0)
                .put("status",failure!=null?"UNAVAILABLE":overruns+missed>0?"COMPLETED_NOT_ACCEPTED":"MEASURED");
        if(minimumSlack==Long.MAX_VALUE)result.putNull("minimumSlotSlackMillis");else result.put("minimumSlotSlackMillis",minimumSlack/1_000_000.0);
        return result;
    }
    private ObjectNode diagnostics() {
        var out=JSON.createObjectNode().put("pid",ProcessHandle.current().pid()).put("uptimeMillis",ManagementFactory.getRuntimeMXBean().getUptime())
                .put("heapUsed",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        out.set("gc",L6GcEvidence.read());var threads=out.putArray("threads");
        var bean=ManagementFactory.getThreadMXBean();
        for(long id:new long[]{controllerThread,Thread.currentThread().threadId()}) {
            var info=bean.getThreadInfo(id,8);
            if(info==null) { threads.addObject().put("id",id).put("status","UNAVAILABLE");continue; }
            var thread=threads.addObject().put("id",id).put("name",info.getThreadName()).put("state",info.getThreadState().name())
                    .put("lockName",info.getLockName()).put("lockOwnerId",info.getLockOwnerId()).put("blockedCount",info.getBlockedCount())
                    .put("waitedCount",info.getWaitedCount()).put("cpuNanos",bean.isThreadCpuTimeSupported()?bean.getThreadCpuTime(id):-1);
            var stack=thread.putArray("stack");for(var frame:info.getStackTrace())stack.add(frame.toString());
        }
        return out;
    }
    @Override public void close() throws Exception {
        executor.shutdown();
        if(!executor.awaitTermination(10,TimeUnit.SECONDS)) {
            executor.shutdownNow();if(!executor.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("L6_SAMPLER_SURVIVOR");
        }
        Files.writeString(output.resolveSibling("resource-summary.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary()));
        if(failure!=null)throw new IllegalStateException("L6_SAMPLER_CLOSED_WITH_FAILURE",failure);
    }
}
