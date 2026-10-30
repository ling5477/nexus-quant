package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.domain.port.ResearchDatasetVersionPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 只核验归属、数据身份和离线报告格式；客户端统计永远不是 Java 绩效或交易授权。 */
@Service
public class FactorResearchService {
    public static final int MAX_ARTIFACT_BYTES = 16 * 1024 * 1024;
    private static final Set<String> KEYS = Set.of("RETURN_MOMENTUM", "REALIZED_VOLATILITY", "VOLUME_ANOMALY");
    private final ResearchDatasetVersionPort versions;
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper();

    public FactorResearchService(ResearchDatasetVersionPort versions, ObjectMapper mapper) {
        this.versions = versions;
    }

    public JsonNode export(UUID datasetId) { return versions.export(datasetId); }

    public JsonNode preview(UUID datasetId, JsonNode artifact) {
        fields(artifact, "schemaVersion", "artifactId", "dataset", "barCount", "factors", "observationAssumptions",
                "limitations", "warnings", "source", "generatedAt", "implementationVersion", "numericalRuntime",
                "diagnosticOnly", "notTradingAuthorization", "liveExecutionReady", "checksum");
        require("python-factor-research-artifact.v1".equals(artifact.path("schemaVersion").asText()));
        require(artifact.path("diagnosticOnly").isBoolean() && artifact.path("diagnosticOnly").asBoolean()
                && artifact.path("notTradingAuthorization").isBoolean() && artifact.path("notTradingAuthorization").asBoolean()
                && artifact.path("liveExecutionReady").isBoolean() && !artifact.path("liveExecutionReady").asBoolean()
                && "PYTHON_OFFLINE_RESEARCH".equals(artifact.path("source").asText())
                && "factor-research-1.0.0".equals(artifact.path("implementationVersion").asText()));
        ObjectNode unsigned = artifact.deepCopy();
        unsigned.remove("checksum");
        require(sha256(canonical(unsigned)).equals(artifact.path("checksum").asText()));
        unsigned.remove("artifactId");
        require(("factor-" + sha256(canonical(unsigned))).equals(artifact.path("artifactId").asText()));

        JsonNode input = export(datasetId), dataset = input.path("dataset"), reference = dataset.path("frozenInput");
        JsonNode identity = artifact.path("dataset");
        fields(identity, "datasetId", "datasetVersionId", "contentSha256", "sourceSha256", "manifestSha256",
                "exchange", "symbol", "interval", "qualityStatus");
        require(datasetId.toString().equals(identity.path("datasetId").asText()));
        for (String name : List.of("datasetVersionId", "sourceSha256", "manifestSha256"))
            require(reference.path(name).equals(identity.path(name)));
        require(reference.path("sha256").equals(identity.path("contentSha256")));
        require(dataset.path("exchangeCode").equals(identity.path("exchange")));
        for (String name : List.of("symbol", "interval", "qualityStatus"))
            require(dataset.path(name).equals(identity.path(name)));
        int count = dataset.path("barCount").asInt();
        require(artifact.path("barCount").isInt() && artifact.path("barCount").asInt() == count);
        JsonNode assumptions = artifact.path("observationAssumptions");
        fields(assumptions, "sourceObservedAt", "featureAvailableAt", "replayVisibilitySource", "frozenAt");
        require("ACTUAL_COLLECTION_OBSERVATION".equals(assumptions.path("sourceObservedAt").asText())
                && "CLOSED_HOURLY_BOUNDARY_V1".equals(assumptions.path("featureAvailableAt").asText())
                && "EXPERIMENT_ASSUMPTION".equals(assumptions.path("replayVisibilitySource").asText())
                && reference.path("frozenAt").equals(assumptions.path("frozenAt")));
        Instant frozen = Instant.parse(reference.path("frozenAt").asText());
        require(!Instant.parse(artifact.path("generatedAt").asText()).isBefore(frozen));
        strings(artifact.path("limitations"), 1, 16);
        strings(artifact.path("warnings"), 3, 16);
        require(artifact.path("warnings").toString().contains("NO_AUTOMATIC_STRATEGY_APPROVAL"));
        fields(artifact.path("numericalRuntime"), "numpy");
        require(artifact.path("numericalRuntime").path("numpy").asText().matches("[0-9.]{3,32}"));
        JsonNode factors = artifact.path("factors");
        require(factors.isArray() && factors.size() == 3);
        Set<String> seen = new TreeSet<>();
        Instant start = Instant.parse(dataset.path("startTime").asText());
        for (JsonNode factor : factors) {
            fields(factor, "factorKey", "factorVersion", "parameters", "datasetVersionId", "sourceChecksum", "points", "statistics");
            String key = factor.path("factorKey").asText();
            require(KEYS.contains(key) && seen.add(key) && "1.0.0".equals(factor.path("factorVersion").asText())
                    && factor.path("datasetVersionId").equals(reference.path("datasetVersionId"))
                    && factor.path("sourceChecksum").equals(reference.path("sourceSha256")));
            JsonNode parameters = factor.path("parameters");
            fields(parameters, "window", "definition", "ddof", "annualized");
            int window = integer(parameters, "window", 2, 200);
            require(parameters.path("ddof").isInt() && parameters.path("ddof").asInt() == 1
                    && parameters.path("annualized").isBoolean() && !parameters.path("annualized").asBoolean());
            String definition = switch (key) {
                case "RETURN_MOMENTUM" -> "close[t]/close[t-window]-1";
                case "REALIZED_VOLATILITY" -> "sample_std(log(close[j]/close[j-1]),j=t-window+1..t)";
                default -> "(volume[t]-mean(volume[t-window:t]))/sample_std(volume[t-window:t])";
            };
            require(definition.equals(parameters.path("definition").asText()));
            JsonNode points = factor.path("points");
            require(points.isArray() && points.size() == count);
            for (int i = 0; i < count; i++) {
                JsonNode point = points.get(i);
                fields(point, "index", "windowStart", "windowEnd", "featureAvailableAt", "factorValue", "qualityStatus");
                require(integer(point, "index", i, i) == i
                        && Instant.parse(point.path("windowStart").asText()).equals(start.plusSeconds(3600L * Math.max(0, i - window)))
                        && Instant.parse(point.path("windowEnd").asText()).equals(start.plusSeconds(3600L * (i + 1)).minusMillis(1))
                        && Instant.parse(point.path("featureAvailableAt").asText()).equals(start.plusSeconds(3600L * (i + 1))));
                String quality = point.path("qualityStatus").asText();
                if ("AVAILABLE".equals(quality)) { require(i >= window); decimal(point.path("factorValue"), false); }
                else require(point.path("factorValue").isNull() && (i < window && "INSUFFICIENT_HISTORY".equals(quality)
                        || i >= window && "VOLUME_ANOMALY".equals(key) && "ZERO_REFERENCE_VARIANCE".equals(quality)));
            }
            statistics(factor.path("statistics"), points, count, window, start);
        }
        return artifact;
    }

    private void statistics(JsonNode stats, JsonNode points, int count, int window, Instant start) {
        fields(stats, "methodVersion", "correlationStatistic", "labelDefinition", "horizonBars", "splitIndex",
                "purgedTrainingSamples", "unlabelledTailSamples", "bootstrap", "windows");
        int horizon = integer(stats, "horizonBars", 1, 48), split = count * 7 / 10;
        require("PURGED_CHRONOLOGICAL_PEARSON_MOVING_BLOCK_V1".equals(stats.path("methodVersion").asText())
                && "PEARSON".equals(stats.path("correlationStatistic").asText())
                && "close[t+horizon]/close[t]-1".equals(stats.path("labelDefinition").asText()));
        integer(stats, "splitIndex", split, split);
        integer(stats, "purgedTrainingSamples", horizon, horizon);
        integer(stats, "unlabelledTailSamples", horizon, horizon);
        JsonNode bootstrap = stats.path("bootstrap");
        fields(bootstrap, "method", "repetitions", "seed", "rng", "quantileMethod");
        int repetitions = integer(bootstrap, "repetitions", 100, 1000);
        require(bootstrap.path("seed").isIntegralNumber() && bootstrap.path("seed").asLong(-1) >= 0
                && bootstrap.path("seed").asLong() <= 4294967295L
                && "PAIRED_MOVING_BLOCK_PERCENTILE".equals(bootstrap.path("method").asText())
                && "PCG64".equals(bootstrap.path("rng").asText()) && "linear".equals(bootstrap.path("quantileMethod").asText()));
        require(stats.path("windows").isArray() && stats.path("windows").size() == 2);
        for (int i = 0; i < 2; i++) {
            JsonNode result = stats.path("windows").get(i);
            fields(result, "name", "startIndex", "endIndexExclusive", "featureFrom", "featureTo", "lastLabelAvailableAt",
                    "candidateSamples", "effectiveSamples", "missingSamples", "minimumSamples", "correlation",
                    "confidenceInterval", "status", "blockLength", "bootstrapValidReplicates");
            int from = i == 0 ? 0 : split, to = i == 0 ? split - horizon : count - horizon;
            require((i == 0 ? "TRAIN" : "OUT_OF_SAMPLE").equals(result.path("name").asText()));
            integer(result, "startIndex", from, from); integer(result, "endIndexExclusive", to, to);
            integer(result, "candidateSamples", to - from, to - from);
            int effective = integer(result, "effectiveSamples", 0, to - from);
            int expected = 0;
            for (int index = from; index < to; index++)
                if ("AVAILABLE".equals(points.get(index).path("qualityStatus").asText())) expected++;
            require(effective == expected
                    && Instant.parse(result.path("featureFrom").asText()).equals(start.plusSeconds((from + 1L) * 3600))
                    && Instant.parse(result.path("featureTo").asText()).equals(start.plusSeconds(to * 3600L))
                    && Instant.parse(result.path("lastLabelAvailableAt").asText()).equals(start.plusSeconds((to + (long) horizon) * 3600)));
            integer(result, "missingSamples", to - from - effective, to - from - effective);
            int block = Math.max(Math.max(window, horizon), (int) Math.ceil(Math.cbrt(to - from)));
            integer(result, "blockLength", block, block);
            int minimum = Math.max(60, 5 * block);
            integer(result, "minimumSamples", minimum, minimum);
            integer(result, "bootstrapValidReplicates", 0, repetitions);
            decimal(result.path("correlation"), true);
            String status = result.path("status").asText();
            require(Set.of("INSUFFICIENT_SAMPLES", "INCONCLUSIVE", "EXPLORATORY_RELATIONSHIP").contains(status));
            require((effective < minimum) == "INSUFFICIENT_SAMPLES".equals(status));
            JsonNode interval = result.path("confidenceInterval");
            if (!interval.isNull()) {
                fields(interval, "lower", "upper", "level");
                decimal(interval.path("lower"), true); decimal(interval.path("upper"), true);
                require("0.95".equals(interval.path("level").asText())
                        && effective >= minimum && !result.path("correlation").isNull()
                        && result.path("bootstrapValidReplicates").asInt() >= repetitions * 9 / 10
                        && new BigDecimal(interval.path("lower").asText()).compareTo(new BigDecimal(interval.path("upper").asText())) <= 0);
            }
            require(!"EXPLORATORY_RELATIONSHIP".equals(status) || !interval.isNull());
            if (!interval.isNull()) {
                boolean excludesZero = new BigDecimal(interval.path("lower").asText()).signum() > 0
                        || new BigDecimal(interval.path("upper").asText()).signum() < 0;
                require(excludesZero == "EXPLORATORY_RELATIONSHIP".equals(status));
            }
        }
    }

    static String canonical(JsonNode node) {
        if (node.isObject()) {
            TreeSet<String> names = new TreeSet<>(); node.fieldNames().forEachRemaining(names::add);
            List<String> pairs = names.stream().map(name -> {
                try { return CANONICAL_MAPPER.writeValueAsString(name) + ":" + canonical(node.path(name)); }
                catch (java.io.IOException error) { throw new IllegalArgumentException("ARTIFACT_JSON_INVALID", error); }
            }).toList();
            return "{" + String.join(",", pairs) + "}";
        }
        if (node.isArray()) {
            List<String> values = new java.util.ArrayList<>(); node.forEach(child -> values.add(canonical(child)));
            return "[" + String.join(",", values) + "]";
        }
        require(!node.isFloatingPointNumber());
        return node.toString();
    }

    static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static void fields(JsonNode node, String... fields) {
        require(node != null && node.isObject());
        Set<String> actual = new TreeSet<>(); node.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(fields)));
    }
    private static int integer(JsonNode node, String field, int min, int max) {
        JsonNode value = node.path(field);
        require(value.isInt() && value.asInt() >= min && value.asInt() <= max);
        return value.asInt();
    }
    private static void strings(JsonNode array, int min, int max) {
        require(array.isArray() && array.size() >= min && array.size() <= max);
        array.forEach(value -> require(value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 1024));
    }
    private static void decimal(JsonNode value, boolean correlation) {
        if (value.isNull() && correlation) return;
        require(value.isTextual() && value.asText().length() <= 64);
        BigDecimal number = new BigDecimal(value.asText());
        require(Double.isFinite(number.doubleValue()) && (!correlation || number.abs().compareTo(BigDecimal.ONE) <= 0));
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("FACTOR_ARTIFACT_INVALID_OR_IDENTITY_MISMATCH");
    }
}
