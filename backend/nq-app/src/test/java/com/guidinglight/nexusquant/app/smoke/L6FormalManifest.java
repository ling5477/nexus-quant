package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

/** 冻结输入只读；schema 1 的接受状态来自显式接受布尔值和决策 token，不改写历史字段。 */
final class L6FormalManifest {
    static final String INVALID = "FORMAL_CALIBRATION_MANIFEST_INVALID";
    static final Path CANONICAL = Path.of("docs/audit/evidence/phase6-l6/L6_FORMAL_CALIBRATION_MANIFEST.json");
    private static final ObjectMapper JSON = new ObjectMapper().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Path path;
    private final String sha;
    private final String semantic;
    private final JsonNode value;

    private L6FormalManifest(Path path, byte[] bytes, JsonNode value) throws Exception {
        this.path = path.toAbsolutePath().normalize(); this.sha = hash(bytes); this.value = value.deepCopy();
        this.semantic = hash(JSON.writeValueAsBytes(sorted(value)));
    }

    static L6FormalManifest read(Path path) {
        try {
            require(Files.isRegularFile(path) && Files.size(path) > 0 && Files.size(path) <= 2_000_000);
            byte[] bytes = Files.readAllBytes(path);
            JsonNode n = JSON.readTree(bytes);
            require(n != null && n.isObject() && n.path("schemaVersion").isIntegralNumber() && n.path("schemaVersion").asInt() == 1);
            require(n.path("formalCalibrationAccepted").isBoolean() && n.path("formalCalibrationAccepted").asBoolean());
            require(List.of(n.path("decision").asText().split(" / ")).contains("FORMAL_CALIBRATION_MANIFEST_ACCEPTED"));
            require(!n.has("status") || "ACCEPTED".equals(n.path("status").asText()));
            require(n.path("sourceHead").asText().matches("[a-f0-9]{40}") && n.path("sourceTree").asText().matches("[a-f0-9]{40}"));
            require(n.path("runId").asText().matches("[a-f0-9-]{36}"));
            var ci = n.path("exactHeadCi");
            require(ci.path("runId").isIntegralNumber() && ci.path("runId").asLong() > 0 && "SUCCESS".equals(ci.path("status").asText()));
            require(ci.path("head").asText().equals(n.path("sourceHead").asText()) && ci.path("jobs").asInt() == 9);
            positive(n, "healthySustainedRate"); positive(n, "finalL6ArrivalRate"); positive(n, "pacingInterval");
            require(n.path("finalL6ArrivalRate").decimalValue().compareTo(BigDecimal.ONE) <= 0);
            require("orders/second".equals(n.path("units").asText()) && "seconds".equals(n.path("pacingIntervalUnits").asText()));
            require(n.path("pacingIntervalNanos").isIntegralNumber() && n.path("pacingIntervalNanos").canConvertToLong());
            long interval = n.path("pacingIntervalNanos").longValue();
            require(interval >= 1_000_000_000L && n.path("pacingInterval").decimalValue().movePointRight(9).longValueExact() == interval);
            require(!n.path("roundingPolicy").asText().isBlank());
            var sampling = n.path("sampling");
            require(sampling.path("intervalMillis").asLong() == L6ResourceSampler.INTERVAL_MILLIS
                    && sampling.path("measurementSampleCount").asInt() == 60);
            for (String k : List.of("mandatoryMissing", "staleReuse", "cadenceViolation")) zero(sampling, k);
            require(n.path("warmupSeconds").asInt() == 300 && n.path("measurementSeconds").asInt() == 600);
            require(n.path("measurement").path("fullChainCompleted").asInt() == 240);
            var c = n.path("correctness");
            require(c.path("checkpointsVerified").asInt() == 181);
            for (String k : List.of("P0", "P1", "duplicateExternalMutation", "duplicateTrade", "duplicateTradeExecuted",
                    "duplicateAccounting", "filledToFilledErrors", "unauthorizedSend", "durableOrphan", "unexpectedValidationDegradation", "hikariAcquisitionTimeoutDelta")) zero(c, k);
            for (String k : List.of("tradeFillIdentityExact", "ledgerBalanced", "positionSnapshotReconstructable")) require(c.path(k).isBoolean() && c.path(k).asBoolean());
            require("PASS".equals(n.path("cleanup").path("status").asText())); zero(n.path("cleanup"), "ownedSurvivors");
            validateBands(n.path("noiseBands"));
            return new L6FormalManifest(path, bytes, n);
        } catch (Exception error) { throw new IllegalStateException(INVALID, error); }
    }

    private static void validateBands(JsonNode bands) {
        require(bands.path("heap").isArray() && bands.path("heap").size() == 2);
        for (String actor : List.of("nq0", "nq1")) {
            int count = 0;
            for (var b : bands.path("heap")) if (actor.equals(b.path("owner").asText())) {
                count++; require("FROZEN".equals(b.path("status").asText()) && "bytes".equals(b.path("units").asText())
                        && "G1 Young Generation".equals(b.path("collector").asText()) && b.path("sampleCount").asInt() >= 2);
                for (String key : List.of("lowWaterMin", "lowWaterMax", "range")) nonnegative(b, key);
            }
            require(count == 1);
        }
        require(bands.path("resources").isArray());
        for (String required : List.of("nq0:.threads", "nq1:.threads", "nq0:.active", "nq1:.active", "nq0:.pending", "nq1:.pending",
                "nq0:.commandQueue.queueSize", "nq1:.commandQueue.queueSize", "postgres:.databaseConnections", "os:.handles",
                "venue:.queue", "files:.ownedTempFileCount", "files:.ownedTempBytes")) {
            int count = 0;
            for (var b : bands.path("resources")) if (required.equals(b.path("source").asText() + ":" + b.path("field").asText())) {
                count++; require(b.path("frozenNoiseBand").asBoolean() && b.path("sampleCount").asInt() == 60);
                for (String key : List.of("min", "max", "maxMinusMin")) nonnegative(b, key);
            }
            require(count == 1);
        }
        require(bands.path("growth").isArray() && bands.path("growth").size() == 3);
        for (var b : bands.path("growth")) for (String key : List.of("absoluteGrowth", "perOrderGrowth", "perFullChainGrowth")) nonnegative(b, key);
    }

    private static void positive(JsonNode n, String key) { nonnegative(n, key); require(n.path(key).decimalValue().signum() > 0); }
    private static void nonnegative(JsonNode n, String key) { require(n.path(key).isNumber() && Double.isFinite(n.path(key).asDouble()) && n.path(key).decimalValue().signum() >= 0); }
    private static void zero(JsonNode n, String key) { nonnegative(n, key); require(n.path(key).decimalValue().signum() == 0); }
    private static void require(boolean condition) { if (!condition) throw new IllegalArgumentException(INVALID); }
    private static Object sorted(JsonNode n) {
        if (n.isObject()) { var map = new TreeMap<String, Object>(); n.fields().forEachRemaining(e -> map.put(e.getKey(), sorted(e.getValue()))); return map; }
        if (n.isArray()) { var list = new java.util.ArrayList<>(); n.forEach(v -> list.add(sorted(v))); return list; }
        return n;
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    long intervalNanos() { return value.path("pacingIntervalNanos").longValue(); }
    static L6DurationContract timing(boolean smoke) {
        return smoke ? new L6DurationContract(20_000_000_000L, 50_000_000_000L, 30_000_000_000L) : L6DurationContract.forMode(false);
    }
    /** 子进程从本run只读参数绑定canonical文件，不放宽B0环境白名单。 */
    static L6FormalManifest fromRunDirectory() throws Exception {
        JsonNode parameters = JSON.readTree(Files.readAllBytes(Path.of("parameters.json")));
        require("FORMAL_L6_A".equals(parameters.path("mode").asText()) && parameters.path("shortSmoke").isBoolean());
        var manifest = read(B0Processes.root().resolve(CANONICAL));
        require(manifest.sha.equals(parameters.path("manifestEntry").path("sha256").asText()));
        return manifest;
    }
    JsonNode frozen() { return value.deepCopy(); }
    void verifyUnchanged() throws Exception { if (!hash(Files.readAllBytes(path)).equals(sha)) throw new IllegalStateException("FORMAL_CALIBRATION_MANIFEST_DRIFT"); }
    ObjectNode identity() { return JSON.createObjectNode().put("path", path.toString()).put("sha256", sha).put("semanticFingerprint", semantic); }

    QualificationCapacity capacity(L6DurationContract duration) {
        int maximum = Math.toIntExact(Math.ceilDiv(duration.activeEnd(), intervalNanos()));
        var capacity = new QualificationCapacity(QualificationCapacity.Mode.L6_FORMAL, maximum, maximum, maximum);
        capacity.validate(); return capacity;
    }
    static void start(Path path, L6DurationContract duration, Ready runtime) throws Exception {
        var manifest = read(path); var capacity = manifest.capacity(duration);
        try { runtime.run(manifest, capacity); } finally { manifest.verifyUnchanged(); }
    }
    @FunctionalInterface interface Ready { void run(L6FormalManifest manifest, QualificationCapacity capacity) throws Exception; }
}
