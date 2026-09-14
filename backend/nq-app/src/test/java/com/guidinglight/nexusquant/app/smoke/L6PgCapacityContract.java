package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 已提交合同只冻结增长模型；容量由本次同生命周期测量推导。 */
final class L6PgCapacityContract {
    static final long MIB = 1_048_576;
    static final Path CANONICAL = L6FormalManifest.CANONICAL.resolveSibling("L6_PG_TMPFS_CAPACITY_CONTRACT.json");
    static final String INVALID = "L6_PG_TMPFS_CAPACITY_CONTRACT_INVALID";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Path file;
    private final String sha;
    private final JsonNode value;

    private L6PgCapacityContract(Path file, byte[] bytes, JsonNode value) throws Exception {
        this.file = file; this.sha = hash(bytes); this.value = value;
    }

    static L6PgCapacityContract read(Path file) {
        try {
            require(Files.isRegularFile(file) && Files.size(file) <= 100_000);
            byte[] bytes = Files.readAllBytes(file); JsonNode n = JSON.readTree(bytes);
            require(n != null && n.isObject());
            var fields = new TreeSet<String>(); n.fieldNames().forEachRemaining(fields::add);
            require(fields.equals(Set.of("schemaVersion", "status", "scope", "sourceHead", "sourceStorageCalibrationRun",
                    "sourceArtifactHashes", "sourceRawArchiveSha256", "manifestSha256", "observed", "projection",
                    "hostMemory", "samplingIntervalSeconds", "limitations")));
            require(n.path("schemaVersion").isIntegralNumber() && n.path("schemaVersion").asInt() == 2);
            require("ACCEPTED".equals(n.path("status").asText()) && "L6_A_60MIN".equals(n.path("scope").asText()));
            require(n.path("sourceHead").asText().matches("[a-f0-9]{40}"));
            require(n.path("sourceStorageCalibrationRun").asText().matches("[a-f0-9-]{36}"));
            for (String key : List.of("sourceRawArchiveSha256", "manifestSha256")) require(n.path(key).asText().matches("[a-f0-9]{64}"));
            require(n.path("samplingIntervalSeconds").isIntegralNumber() && n.path("samplingIntervalSeconds").asInt() == 10);
            var model = n.path("projection");
            var observed = n.path("observed");
            for (String key : List.of("peakUsedBytes", "startUsedBytes", "maxRolling10MinuteGrowthBytes", "drainGrowthBytes", "measurementGrowthBytes", "measurementFullChainDelta")) positive(observed, key);
            require(positive(observed, "startUsedBytes") == positive(model.path("calibrationReference"), "baselineBytes"));
            require(n.path("limitations").isArray() && n.path("limitations").toString().contains("L6B_CAPACITY_REQUIRES_SEPARATE_PROJECTION"));
            require("ORDER_TIME_ENVELOPE_V1".equals(model.path("model").asText()));
            require(model.path("formalSeconds").equals(JSON.readTree("[600,2400,600]")));
            var contract = new L6PgCapacityContract(file.toAbsolutePath().normalize(), bytes, n);
            require(contract.interval() == L6FormalManifest.read(B0Processes.root().resolve(L6FormalManifest.CANONICAL)).intervalNanos());
            require(contract.maximumOrders() == Math.ceilDiv(3_000_000_000_000L, contract.interval()));
            long[] limits = {0, 600_000_000_000L, 3_000_000_000_000L, 3_600_000_000_000L};
            long sum = positive(model.path("calibrationReference"), "baselineBytes");
            String[] keys = {"projectedWarmupGrowthBytes", "projected40minActiveGrowthBytes", "projected10minDrainGrowthBytes"};
            for (int i = 0; i < 3; i++) {
                long expected = contract.growth(limits[i], limits[i+1], contract.rate(i), -1);
                require(expected == positive(model, keys[i])); sum = Math.addExact(sum, expected);
            }
            require(sum == positive(model.path("calibrationReference"), "projectedEndBytes"));
            require(contract.reserve() == Math.multiplyExact(contract.rate(1), Math.multiplyExact(1 + contract.maximumOrders(), 600)));
            require(contract.referenceCapacity() == Math.multiplyExact(Math.ceilDiv(Math.addExact(sum, contract.reserve()), MIB), MIB));
            require(contract.referenceCapacity() >= positive(n.path("observed"), "peakUsedBytes"));
            require("ACTORS_READY_PAPER_READY_VENUE_OPEN_BEFORE_WORKLOAD".equals(model.path("baselineLifecycle").asText()));
            require(positive(model, "allocationGranularityBytes") == MIB && positive(model, "minimumCapacityBytes") == 256*MIB);
            positive(model, "allocationBurstBytes");
            require("MAX_POSITIVE_ACCEPTED_PERIODIC_10S_DELTA_CONSUMABLE_NOT_REQUIRED_FREE".equals(model.path("allocationBurstRule").asText()));
            for (String k : List.of("nqHeapEachBytes", "venueHeapBytes", "controllerHeapBytes", "mavenHeapBytes", "nativeAndToolsBudgetBytes", "pgNonTmpfsBudgetBytes")) positive(n.path("hostMemory"), k);
            require(n.path("hostMemory").path("maximumFraction").isNumber()
                    && n.path("hostMemory").path("maximumFraction").decimalValue().compareTo(new java.math.BigDecimal("0.60")) == 0);
            require(n.path("hostMemory").path("nqHeapEachBytes").asLong() == 512*MIB
                    && n.path("hostMemory").path("venueHeapBytes").asLong() == 256*MIB);
            positive(model, "backlogBytesPerChain");
            require(contract.backlogUnit() == Math.ceilDiv(positive(observed, "measurementGrowthBytes"), positive(observed, "measurementFullChainDelta")));
            require(n.path("sourceArtifactHashes").isObject() && n.path("sourceArtifactHashes").size() == 1);
            var source = n.path("sourceArtifactHashes").fields().next();
            Path relative = Path.of(source.getKey());
            require(!relative.isAbsolute() && relative.equals(relative.normalize())
                    && relative.getFileName().toString().equals("calibration-replay.zip")
                    && relative.getParent().getFileName().toString().matches("[A-Za-z0-9_-]+")
                    && relative.getParent().getParent().equals(CANONICAL.getParent().resolve("runs")));
            Path archive = B0Processes.root().resolve(source.getKey());
            require(Files.size(archive) <= 4*MIB && hash(Files.readAllBytes(archive)).equals(source.getValue().asText()));
            require(contract.burst() == replayAllocationBurst(archive));
            require(hash(Files.readAllBytes(B0Processes.root().resolve(L6FormalManifest.CANONICAL))).equals(n.path("manifestSha256").asText()));
            return contract;
        } catch (Exception failure) { throw new IllegalStateException(INVALID, failure); }
    }

    static L6PgCapacityContract committed() throws Exception {
        try {
        Path root = B0Processes.root(); var c = read(root.resolve(CANONICAL));
        requireCommitted(CANONICAL); requireCommitted(L6FormalManifest.CANONICAL);
        for (var it = c.value.path("sourceArtifactHashes").fieldNames(); it.hasNext();) requireCommitted(Path.of(it.next()));
        return c;
        } catch (Exception failure) { throw new IllegalStateException("BLOCKED / " + INVALID, failure); }
    }

    static void requireCommitted(Path relative) throws Exception {
        String path = relative.toString().replace('\\', '/');
        String working = B0Processes.command("git", "-C", B0Processes.root().toString(), "hash-object", path);
        String head = B0Processes.command("git", "-C", B0Processes.root().toString(), "rev-parse", "HEAD:" + path);
        require(working.equals(head));
    }

    static void require(boolean valid) { if (!valid) throw new IllegalArgumentException(INVALID); }
    static long positive(JsonNode node, String key) {
        JsonNode n = node.path(key); require(n.isIntegralNumber() && n.canConvertToLong() && n.longValue() > 0); return n.longValue();
    }
    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static long replayAllocationBurst(Path archive) throws Exception {
        try (var zip = new java.util.zip.ZipFile(archive.toFile())) {
            var entry = zip.getEntry("resources.ndjson");
            require(entry != null && entry.getSize() > 0 && entry.getSize() <= 8*MIB);
            byte[] bytes;
            try (var input = zip.getInputStream(entry)) { bytes = input.readNBytes((int) (8*MIB+1)); }
            require(bytes.length <= 8*MIB);
            long previous = -1, burst = 0; int index = 0;
            for (String line : new String(bytes, java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                var row = JSON.readTree(line);
                require("MEASURED".equals(row.path("status").asText()) && "PERIODIC".equals(row.path("sampleType").asText())
                        && row.path("sampleIndex").asInt(-1) == index && row.path("scheduledElapsedMillis").asLong(-1) == index*10_000L);
                long used = positive(row.path("sources").path("postgres").path("values"), "pgTmpfsUsedBytes");
                if (previous >= 0) burst = Math.max(burst, used-previous);
                previous = used; index++;
            }
            require(index == 210 && burst > 0);
            return burst;
        }
    }
    long referenceCapacity() { return positive(value.path("projection").path("calibrationReference"), "legacyCapacityBytes"); }
    long burst() { return positive(value.path("projection"), "allocationBurstBytes"); }
    long growth(int phase) {
        long[] bounds = {0, 600_000_000_000L, 3_000_000_000_000L, 3_600_000_000_000L};
        return growth(bounds[phase], bounds[phase+1], rate(phase), -1);
    }
    long deriveCapacity(long baseline) {
        require(baseline > 0);
        long total = Math.addExact(baseline, Math.addExact(reserve(), burst()));
        for (int phase = 0; phase < 3; phase++) total = Math.addExact(total, growth(phase));
        return Math.max(256*MIB, Math.multiplyExact(Math.ceilDiv(total, MIB), MIB));
    }
    String manifestSha() { return value.path("manifestSha256").asText(); }
    String sourceRelative() { return value.path("sourceArtifactHashes").fieldNames().next(); }
    long interval() { return positive(value.path("projection"), "pacingIntervalNanos"); }
    long maximumOrders() { return positive(value.path("projection"), "maximumOrders"); }
    long reserve() { return positive(value.path("projection"), "reserveBytes"); }
    long backlogUnit() { return positive(value.path("projection"), "backlogBytesPerChain"); }
    long memory(String key) { return positive(value.path("hostMemory"), key); }
    long pgMemory(long capacity) { return Math.addExact(memory("pgNonTmpfsBudgetBytes"), capacity); }
    long rate(int phase) { return positive(value.path("projection"), List.of("warmupBytesPerOrderSecond", "activeBytesPerOrderSecond", "drainBytesPerOrderSecond").get(phase)); }

    /** 纳秒积分保留slot边界；DRAIN只计算已有库存，不新增潜在producer。 */
    long growth(long from, long to, long rate, long drainOrders) {
        require(from >= 0 && to >= from && to <= 3_600_000_000_000L && rate > 0);
        BigInteger exposure = BigInteger.ZERO;
        long cursor = from;
        while (cursor < to) {
            long count = drainOrders >= 0 ? drainOrders : Math.min(maximumOrders(), cursor / interval() + 1);
            long next = drainOrders >= 0 || cursor >= 3_000_000_000_000L ? to : Math.min(to, Math.multiplyExact(count, interval()));
            require(next > cursor);
            exposure = exposure.add(BigInteger.valueOf(next-cursor).multiply(BigInteger.valueOf(count+1))); cursor = next;
        }
        return ceilRatio(exposure.multiply(BigInteger.valueOf(rate)), 1_000_000_000L);
    }
    static long ceilRatio(BigInteger numerator, long denominator) {
        require(numerator.signum() >= 0 && denominator > 0);
        return numerator.add(BigInteger.valueOf(denominator-1)).divide(BigInteger.valueOf(denominator)).longValueExact();
    }
    ObjectNode identity() { return JSON.createObjectNode().put("path", file.toString()).put("sha256", sha)
            .put("scope", "L6_A_60MIN").put("authority", "FROZEN_CAPACITY_MODEL"); }
    void verifyUnchanged() throws Exception { require(hash(Files.readAllBytes(file)).equals(sha)); }
}
