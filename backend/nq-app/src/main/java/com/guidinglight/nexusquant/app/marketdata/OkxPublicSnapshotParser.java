package com.guidinglight.nexusquant.app.marketdata;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot.BarStatus.CLOSED;
import static com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot.BarStatus.FORMING;
import static com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot.SourceStatus.INCOMPLETE_RESPONSE;
import static com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot.SourceStatus.INVALID_RESPONSE;
import static com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot.SourceStatus.RATE_LIMITED;

final class OkxPublicSnapshotParser {
    static final int MAX_BARS = 100;
    static final long FRESH_SECONDS = 90;
    private final ObjectReader reader;

    OkxPublicSnapshotParser(ObjectMapper mapper) {
        reader = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    Ticker ticker(byte[] payload, String venueSymbol, Instant receivedAt) {
        JsonNode data = rows(payload);
        if (data.size() != 1) throw invalid();
        JsonNode row = data.get(0);
        if (!venueSymbol.equals(requiredText(row.path("instId")))
                || !"SPOT".equals(requiredText(row.path("instType")))) throw invalid();
        Instant timestamp = timestamp(row.path("ts"));
        if (timestamp.isAfter(receivedAt.plusSeconds(5))) throw invalid();
        return new Ticker(decimal(row.path("last"), false).toPlainString(), timestamp);
    }

    List<PublicMarketSnapshot.Bar> candles(byte[] payload, BarInterval interval, Instant receivedAt) {
        JsonNode data = rows(payload);
        if (data.size() > MAX_BARS) throw invalid();
        List<PublicMarketSnapshot.Bar> bars = new ArrayList<>(data.size());
        long periodMillis = interval.duration().toMillis();
        // OKX 1D 以 UTC+8 的零点开盘，沿用历史读取的 1D 周期。
        long offsetMillis = interval == BarInterval.ONE_DAY ? 28_800_000 : 0;
        for (JsonNode row : data) {
            if (!row.isArray() || row.size() > 9) throw invalid();
            if (row.size() < 9) throw incomplete();
            Instant openTime = timestamp(row.get(0));
            Instant closeTime = openTime.plus(interval.duration()).minusMillis(1);
            if (Math.floorMod(openTime.toEpochMilli() + offsetMillis, periodMillis) != 0
                    || openTime.isAfter(receivedAt.plusSeconds(5))) throw invalid();
            var status = switch (requiredText(row.get(8))) {
                case "0" -> FORMING;
                case "1" -> CLOSED;
                default -> throw invalid();
            };
            if (status == CLOSED && closeTime.isAfter(receivedAt.plusSeconds(5))) throw invalid();
            BigDecimal open = decimal(row.get(1), false);
            BigDecimal high = decimal(row.get(2), false);
            BigDecimal low = decimal(row.get(3), false);
            BigDecimal close = decimal(row.get(4), false);
            BigDecimal volume = decimal(row.get(5), true);
            decimal(row.get(6), true);
            decimal(row.get(7), true);
            if (low.compareTo(open.min(close)) > 0 || high.compareTo(open.max(close)) < 0)
                throw invalid();
            bars.add(new PublicMarketSnapshot.Bar(openTime, closeTime, open.toPlainString(),
                    high.toPlainString(), low.toPlainString(), close.toPlainString(),
                    volume.toPlainString(), status));
        }
        bars.sort(Comparator.comparing(PublicMarketSnapshot.Bar::openTime));
        for (int i = 0; i < bars.size() - 1; i++) {
            if (bars.get(i).status() == FORMING || bars.get(i).openTime().equals(bars.get(i + 1).openTime()))
                throw invalid();
        }
        return List.copyOf(bars);
    }

    private JsonNode rows(byte[] payload) {
        try {
            JsonNode root = reader.readTree(payload);
            if (root == null || !root.isObject()) throw invalid();
            String code = requiredText(root.path("code"));
            if ("50011".equals(code) || "50040".equals(code)) throw new SourceFailure(RATE_LIMITED);
            if (!"0".equals(code)) throw invalid();
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull() || data.isEmpty()) throw incomplete();
            if (!data.isArray()) throw invalid();
            return data;
        } catch (IOException ex) {
            throw invalid();
        }
    }

    private static String requiredText(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) throw incomplete();
        if (!value.isTextual()) throw invalid();
        if (value.textValue().isEmpty()) throw incomplete();
        return value.textValue();
    }

    private static Instant timestamp(JsonNode node) {
        String value = requiredText(node);
        if (!value.matches("[0-9]{1,15}") || Long.parseLong(value) <= 0) throw invalid();
        return Instant.ofEpochMilli(Long.parseLong(value));
    }

    private static BigDecimal decimal(JsonNode node, boolean allowZero) {
        String value = requiredText(node);
        if (value.length() > 64 || !value.matches("[0-9]+(\\.[0-9]+)?")) throw invalid();
        BigDecimal parsed = new BigDecimal(value);
        if (parsed.precision() > 40 || parsed.scale() > 18 || (!allowZero && parsed.signum() == 0))
            throw invalid();
        return parsed;
    }

    private static SourceFailure invalid() { return new SourceFailure(INVALID_RESPONSE); }

    private static SourceFailure incomplete() { return new SourceFailure(INCOMPLETE_RESPONSE); }

    record Ticker(String price, Instant venueTime) {}

    static final class SourceFailure extends RuntimeException {
        private final PublicMarketSnapshot.SourceStatus status;

        SourceFailure(PublicMarketSnapshot.SourceStatus status) {
            super(status.name());
            this.status = status;
        }

        PublicMarketSnapshot.SourceStatus status() { return status; }
    }
}
