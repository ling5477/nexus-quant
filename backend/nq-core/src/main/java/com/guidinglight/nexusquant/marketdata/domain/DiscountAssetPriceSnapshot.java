package com.guidinglight.nexusquant.marketdata.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 优惠资产的不可变历史价格来源；仅保存行情元数据，不拥有资金余额或账务事实。 */
public final class DiscountAssetPriceSnapshot {
    public static final String VERSION = "DISCOUNT_ASSET_PRICE_SNAPSHOT_V1";
    public static final int MAX_POINTS = 20_000;
    public static final int MAX_SOURCE_BYTES = 32 * 1024 * 1024;
    private static final Duration MAX_AGE = Duration.ofMinutes(5);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final UUID snapshotId;
    private final UUID datasetId;
    private final long ownerUserId;
    private final String exchangeCode;
    private final String assetCurrency;
    private final String quoteCurrency;
    private final Instant from;
    private final Instant to;
    private final Instant frozenAt;
    private final String sourceSha256;
    private final String sourceJson;
    private final List<Point> points;

    private DiscountAssetPriceSnapshot(JsonNode node, String sourceJson, String sha) {
        this.snapshotId = UUID.fromString(text(node, "snapshotId"));
        this.datasetId = UUID.fromString(text(node, "datasetId"));
        this.ownerUserId = Long.parseLong(text(node, "ownerUserId"));
        this.exchangeCode = text(node, "exchangeCode");
        this.assetCurrency = text(node, "assetCurrency");
        this.quoteCurrency = text(node, "quoteCurrency");
        this.from = time(node, "from");
        this.to = time(node, "to");
        this.frozenAt = time(node, "frozenAt");
        this.sourceSha256 = sha;
        this.sourceJson = sourceJson;
        validateScope(ownerUserId, exchangeCode, assetCurrency, quoteCurrency, from, to, frozenAt);
        JsonNode dataset = node.path("dataset");
        if (!VERSION.equals(text(node, "schemaVersion"))
                || !"db".equals(text(dataset, "provider"))
                || !"marketdata_bars".equals(text(dataset, "source"))
                || !datasetId.toString().equals(text(dataset, "datasetId"))
                || !exchangeCode.equals(text(dataset, "exchangeCode"))
                || !"SPOT".equals(text(dataset, "marketType"))
                || !(assetCurrency + "-" + quoteCurrency).equals(text(dataset, "symbol"))
                || !"1m".equals(text(dataset, "interval"))
                || !"READY".equals(text(dataset, "status"))
                || !"OK".equals(text(dataset, "qualityStatus"))
                || Long.parseLong(text(dataset, "gapCount")) != 0
                || time(dataset, "startTime")
                        .isAfter(from.minus(MAX_AGE).truncatedTo(ChronoUnit.MINUTES))
                || time(dataset, "endTime").isBefore(to)) throw invalid();
        JsonNode sourcePoints = node.path("points");
        if (!sourcePoints.isArray() || sourcePoints.isEmpty() || sourcePoints.size() > MAX_POINTS)
            throw invalid();
        List<Point> consumed = new ArrayList<>(sourcePoints.size());
        Instant previousOpen = null;
        for (JsonNode source : sourcePoints) {
            Instant open = time(source, "openTime"),
                    observed = time(source, "observedAt"),
                    visible = time(source, "visibleAt");
            if (!exchangeCode.equals(text(source, "exchangeCode"))
                    || !"SPOT".equals(text(source, "marketType"))
                    || !(assetCurrency + "-" + quoteCurrency).equals(text(source, "symbol"))
                    || !"1m".equals(text(source, "interval"))
                    || !"OK".equals(text(source, "qualityStatus"))
                    || Long.parseLong(text(source, "barId")) <= 0
                    || !text(source, "barIdentitySha256").matches("[a-f0-9]{64}")
                    || text(source, "barSource").length() > 128
                    || !observed.isAfter(open)
                    || observed.isAfter(open.plusSeconds(60))
                    || open.isBefore(time(dataset, "startTime"))
                    || visible.isBefore(observed)
                    || visible.isAfter(frozenAt)
                    || observed.isAfter(to)
                    || (previousOpen != null && !open.equals(previousOpen.plusSeconds(60))))
                throw invalid();
            BigDecimal price = positive(text(source, "closePrice"));
            BigDecimal opening = positive(text(source, "openPrice")),
                    high = positive(text(source, "highPrice")),
                    low = positive(text(source, "lowPrice"));
            if (high.compareTo(opening.max(price)) < 0
                    || low.compareTo(opening.min(price)) > 0
                    || high.compareTo(low) < 0
                    || decimal(text(source, "volume")).signum() < 0
                    || (!source.path("quoteVolume").isNull()
                            && decimal(text(source, "quoteVolume")).signum() < 0)
                    || (!source.path("tradeCount").isNull()
                            && Long.parseLong(text(source, "tradeCount")) < 0)) throw invalid();
            String rawPayload = text(source, "rawPayloadJson");
            if (!sha256(rawPayload).equals(text(source, "rawPayloadSha256"))) throw invalid();
            String pointJson = source.toString();
            consumed.add(new Point(price, observed, visible, sha256(pointJson), pointJson));
            previousOpen = open;
        }
        if (Long.parseLong(text(dataset, "barCount")) < consumed.size()
                || consumed.getFirst().observedAt().isAfter(from)
                || consumed.getFirst().observedAt().isAfter(from.minus(MAX_AGE).plusSeconds(60))
                || consumed.getLast().observedAt().isBefore(to.minusSeconds(60))) throw invalid();
        this.points = List.copyOf(consumed);
    }

    /** 仅由正式服务在一致读事务中调用；保存完整数据集与原OHLCV内容，不根据展示四价时点猜测可见性。 */
    public static DiscountAssetPriceSnapshot capture(
            long owner,
            MarketdataDataset dataset,
            String exchange,
            String asset,
            String quote,
            Instant from,
            Instant to,
            Instant frozenAt,
            List<HistoricalBar> bars,
            Map<Instant, BarProvenance> versions) {
        validateScope(owner, exchange, asset, quote, from, to, frozenAt);
        if (dataset == null
                || bars == null
                || bars.isEmpty()
                || bars.size() > MAX_POINTS
                || versions == null
                || versions.size() != bars.size()) throw invalid();
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", VERSION);
        root.put("snapshotId", UUID.randomUUID().toString());
        root.put("datasetId", dataset.datasetId().toString());
        root.put("ownerUserId", Long.toString(owner));
        root.put("exchangeCode", exchange);
        root.put("assetCurrency", asset);
        root.put("quoteCurrency", quote);
        root.put("from", from.toString());
        root.put("to", to.toString());
        root.put("frozenAt", frozenAt.toString());
        ObjectNode identity = root.putObject("dataset");
        identity.put("provider", "db");
        identity.put("source", dataset.source());
        identity.put("datasetId", dataset.datasetId().toString());
        identity.put("datasetName", dataset.datasetName());
        identity.put("exchangeCode", dataset.exchangeCode());
        identity.put("marketType", dataset.marketType());
        identity.put("symbol", dataset.symbol());
        identity.put("interval", dataset.interval().wireValue());
        identity.put("startTime", dataset.startTime().toString());
        identity.put("endTime", dataset.endTime().toString());
        identity.put("status", dataset.status().name());
        identity.put("qualityStatus", dataset.qualityStatus().name());
        identity.put("barCount", Long.toString(dataset.barCount()));
        identity.put("gapCount", Long.toString(dataset.gapCount()));
        identity.put("createdBy", dataset.createdBy());
        identity.put("createdAt", dataset.createdAt().toString());
        identity.put("updatedAt", dataset.updatedAt().toString());
        identity.put("requestJson", dataset.requestJson());
        var series = root.putArray("points");
        for (HistoricalBar bar : bars) {
            if (bar == null
                    || bar.rawPayloadJson() == null
                    || bar.rawPayloadJson().length() > 16_384) throw invalid();
            BarProvenance version = versions.get(bar.openTime());
            if (version == null
                    || version.barId() == null
                    || version.barIdentitySha256() == null
                    || !bar.openTime().equals(version.openTime())
                    || !bar.availableAt().equals(version.availableAt())) throw invalid();
            ObjectNode point = series.addObject();
            point.put("barId", Long.toString(version.barId()));
            point.put("barIdentitySha256", version.barIdentitySha256());
            point.put("barSource", version.source());
            point.put("exchangeCode", bar.exchangeCode());
            point.put("marketType", bar.marketType());
            point.put("symbol", bar.symbol());
            point.put("interval", bar.interval().wireValue());
            point.put("openTime", bar.openTime().toString());
            point.put("observedAt", bar.closeTime().toString());
            point.put("visibleAt", bar.availableAt().toString());
            point.put("openPrice", exact(bar.openPrice()));
            point.put("highPrice", exact(bar.highPrice()));
            point.put("lowPrice", exact(bar.lowPrice()));
            point.put("closePrice", exact(bar.closePrice()));
            point.put("volume", exact(bar.volume()));
            if (bar.quoteVolume() == null) point.putNull("quoteVolume");
            else point.put("quoteVolume", exact(bar.quoteVolume()));
            if (bar.tradeCount() == null) point.putNull("tradeCount");
            else point.put("tradeCount", Long.toString(bar.tradeCount()));
            point.put("qualityStatus", bar.qualityStatus());
            point.put("rawPayloadJson", bar.rawPayloadJson());
            point.put("rawPayloadSha256", sha256(bar.rawPayloadJson()));
        }
        String raw = root.toString();
        return restore(raw, sha256(raw));
    }

    /** 从持久原文本恢复；摘要只证明内容未漂移，来源准入仍由正式repository及服务负责。 */
    public static DiscountAssetPriceSnapshot restore(String sourceJson, String expectedSha) {
        if (sourceJson == null
                || sourceJson.isBlank()
                || sourceJson.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES
                || expectedSha == null
                || !expectedSha.matches("[a-f0-9]{64}")
                || !sha256(sourceJson).equals(expectedSha)) throw invalid();
        try {
            JsonNode node = MAPPER.readTree(sourceJson);
            if (!node.isObject()) throw invalid();
            return new DiscountAssetPriceSnapshot(node, sourceJson, expectedSha);
        } catch (JsonProcessingException | NumberFormatException | DateTimeException failure) {
            throw invalid();
        }
    }

    /** 最新已可见收盘观察用于换算；迟到、未来或超过五分钟的价格均保持不可用，不补常数或当前报价。 */
    public Point priceAt(Instant asof) {
        requireMicro(asof);
        if (asof.isBefore(from) || asof.isAfter(to)) throw unavailable();
        for (int index = points.size() - 1; index >= 0; index--) {
            Point point = points.get(index);
            if (point.observedAt().isBefore(asof.minus(MAX_AGE))) break;
            if (!point.observedAt().isAfter(asof) && !point.visibleAt().isAfter(asof)) return point;
        }
        throw unavailable();
    }

    /** 消费端提供扣除本金及其他在途预留后的余额；本快照绝不把预计返佣转换为余额。 */
    public SpotFeeModel.DiscountFunding funding(Instant asof, BigDecimal availableAfterReserves) {
        Point point = priceAt(asof);
        return new SpotFeeModel.DiscountFunding(
                assetCurrency,
                quoteCurrency,
                point.quotePerAsset(),
                availableAfterReserves,
                point.observedAt(),
                MAX_AGE,
                point.sourceSha256());
    }

    private static void validateScope(
            long owner,
            String exchange,
            String asset,
            String quote,
            Instant from,
            Instant to,
            Instant frozen) {
        requireMicro(from);
        requireMicro(to);
        requireMicro(frozen);
        if (owner <= 0
                || !("BINANCE".equals(exchange) || "OKX".equals(exchange))
                || !"BNB".equals(asset)
                || !"USDT".equals(quote)
                || !to.isAfter(from)
                || to.isAfter(frozen)
                || Duration.between(from.minus(MAX_AGE), to).toMinutes() >= MAX_POINTS)
            throw invalid();
    }

    private static void requireMicro(Instant at) {
        if (at == null || !at.equals(at.truncatedTo(ChronoUnit.MICROS))) throw invalid();
    }

    private static Instant time(JsonNode node, String name) {
        Instant at = Instant.parse(text(node, name));
        requireMicro(at);
        return at;
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isTextual() || value.textValue().isBlank()) throw invalid();
        return value.textValue();
    }

    private static String exact(BigDecimal value) {
        if (value == null) throw invalid();
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimal(String value) {
        BigDecimal number = new BigDecimal(value);
        if (number.stripTrailingZeros().scale() > 18 || number.precision() > 38) throw invalid();
        return number;
    }

    private static BigDecimal positive(String value) {
        BigDecimal number = decimal(value);
        if (number.signum() <= 0) throw invalid();
        return number;
    }

    /** 原文本按UTF-8计算身份，不以重新排版或数据库JSONB输出替代已冻结字节。 */
    public static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("FEE_DISCOUNT_PRICE_SOURCE_INVALID");
    }

    private static IllegalArgumentException unavailable() {
        return new IllegalArgumentException("FEE_DISCOUNT_PRICE_UNAVAILABLE");
    }

    public UUID snapshotId() {
        return snapshotId;
    }

    public UUID datasetId() {
        return datasetId;
    }

    public long ownerUserId() {
        return ownerUserId;
    }

    public String exchangeCode() {
        return exchangeCode;
    }

    public String assetCurrency() {
        return assetCurrency;
    }

    public String quoteCurrency() {
        return quoteCurrency;
    }

    public Instant from() {
        return from;
    }

    public Instant to() {
        return to;
    }

    public Instant frozenAt() {
        return frozenAt;
    }

    public String sourceSha256() {
        return sourceSha256;
    }

    public String sourceJson() {
        return sourceJson;
    }

    public List<Point> points() {
        return points;
    }

    /** 原观察时点和实际可见时点分别保存；单点来源摘要可从原文本复算。 */
    public record Point(
            BigDecimal quotePerAsset,
            Instant observedAt,
            Instant visibleAt,
            String sourceSha256,
            String sourceJson) {}

    @Override
    public String toString() {
        return "DiscountAssetPriceSnapshot[" + snapshotId + ",points=" + points.size() + "]";
    }
}
