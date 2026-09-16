package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 同一绝对时钟覆盖迟到、overrun和缺槽；断言真实collector调用数以排除补拍。 */
class L6BSlotSamplerTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final long MS=1_000_000L;
    @TempDir Path directory;
    private final AtomicLong clock=new AtomicLong();
    private final AtomicInteger calls=new AtomicInteger();
    private final Set<String> fields=Set.of("counter");
    private L6ResourceSampler.Collector collector(long costMillis) {
        return stamp -> {
            int count=calls.incrementAndGet();clock.addAndGet(costMillis*MS);
            return L6RuntimeResources.measured(stamp,JSON.createObjectNode().put("counter",count),fields);
        };
    }
    private L6BSlotSampler sampler(L6ResourceSampler.Collector collector) {
        return new L6BSlotSampler(Map.of("fixture",collector),Map.of("fixture",fields),clock::get,
                ()->Instant.EPOCH.plusNanos(clock.get()),0,new L6DurationContract(10_000*MS,20_000*MS,10_000*MS),
                directory.resolve("resources.ndjson"),new Object());
    }
    @Test void exact5421LateStillMeasuredWhenSlotCompletesAndNextTargetStaysAbsolute() throws Exception {
        var fixture=JSON.readTree(getClass().getResourceAsStream("/l6b-126m55-cadence.json"));
        clock.set(fixture.path("startLagMillis").asLong()*MS);
        try(var sampler=sampler(collector(700))) {
            sampler.sample();var row=sampler.latest();
            assertEquals("VALID_WITH_JITTER",row.path("slotStatus").asText());
            assertEquals(6121,row.path("completionElapsedMillis").asDouble());
            assertEquals(3879,row.path("slotSlackMillis").asDouble());
            assertEquals(1,calls.get());clock.set(10_000*MS);sampler.sample();
            assertEquals(1,sampler.latest().path("sampleIndex").asInt());
            assertEquals(10_000,sampler.latest().path("scheduledElapsedMillis").asLong());
        }
    }
    @Test void exact5421OverrunRetainsDiagnosticsAndSkipsCoveredTarget() throws Exception {
        clock.set(5421*MS);
        try(var sampler=sampler(collector(5000))) {
            sampler.sample();assertEquals(1,calls.get());
            var rows=sampler.summary().path("samples");assertEquals(2,rows.size());
            assertEquals("SLOT_OVERRUN",rows.get(0).path("slotStatus").asText());
            assertTrue(rows.get(0).has("controllerBefore"));assertTrue(rows.get(0).has("controllerAfter"));
            assertEquals("MISSED_SLOT",rows.get(1).path("status").asText());
            assertTrue(rows.get(1).path("sources").isEmpty());assertTrue(rows.get(1).path("actualStartElapsedNanos").isNull());
            sampler.checkHealthy();clock.set(20_000*MS);sampler.sample();
            assertEquals(2,calls.get());assertEquals(2,sampler.latest().path("sampleIndex").asInt());
            assertEquals("COMPLETED_NOT_ACCEPTED",sampler.summary().path("status").asText());
        }
    }
    @Test void pauseAcrossWholeSlotMakesOneExplicitMissingRecordWithoutCatchUp() throws Exception {
        try(var sampler=sampler(collector(1))) {
            sampler.sample();clock.set(22_500*MS);sampler.sample();
            assertEquals(2,calls.get());var rows=sampler.summary().path("samples");
            assertEquals(3,rows.size());assertEquals("MISSED_SLOT",rows.get(1).path("status").asText());
            assertEquals(2,rows.get(2).path("sampleIndex").asInt());
            clock.set(30_000*MS);sampler.sample();clock.set(40_000*MS);sampler.requireComplete();
            assertEquals(4,sampler.summary().path("sampleCount").asInt());assertEquals(3,calls.get());
        }
    }
    @Test void sustainedInvalidSlotsAreBlockingAfterDiagnostics() throws Exception {
        var sampler=sampler(collector(25_000));
        assertThrows(IllegalStateException.class,sampler::sample);
        assertEquals(1,calls.get());assertEquals(3,sampler.summary().path("sampleCount").asInt());
        assertTrue(sampler.summary().path("samples").get(0).has("controllerAfter"));
        assertThrows(IllegalStateException.class,sampler::checkHealthy);
        assertThrows(IllegalStateException.class,sampler::close);
        assertTrue(Files.readString(directory.resolve("resources.ndjson")).contains("PREVIOUS_SLOT_OVERRUN"));
    }
    @Test void exactCompletionDeadlineIsValidButOneNanosecondAfterIsOverrun() throws Exception {
        var cost=new AtomicLong(L6BSlotSampler.INTERVAL);
        try(var sampler=sampler(stamp -> { clock.addAndGet(cost.get());return L6RuntimeResources.measured(stamp,JSON.createObjectNode().put("counter",1),fields); })) {
            sampler.sample();assertEquals("VALID",sampler.latest().path("slotStatus").asText());
            cost.incrementAndGet();sampler.sample();assertEquals("SLOT_OVERRUN",sampler.latest().path("slotStatus").asText());
        }
    }
    @Test void collectorExceptionStillBlocksButOtherDiagnosticsAreRetained() throws Exception {
        var collectors=new LinkedHashMap<String,L6ResourceSampler.Collector>();
        collectors.put("broken",stamp->{throw new java.io.IOException("real collection failure");});collectors.put("other",collector(1));
        var sampler=new L6BSlotSampler(collectors,Map.of("broken",fields,"other",fields),clock::get,()->Instant.EPOCH,0,
                new L6DurationContract(10_000*MS,20_000*MS,10_000*MS),directory.resolve("resources.ndjson"),new Object());
        assertThrows(IllegalStateException.class,sampler::sample);assertEquals(1,calls.get());
        assertEquals("MEASURED",sampler.summary().path("samples").get(0).path("sources").path("other").path("status").asText());
        assertThrows(IllegalStateException.class,sampler::close);
    }
    @Test void staleIdentityAndFormalInjectionAreRejected() throws Exception {
        L6ResourceSampler.Observation[] cached={null};
        var sampler=sampler(stamp->{if(cached[0]==null)cached[0]=L6RuntimeResources.measured(stamp,JSON.createObjectNode().put("counter",1),fields);return cached[0];});
        sampler.sample();clock.set(10_000*MS);assertThrows(IllegalStateException.class,sampler::sample);
        assertThrows(IllegalStateException.class,sampler::close);
        assertThrows(IllegalArgumentException.class,()->new L6BRuntime(false,false,true));
    }
    @Test void duplicateOrEarlySlotCannotCollectAgain() throws Exception {
        var sampler=sampler(collector(1));sampler.sample();
        assertThrows(IllegalStateException.class,sampler::sample);assertEquals(1,calls.get());
        assertThrows(IllegalStateException.class,sampler::close);
    }
    @Test void formalEndSealsQueuedLastSlotWithoutTurningIsolatedMissIntoFailure() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var sampler=sampler(collector(1));
        for(int i=0;i<3;i++){clock.set(i*10_000*MS);sampler.sample();}
        sampler.probeInjection(slot->{
            entered.countDown();
            try { if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test callback timeout"); }
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
        });
        clock.set(30_000*MS);sampler.start();
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            clock.set(40_000*MS);sampler.requireComplete();
        } finally {release.countDown();sampler.close();}
        sampler.checkHealthy();assertEquals(3,calls.get());
        assertEquals(4,sampler.summary().path("sampleCount").asInt());
        assertEquals("COMPLETED_NOT_ACCEPTED",sampler.summary().path("status").asText());
        assertEquals("RUN_ENDED_WITHOUT_SLOT",sampler.summary().path("samples").get(3).path("reason").asText());
        assertThrows(IllegalStateException.class,sampler::sample);
    }
    @Test void lastSlotCallbackMayRecordMissingBeforeFormalEndWithoutDuplicate() throws Exception {
        try(var sampler=sampler(collector(1))) {
            for(int i=0;i<3;i++){clock.set(i*10_000*MS);sampler.sample();}
            clock.set(40_000*MS);sampler.sample();sampler.requireComplete();sampler.checkHealthy();
            assertEquals(3,calls.get());assertEquals(4,sampler.summary().path("sampleCount").asInt());
            assertEquals(1,sampler.summary().path("missedSlotCount").asInt());
        }
    }
}
