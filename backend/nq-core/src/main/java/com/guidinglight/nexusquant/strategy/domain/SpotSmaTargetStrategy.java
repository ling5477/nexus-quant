package com.guidinglight.nexusquant.strategy.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 一个冻结版本驱动的现货目标敞口样例；只消费已收盘且连续的 bar。 */
public final class SpotSmaTargetStrategy {
    public static final String EXECUTABLE = "SPOT_SMA_TARGET_V1";

    private final String strategyVersionId;
    private final String checksum;
    private final int window;
    private final BigDecimal investedExposure;

    private SpotSmaTargetStrategy(String strategyVersionId, String checksum, int window,
                                  BigDecimal investedExposure) {
        this.strategyVersionId = strategyVersionId;
        this.checksum = checksum;
        this.window = window;
        this.investedExposure = investedExposure;
    }

    public static SpotSmaTargetStrategy fromSnapshot(String snapshotJson, ObjectMapper mapper) {
        Objects.requireNonNull(mapper, "mapper");
        try {
            JsonNode snapshot = mapper.readTree(snapshotJson);
            String versionId = requiredText(snapshot, "strategyVersionId");
            String checksum = requiredText(snapshot, "checksum");
            JsonNode source = child(snapshot, "sourceSnapshotJson", "sourceSnapshot");
            if (!EXECUTABLE.equals(requiredText(source, "executable"))) {
                throw new IllegalArgumentException("unsupported frozen strategy executable");
            }
            JsonNode parameters = child(snapshot, "paramSnapshotJson", "paramSnapshot");
            int window = parameters.path("window").asInt(-1);
            if (window < 2 || window > 200) {
                throw new IllegalArgumentException("window must be between 2 and 200");
            }
            BigDecimal exposure = new BigDecimal(requiredText(parameters, "investedExposure"));
            if (exposure.signum() <= 0 || exposure.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("investedExposure must be within (0, 1]");
            }
            return new SpotSmaTargetStrategy(versionId, checksum, window, exposure);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid frozen strategy snapshot", ex);
        }
    }

    public Decision evaluate(List<HistoricalBar> closedBars) {
        Objects.requireNonNull(closedBars, "closedBars");
        if (closedBars.isEmpty()) {
            return new Decision(BigDecimal.ZERO, "INSUFFICIENT_HISTORY", null);
        }
        for (int index = 0; index < closedBars.size(); index++) {
            HistoricalBar bar = Objects.requireNonNull(closedBars.get(index), "bar");
            if (!bar.closeTime().isAfter(bar.openTime()) || bar.availableAt() == null
                    || bar.availableAt().isBefore(bar.closeTime()) || bar.closePrice() == null
                    || bar.closePrice().signum() <= 0 || !"OK".equals(bar.qualityStatus())) {
                throw new IllegalArgumentException("INVALID_BAR");
            }
            if (index > 0) {
                HistoricalBar previous = closedBars.get(index - 1);
                if (!bar.openTime().isAfter(previous.openTime())) {
                    throw new IllegalArgumentException("DUPLICATE_OR_OUT_OF_ORDER_BAR");
                }
                if (!bar.openTime().equals(previous.openTime().plus(previous.interval().duration()))
                        || !bar.exchangeCode().equals(previous.exchangeCode())
                        || !bar.marketType().equals(previous.marketType())
                        || !bar.symbol().equals(previous.symbol()) || bar.interval() != previous.interval()) {
                    throw new IllegalArgumentException("MISSING_OR_MIXED_BAR");
                }
                if (bar.availableAt().isBefore(previous.availableAt())) {
                    throw new IllegalArgumentException("LATE_ARRIVING_BAR_IN_SIGNAL_WINDOW");
                }
            }
        }
        HistoricalBar latest = closedBars.getLast();
        if (closedBars.size() < window) {
            return new Decision(BigDecimal.ZERO, "INSUFFICIENT_HISTORY", latest.availableAt());
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = closedBars.size() - window; index < closedBars.size(); index++) {
            sum = sum.add(closedBars.get(index).closePrice());
        }
        BigDecimal average = sum.divide(BigDecimal.valueOf(window), MathContext.DECIMAL128);
        BigDecimal target = latest.closePrice().compareTo(average) > 0
                ? investedExposure : BigDecimal.ZERO;
        return new Decision(target, target.signum() > 0 ? "ABOVE_SMA" : "AT_OR_BELOW_SMA",
                latest.availableAt());
    }

    public String strategyVersionId() { return strategyVersionId; }
    public String checksum() { return checksum; }
    public int window() { return window; }
    public BigDecimal investedExposure() { return investedExposure; }

    public record Decision(BigDecimal targetExposure, String reason, Instant availableAt) { }

    private static JsonNode child(JsonNode parent, String first, String second) {
        JsonNode child = parent.path(first);
        if (child.isMissingNode()) {
            child = parent.path(second);
        }
        if (child.isTextual()) {
            try {
                child = new ObjectMapper().readTree(child.asText());
            } catch (Exception ex) {
                throw new IllegalArgumentException("invalid strategy snapshot child", ex);
            }
        }
        if (!child.isObject()) {
            throw new IllegalArgumentException("missing strategy snapshot child: " + first);
        }
        return child;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value;
    }
}
