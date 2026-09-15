package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 按绝对时长证明1080拍及命名空间；额外periodic仍拒绝，不靠扩大任意常量。 */
class L6BSamplingTest {
    @TempDir Path directory;
    @Test void fullDurationHas1080PeriodicAndDisjointBoundaryTokens() throws Exception {
        var nanos=new AtomicLong();var json=new ObjectMapper();var fields=Set.of("generation");
        L6ResourceSampler.Collector collector=stamp->L6RuntimeResources.measured(stamp,json.createObjectNode().put("generation",stamp.sampleIndex()/240),fields);
        var duration=new L6DurationContract(600*L6BContract.SECOND,9600*L6BContract.SECOND,600*L6BContract.SECOND);
        var sampler=new L6ResourceSampler(Map.of("nq",collector),Map.of("nq",fields),nanos::get,
                ()->Instant.EPOCH.plusNanos(nanos.get()),0,duration,directory.resolve("resources.ndjson"),true,new Object());
        for(int i=0;i<1080;i++){nanos.set(i*10*L6BContract.SECOND);sampler.sample();assertTrue(sampler.latest().path("sampleToken").asText().endsWith(":"+i));}
        sampler.requireComplete();nanos.set(duration.total());
        assertTrue(sampler.boundary("FINAL","DRAIN").path("sampleToken").asText().endsWith(":1081"));
        assertThrows(IllegalStateException.class,sampler::sample);
        assertThrows(IllegalStateException.class,sampler::close);
    }
    @Test void missingRunningMeasurementStillRejects() throws Exception {
        var json=new ObjectMapper();var fields=Set.of("generation");
        L6ResourceSampler.Collector missing=stamp->L6RuntimeResources.measured(stamp,json.createObjectNode(),fields);
        var sampler=new L6ResourceSampler(Map.of("nq",missing),Map.of("nq",fields),()->0L,()->Instant.EPOCH,0,
                new L6DurationContract(10,10,10),directory.resolve("missing.ndjson"),true,new Object());
        assertThrows(IllegalStateException.class,sampler::sample);
        assertThrows(IllegalStateException.class,sampler::close);
    }
}
