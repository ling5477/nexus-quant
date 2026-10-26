package com.guidinglight.nexusquant.marketdata.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 研究输入使用原快照列保存完整序列，目录刷新或共享行情修订不能改变已绑定内容。 */
public final class FrozenHistoricalInput {
    public static final int MAX_BARS = 720;
    public static final String VERSION = "HISTORICAL_RESEARCH_INPUT_V1";
    private FrozenHistoricalInput() {}

    public static boolean supports(JsonNode dataset) {
        return "db".equals(dataset.path("provider").asText())
                && "OKX".equals(dataset.path("exchangeCode").asText())
                && "SPOT".equals(dataset.path("marketType").asText())
                && Set.of("BTC-USDT", "ETH-USDT").contains(dataset.path("symbol").asText())
                && "1h".equals(dataset.path("interval").asText());
    }

    public static ObjectNode freeze(JsonNode dataset, List<HistoricalBar> source,
            Instant frozenAt, ObjectMapper mapper) {
        if (source.isEmpty() || source.size() > MAX_BARS)
            throw new IllegalStateException("FROZEN_INPUT_COVERAGE_INVALID");
        List<HistoricalBar> replay = new ArrayList<>(source.size());
        var observations = mapper.createArrayNode();
        for (HistoricalBar bar : source) {
            if (bar.availableAt() == null || bar.availableAt().isAfter(frozenAt))
                throw new IllegalStateException("FROZEN_INPUT_OBSERVATION_INVALID");
            validateSource(bar, mapper);
            observations.add(bar.availableAt().toString());
            replay.add(new HistoricalBar(bar.exchangeCode(), bar.marketType(), bar.symbol(),
                    bar.interval(), bar.openTime(), bar.closeTime(), bar.openPrice(), bar.highPrice(),
                    bar.lowPrice(), bar.closePrice(), bar.volume(), bar.quoteVolume(), bar.tradeCount(),
                    bar.qualityStatus(), bar.rawPayloadJson(), bar.openTime().plus(bar.interval().duration())));
        }
        validate(dataset, replay, frozenAt);
        var identity = SpotBarIdentity.capture(replay, mapper);
        ObjectNode result = mapper.createObjectNode();
        result.put("version", VERSION);
        result.put("sha256", identity.sha256());
        result.put("sourceSha256", SpotBarIdentity.capture(source, mapper).sha256());
        result.put("frozenAt", frozenAt.toString());
        result.put("source", dataset.path("source").asText());
        result.put("replayVisibilityVersion", "CLOSED_HOURLY_BOUNDARY_V1");
        result.put("replayVisibilitySource", "EXPERIMENT_ASSUMPTION");
        result.set("sourceObservedAt", observations);
        try { result.set("bars", mapper.readTree(identity.canonicalJson())); }
        catch (java.io.IOException ex) { throw new IllegalStateException("FROZEN_INPUT_JSON_INVALID", ex); }
        return result;
    }

    public static List<HistoricalBar> read(JsonNode dataset, ObjectMapper mapper) {
        JsonNode frozen = dataset.path("frozenInput");
        JsonNode rows = frozen.path("bars");
        if (!VERSION.equals(frozen.path("version").asText()) || !rows.isArray() || rows.isEmpty() || rows.size() > MAX_BARS
                || !"CLOSED_HOURLY_BOUNDARY_V1".equals(frozen.path("replayVisibilityVersion").asText())
                || !"EXPERIMENT_ASSUMPTION".equals(frozen.path("replayVisibilitySource").asText())
                || !dataset.path("source").asText().equals(frozen.path("source").asText())
                || !frozen.path("sourceObservedAt").isArray()
                || frozen.path("sourceObservedAt").size() != rows.size()) {
            throw new IllegalStateException("FROZEN_INPUT_IDENTITY_INVALID");
        }
        List<HistoricalBar> bars = new ArrayList<>(rows.size());
        for (JsonNode bar : rows) {
            bars.add(new HistoricalBar(bar.path("exchangeCode").asText(), bar.path("marketType").asText(),
                    bar.path("symbol").asText(), BarInterval.fromWireValue(bar.path("interval").asText()),
                    Instant.parse(bar.path("openTime").asText()), Instant.parse(bar.path("closeTime").asText()),
                    decimal(bar, "openPrice"), decimal(bar, "highPrice"), decimal(bar, "lowPrice"),
                    decimal(bar, "closePrice"), decimal(bar, "volume"),
                    bar.path("quoteVolume").isNull() ? null : decimal(bar, "quoteVolume"),
                    bar.path("tradeCount").isNull() ? null : bar.path("tradeCount").asLong(),
                    bar.path("qualityStatus").asText(), bar.path("rawPayloadJson").asText(),
                    Instant.parse(bar.path("availableAt").asText())));
        }
        Instant frozenAt = Instant.parse(frozen.path("frozenAt").asText());
        validate(dataset, bars, frozenAt);
        List<HistoricalBar> source = new ArrayList<>(bars.size());
        for (int index = 0; index < bars.size(); index++) {
            HistoricalBar bar = bars.get(index);
            Instant observedAt = Instant.parse(frozen.path("sourceObservedAt").get(index).asText());
            if (observedAt.isAfter(frozenAt)) throw new IllegalStateException("FROZEN_INPUT_OBSERVATION_INVALID");
            HistoricalBar original = new HistoricalBar(bar.exchangeCode(), bar.marketType(), bar.symbol(), bar.interval(),
                    bar.openTime(), bar.closeTime(), bar.openPrice(), bar.highPrice(), bar.lowPrice(),
                    bar.closePrice(), bar.volume(), bar.quoteVolume(), bar.tradeCount(), bar.qualityStatus(),
                    bar.rawPayloadJson(), observedAt);
            validateSource(original, mapper);
            source.add(original);
        }
        if (!SpotBarIdentity.capture(source, mapper).sha256().equals(frozen.path("sourceSha256").asText()))
            throw new IllegalStateException("FROZEN_INPUT_SOURCE_DIGEST_MISMATCH");
        if (!SpotBarIdentity.capture(bars, mapper).sha256().equals(frozen.path("sha256").asText()))
            throw new IllegalStateException("FROZEN_INPUT_DIGEST_MISMATCH");
        return List.copyOf(bars);
    }

    private static void validate(JsonNode dataset, List<HistoricalBar> bars, Instant at) {
        Instant start = Instant.parse(dataset.path("startTime").asText());
        Instant end = Instant.parse(dataset.path("endTime").asText());
        if (!supports(dataset) || !"READY".equals(dataset.path("status").asText())
                || !"OK".equals(dataset.path("qualityStatus").asText())
                || dataset.path("gapCount").asLong(-1) != 0 || bars.isEmpty() || bars.size() > MAX_BARS
                || bars.size() != dataset.path("barCount").asLong(-1)
                || !start.equals(start.truncatedTo(java.time.temporal.ChronoUnit.HOURS))
                || !end.equals(start.plusSeconds(3600L * bars.size()).minusMillis(1)) || start.plusSeconds(3600L * bars.size()).isAfter(at))
            throw new IllegalStateException("FROZEN_INPUT_COVERAGE_INVALID");
        for (int index = 0; index < bars.size(); index++) {
            HistoricalBar bar = bars.get(index);
            if (!dataset.path("exchangeCode").asText().equals(bar.exchangeCode())
                    || !dataset.path("marketType").asText().equals(bar.marketType())
                    || !dataset.path("symbol").asText().equals(bar.symbol()) || bar.interval() != BarInterval.ONE_HOUR
                    || !bar.openTime().equals(start.plusSeconds(3600L * index))
                    || !bar.closeTime().equals(bar.openTime().plusSeconds(3600).minusMillis(1))
                    || !bar.availableAt().equals(bar.openTime().plusSeconds(3600))
                    || !"OK".equals(bar.qualityStatus()) || !positive(bar.openPrice())
                    || !positive(bar.highPrice()) || !positive(bar.lowPrice()) || !positive(bar.closePrice())
                    || bar.highPrice().compareTo(bar.openPrice().max(bar.closePrice()).max(bar.lowPrice())) < 0
                    || bar.lowPrice().compareTo(bar.openPrice().min(bar.closePrice()).min(bar.highPrice())) > 0
                    || bar.volume() == null || bar.volume().signum() < 0
                    || (bar.quoteVolume() != null && bar.quoteVolume().signum() < 0))
                throw new IllegalStateException("FROZEN_INPUT_BAR_INVALID");
        }
    }

    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }

    /** 后来时间流逝不会把早先采到的部分OHLC变成完整bar；保留并复核原收盘标识。 */
    private static void validateSource(HistoricalBar bar, ObjectMapper mapper) {
        if (bar.availableAt() == null || bar.availableAt().isBefore(bar.openTime().plus(bar.interval().duration())))
            throw new IllegalStateException("FROZEN_INPUT_SOURCE_UNCLOSED");
        try {
            JsonNode raw = mapper.readTree(bar.rawPayloadJson());
            if (raw != null && raw.isArray() && (raw.size() < 9 || !"1".equals(raw.path(8).asText())))
                throw new IllegalStateException("FROZEN_INPUT_SOURCE_UNCLOSED");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("FROZEN_INPUT_SOURCE_INVALID", failure);
        }
    }

    private static BigDecimal decimal(JsonNode bar, String field) { return new BigDecimal(bar.path(field).asText()); }
}
