package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

/** L6-B独立时长与预算；只复用L6-A已接受模型，不修改其合同或运行入口。 */
final class L6BContract {
    static final String MODE = "FORMAL_L6_B";
    static final Path CANONICAL = L6PgCapacityContract.CANONICAL.resolveSibling("L6_B_RUNNER_CONTRACT_V2.json");
    static final long SECOND = 1_000_000_000L;
    static final int RESTARTS = 3, READY_SECONDS = 75, RECOVERY_SECONDS = 600;
    static final int TICK_CAP = 3000;
    static final int COMMAND_CAP = Math.toIntExact(2*(10800/5) + Math.ceilDiv(10200*SECOND,7_117_650_000L)
            + RESTARTS*(2*Math.ceilDiv(Math.ceilDiv(10200*SECOND,7_117_650_000L),100) + RECOVERY_SECONDS/5 + 16) + 128);
    static final long MIB = 1_048_576;
    final L6PgCapacityContract base;
    final L6FormalManifest manifest;
    final L6DurationContract timing;
    final boolean probe;
    final long[] restartSeconds;
    final int orders, samples, checkpoints;
    final long restartStorage, reserve, rawCap, journalEvents, journalBytes;
    private final String authoritySha;
    private final JsonNode authority;

    L6BContract(boolean probe) throws Exception {
        this(probe,false);
    }
    static L6BContract inspectFormalCandidate() throws Exception { return new L6BContract(false,true); }
    private L6BContract(boolean probe,boolean inspectOnly) throws Exception {
        this.probe = probe;
        base = L6PgCapacityContract.committed();
        manifest = L6FormalManifest.read(B0Processes.root().resolve(L6FormalManifest.CANONICAL));
        byte[] bytes = Files.readAllBytes(B0Processes.root().resolve(CANONICAL));
        authoritySha = L6PgCapacityContract.hash(bytes);
        authority = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
        var fields=new java.util.HashSet<String>();authority.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(java.util.Set.of("schemaVersion","mode","status","sourceModelSha256","sourceManifestSha256","sourceL6AReportSha256","formalSeconds","restartSeconds","checkpointCompressionNumerator","checkpointCompressionDenominator","evidenceVolumeAnalysisSha256","planArtifact","sourceTextHashMode")));
        require(java.util.Set.of("CANDIDATE","FROZEN").contains(authority.path("status").asText()));
        require(authority.path("schemaVersion").isIntegralNumber() && authority.path("schemaVersion").asInt() == 1 && MODE.equals(authority.path("mode").asText()));
        require("UTF8_LF_EXACT_CONTENT".equals(authority.path("sourceTextHashMode").asText()));
        require(authority.path("sourceModelSha256").asText().equals(textHash(Files.readAllBytes(B0Processes.root().resolve(L6PgCapacityContract.CANONICAL)))));
        require(authority.path("sourceManifestSha256").asText().equals(manifest.identity().path("sha256").asText()));
        require(authority.path("formalSeconds").equals(new ObjectMapper().readTree("[600,9600,600]")));
        require(authority.path("restartSeconds").equals(new ObjectMapper().readTree("[2400,4800,7200]")));
        require(authority.path("sourceL6AReportSha256").asText().equals(textHash(Files.readAllBytes(
                B0Processes.root().resolve(L6FormalManifest.CANONICAL.resolveSibling("L6_B_ACCEPTED_A_INPUT.json"))))));
        require(authority.path("checkpointCompressionNumerator").isIntegralNumber() && authority.path("checkpointCompressionDenominator").isIntegralNumber()
                && authority.path("checkpointCompressionNumerator").asInt() > 0
                && authority.path("checkpointCompressionNumerator").asInt() <= 16
                && authority.path("checkpointCompressionDenominator").asInt() == 16);
        Path volumePath=B0Processes.root().resolve(CANONICAL.getParent().resolve("runs/L6_B_RUNNER_CLOSURE_20260915/evidence-volume-analysis.json"));
        Path plan=planPath(authority);
        require(authority.path("planArtifact").path("sha256").asText().equals(L6PgCapacityContract.hash(Files.readAllBytes(B0Processes.root().resolve(plan)))));
        require(authority.path("evidenceVolumeAnalysisSha256").asText().equals(textHash(Files.readAllBytes(volumePath))));
        var volume=new ObjectMapper().readTree(Files.readAllBytes(volumePath));
        require(volume.path("frozenNumerator").asInt()==authority.path("checkpointCompressionNumerator").asInt());
        var accepted=new ObjectMapper().readTree(Files.readAllBytes(B0Processes.root().resolve(
                L6FormalManifest.CANONICAL.resolveSibling("L6_B_ACCEPTED_A_INPUT.json"))));
        require(accepted.path("l6AAccepted").asBoolean() && accepted.path("P0").asInt(-1)==0 && accepted.path("P1").asInt(-1)==0);
        if (!probe && !inspectOnly) {
            require("FROZEN".equals(authority.path("status").asText()));
            L6PgCapacityContract.requireCommitted(CANONICAL);
            L6PgCapacityContract.requireCommitted(plan);
            L6PgCapacityContract.requireCommitted(L6FormalManifest.CANONICAL.resolveSibling("L6_B_ACCEPTED_A_INPUT.json"));
            L6PgCapacityContract.requireCommitted(B0Processes.root().relativize(volumePath));
        }
        timing = probe ? new L6DurationContract(60*SECOND, 180*SECOND, 60*SECOND)
                : new L6DurationContract(600*SECOND, 9600*SECOND, 600*SECOND);
        restartSeconds = probe ? new long[]{70, 130, 190} : new long[]{2400, 4800, 7200};
        orders = Math.toIntExact(Math.ceilDiv(timing.activeEnd(), manifest.intervalNanos()));
        samples = Math.toIntExact(Math.ceilDiv(timing.total(), 10*SECOND));
        checkpoints = Math.toIntExact(Math.ceilDiv(timing.total(), 60*SECOND)) + 1 + RESTARTS*3;
        reserve = Math.multiplyExact(base.rate(1), Math.multiplyExact(orders + 1L, 600));
        // 每次重启按整个已接受空载fixture、一次WAL分配阶跃与启动时最多100候选的完整链成本计费。
        restartStorage = RESTARTS * (48_910_336L + base.burst() + Math.min(orders, 100L)*base.backlogUnit());
        long reconciliations = 2*Math.ceilDiv(timing.total(), 5*SECOND) + RESTARTS*(1 + 2*Math.ceilDiv(orders,100));
        journalEvents = 2*100*reconciliations + 16L*orders + 32;
        journalBytes = journalEvents*512;
        long snapshot = 65_536 + orders*8192L;
        long compressed = Math.ceilDiv(snapshot*authority.path("checkpointCompressionNumerator").asLong(),16) + 1024;
        // 每个快照独立gzip；完整Venue事件仅保存一份append-only journal，索引绑定长度与SHA。
        rawCap = Math.ceilDiv(checkpoints*compressed + journalBytes + 128*MIB + snapshot*2, MIB)*MIB;
        require(orders > 0 && orders <= 3000 && samples <= 1080 && checkpoints <= 190);
    }

    static String textHash(byte[] bytes) throws Exception {
        // 仅模型、A报告副本与volume JSON按Git文本换行归一化；保留全部内容与尾部换行。
        // 原文件不重写，运行指纹仍绑定现场原始字节；manifest和计划仍按原raw SHA验证。
        byte[] lf=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).replace("\r\n","\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return L6PgCapacityContract.hash(lf);
    }

    static Path planPath(JsonNode authority) throws Exception {
        var artifact=authority.path("planArtifact");
        require(artifact.isObject() && artifact.size()==2 && artifact.path("sha256").asText().matches("[a-f0-9]{64}"));
        String text=artifact.path("path").asText();Path relative=Path.of(text);
        require(!relative.isAbsolute() && !text.contains("..") && !text.contains("\\") && text.endsWith(".md"));
        require(relative.startsWith(CANONICAL.getParent().getParent()));
        require(B0Processes.root().resolve(relative).toRealPath().startsWith(B0Processes.root().toRealPath()));
        return relative;
    }

    static boolean inRunDirectory() {
        try { return MODE.equals(new ObjectMapper().readTree(Files.readAllBytes(Path.of("parameters.json"))).path("mode").asText()); }
        catch (Exception absent) { return false; }
    }
    static L6FormalManifest runManifest() throws Exception {
        var n = new ObjectMapper().readTree(Files.readAllBytes(Path.of("parameters.json")));
        require(MODE.equals(n.path("mode").asText()));
        var m = L6FormalManifest.read(B0Processes.root().resolve(L6FormalManifest.CANONICAL));
        require(m.identity().path("sha256").asText().equals(n.path("manifestEntry").path("sha256").asText()));
        return m;
    }
    QualificationCapacity capacity() { return manifest.capacity(timing); }
    long growth(long from, long to, long rate, long drainOrders) {
        require(from >= 0 && to >= from && to <= timing.total());
        BigInteger exposure = BigInteger.ZERO;
        long at = from;
        while (at < to) {
            long count = drainOrders >= 0 ? drainOrders : Math.min(orders, at/manifest.intervalNanos()+1);
            long next = drainOrders >= 0 || at >= timing.activeEnd() ? to : Math.min(to,count*manifest.intervalNanos());
            require(next > at);
            exposure = exposure.add(BigInteger.valueOf(next-at).multiply(BigInteger.valueOf(count+1))); at=next;
        }
        return L6PgCapacityContract.ceilRatio(exposure.multiply(BigInteger.valueOf(rate)), SECOND);
    }
    long deriveCapacity(long baseline) {
        require(baseline > 0);
        long total = baseline + reserve + restartStorage + base.burst()
                + growth(0,timing.warmupNanos(),base.rate(0),-1)
                + growth(timing.warmupNanos(),timing.activeEnd(),base.rate(1),-1)
                + growth(timing.activeEnd(),timing.total(),base.rate(2),orders);
        return Math.max(256*MIB,Math.ceilDiv(total,MIB)*MIB);
    }
    ObjectNode capacityEvidence(long preparationBaseline, long actualBaseline, long capacity) {
        require(capacity == deriveCapacity(preparationBaseline) && capacity == deriveCapacity(actualBaseline));
        return new ObjectMapper().createObjectNode().put("preflightResult","PASS")
                .put("preparationBaselineBytes",preparationBaseline).put("formalEntryBaselineBytes",actualBaseline)
                .put("requiredRunCapacityBytes",capacity).put("actualPgTmpfsCapacityBytes",capacity)
                .put("reserveBytes",reserve).put("restartStorageBytes",restartStorage)
                .put("capacityImmutableAfterPgStartup",true);
    }
    void verifyUnchanged() throws Exception {
        base.verifyUnchanged(); manifest.verifyUnchanged();
        require(authoritySha.equals(L6PgCapacityContract.hash(Files.readAllBytes(B0Processes.root().resolve(CANONICAL)))));
    }
    ObjectNode identity() { return new ObjectMapper().createObjectNode().put("mode",MODE).put("sha256",authoritySha)
            .put("probe",probe).put("orders",orders).put("resourceSamples",samples).put("checkpointCap",checkpoints)
            .put("rawCapBytes",rawCap).put("journalEventCap",journalEvents).put("journalByteCap",journalBytes); }
    static void require(boolean valid) { if (!valid) throw new IllegalStateException("L6_B_CONTRACT_REJECTED"); }
}
