package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 独立按订单存续时间求和复算；覆盖180min预算和放大、缺失、容量漂移拒绝。 */
class L6BBudgetTest {
    private final ObjectMapper json=new ObjectMapper();
    private com.fasterxml.jackson.databind.node.ArrayNode actors() {
        var a=json.createArrayNode();for(int i=0;i<2;i++)a.addObject().put("poolMax",10).put("commands",4).put("tickStarted",1);return a;
    }
    @Test void exactFullDurationProjectionIncludesRestartsAndCompressionEvidence() throws Exception {
        var c=L6BContract.inspectFormalCandidate();
        assertEquals(10800*L6BContract.SECOND,c.timing.total());assertEquals(1434,c.orders);assertEquals(1080,c.samples);
        assertArrayEquals(new long[]{2400,4800,7200},c.restartSeconds);assertEquals(190,c.checkpoints);
        for(int phase=0;phase<3;phase++) {
            long from=new long[]{0,600,10200}[phase]*L6BContract.SECOND;
            long to=new long[]{600,10200,10800}[phase]*L6BContract.SECOND;
            BigInteger exposure=BigInteger.valueOf(to-from);
            for(long slot=0;slot<c.orders;slot++)exposure=exposure.add(BigInteger.valueOf(Math.max(0,to-Math.max(from,slot*c.manifest.intervalNanos()))));
            long independent=L6PgCapacityContract.ceilRatio(exposure.multiply(BigInteger.valueOf(c.base.rate(phase))),L6BContract.SECOND);
            assertEquals(independent,c.growth(from,to,c.base.rate(phase),phase==2?c.orders:-1));
        }
        assertTrue(c.rawCap>1024*L6BContract.MIB && c.rawCap<2048*L6BContract.MIB);
        var budget=new L6BBudgets(c,349,100L*1024*1024*1024,actors()).evidence();
        assertEquals(3032640,budget.path("transactionGroups").path("qualificationReconciliation").asLong());
        assertTrue(budget.path("transactionGroups").path("restartStartup").asLong()>0);
        assertTrue(budget.path("transactionGroups").path("restartFairCursorRecovery").asLong()>0);
        assertTrue(budget.path("transactionHardCap").asLong()>3606000);
        assertTrue(budget.path("transactionHardCap").asLong()<4000000);
    }
    @Test void projectionAndMemoryRejectChangedCapacityAndInsufficientHost() throws Exception {
        var c=L6BContract.inspectFormalCandidate();long baseline=48910336,capacity=c.deriveCapacity(baseline);
        assertEquals("PASS",c.capacityEvidence(baseline,baseline,capacity).path("preflightResult").asText());
        assertThrows(IllegalStateException.class,()->c.capacityEvidence(baseline,baseline,capacity+L6BContract.MIB));
        assertThrows(IllegalStateException.class,()->c.capacityEvidence(baseline,baseline+L6BContract.MIB,capacity));
        long budget=L6HostMemoryPreflight.budget(c.base,capacity);
        long required=Math.ceilDiv(budget*5,3);
        assertThrows(IllegalStateException.class,()->L6HostMemoryPreflight.verify(c.base,capacity,new L6HostMemoryPreflight.Entry(required-1,512*L6BContract.MIB,512*L6BContract.MIB)));
        assertEquals("PASS",L6HostMemoryPreflight.verify(c.base,capacity,new L6HostMemoryPreflight.Entry(required,512*L6BContract.MIB,512*L6BContract.MIB)).path("status").asText());
        var guard=new L6BProjectionGuard(c,capacity);
        assertTrue(guard.need(0,0,0)<capacity-baseline);
        assertTrue(guard.need(7200*L6BContract.SECOND,1000,1)>0);
    }
    @Test void transactionRunawayCounterRegressionMissingPoolAndDiskRejected() throws Exception {
        var c=L6BContract.inspectFormalCandidate();
        assertThrows(IllegalStateException.class,()->new L6BBudgets(c,349,1024,actors()));
        var bad=actors();bad.get(0).deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)bad.get(0)).put("poolMax",20);
        assertThrows(IllegalStateException.class,()->new L6BBudgets(c,349,100L*1024*1024*1024,bad));
        var rate=new L6BBudgets(c,349,100L*1024*1024*1024,actors());rate.check(0,349);
        assertThrows(IllegalStateException.class,()->rate.check(60000,100000));
        var backwards=new L6BBudgets(c,349,100L*1024*1024*1024,actors());backwards.check(0,400);
        assertThrows(IllegalStateException.class,()->backwards.check(10000,399));
        assertThrows(IllegalStateException.class,()->backwards.checkFinal(10_000_000));
    }
    @Test void restartAnalysisNegativeCases() throws Exception {
        B0Processes.command("python","-X","utf8",B0Processes.root().resolve("backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_l6_b_analyzer.py").toString());
    }
    @Test void candidateInputAdditionRejected() throws Exception {
        var fingerprint=new L6BFingerprint(false);fingerprint.verify();
        assertTrue(fingerprint.evidence().path("files").has(L6PgCapacityContract.CANONICAL.toString().replace('\\','/')));
        var file=java.nio.file.Files.createTempFile(B0Processes.root().resolve("backend"),"l6-fingerprint-",".txt");
        try { assertThrows(IllegalStateException.class,fingerprint::verify); }
        finally { java.nio.file.Files.delete(file); }
        fingerprint.verify();
    }
    @Test void frozenMemoryReserveDoesNotDoubleCountOwnedAllocation() {
        long gib=1024L*1024*1024;
        L6BResources.verifyMemory(15*gib,30*gib,24*gib);
        L6BResources.verifyMemory(18*gib,30*gib,12*gib);
        assertThrows(IllegalStateException.class,()->L6BResources.verifyMemory(18*gib+1,30*gib,30*gib));
        assertThrows(IllegalStateException.class,()->L6BResources.verifyMemory(15*gib,30*gib,12*gib-1));
    }
    @Test void gitCheckoutLineEndingsPreserveContentBinding() throws Exception {
        var utf8=java.nio.charset.StandardCharsets.UTF_8;
        assertEquals(L6BContract.textHash("{\"value\":1}\n".getBytes(utf8)),L6BContract.textHash("{\"value\":1}\r\n".getBytes(utf8)));
        assertNotEquals(L6BContract.textHash("{\"value\":1}\n".getBytes(utf8)),L6BContract.textHash("{\"value\":2}\n".getBytes(utf8)));
        assertNotEquals(L6BContract.textHash("{\"value\":1}\n".getBytes(utf8)),L6BContract.textHash("{\"value\":1}".getBytes(utf8)));
    }
    @Test void planArtifactCannotEscapeOwnedEvidence() throws Exception {
        var input=json.readTree(java.nio.file.Files.readAllBytes(B0Processes.root().resolve(L6BContract.CANONICAL)));
        assertTrue(java.nio.file.Files.isRegularFile(B0Processes.root().resolve(L6BContract.planPath(input))));
        var artifact=(com.fasterxml.jackson.databind.node.ObjectNode)input.path("planArtifact");
        for(String path:new String[]{"../outside.md","C:/outside.md","backend/pom.xml","docs/../../outside.md"}) {
            artifact.put("path",path);assertThrows(IllegalStateException.class,()->L6BContract.planPath(input));
        }
    }
    @Test void rawPrefixTamperAndCompressionTestsExecute() throws Exception {
        B0Processes.command("python","-X","utf8",B0Processes.root().resolve("backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_l6_b_oracle.py").toString());
    }
}
