package com.guidinglight.nexusquant.strategy.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 冻结版本的共享执行契约：目标方向与本次交易动作分开，策略状态不从账户持仓推断。 */
public final class SpotTargetStrategy {
    public static final String CHANNEL_BREAKOUT = "SPOT_CHANNEL_BREAKOUT_V1";

    public enum PositionState { FLAT, LONG }
    public enum Action { REBALANCE, ENTER_LONG, EXIT_LONG, HOLD }

    public record Decision(BigDecimal targetExposure, String reason, Instant availableAt,
                           PositionState desiredState, Action action) {
        public boolean requestsTrade() { return action != Action.HOLD; }
    }

    private final SpotSmaTargetStrategy sma;
    private final String executable;
    private final String versionId;
    private final String checksum;
    private final BigDecimal exposure;
    private final int entryLookbackBars;
    private final int exitLookbackBars;

    private SpotTargetStrategy(SpotSmaTargetStrategy sma, String executable, String versionId,
            String checksum, BigDecimal exposure, int entryLookbackBars, int exitLookbackBars) {
        this.sma = sma;
        this.executable = executable;
        this.versionId = versionId;
        this.checksum = checksum;
        this.exposure = exposure;
        this.entryLookbackBars = entryLookbackBars;
        this.exitLookbackBars = exitLookbackBars;
    }

    public static SpotTargetStrategy fromSnapshot(String json, ObjectMapper mapper) {
        try {
            JsonNode snapshot = mapper.reader()
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(json);
            JsonNode source = child(snapshot, "sourceSnapshotJson", "sourceSnapshot", mapper);
            String executable = text(source, "executable");
            if (SpotSmaTargetStrategy.EXECUTABLE.equals(executable)) {
                SpotSmaTargetStrategy sma = SpotSmaTargetStrategy.fromSnapshot(json, mapper);
                return new SpotTargetStrategy(sma, executable, sma.strategyVersionId(), sma.checksum(),
                        sma.investedExposure(), sma.window(), 0);
            }
            if (!CHANNEL_BREAKOUT.equals(executable)) {
                throw new IllegalArgumentException("unsupported frozen strategy executable");
            }
            JsonNode parameters = child(snapshot, "paramSnapshotJson", "paramSnapshot", mapper);
            int entry = integer(parameters, "entryLookbackBars");
            int exit = integer(parameters, "exitLookbackBars");
            if (exit < 2 || entry <= exit || entry > 200) {
                throw new IllegalArgumentException("require 2 <= exitLookbackBars < entryLookbackBars <= 200");
            }
            BigDecimal exposure = new BigDecimal(text(parameters, "investedExposure"));
            // 复用的 durable 决策列精度为8位，超精度目标必须在冻结前拒绝，不能舍入策略状态。
            if (exposure.signum() <= 0 || exposure.compareTo(BigDecimal.ONE) > 0
                    || exposure.stripTrailingZeros().scale() > 8) {
                throw new IllegalArgumentException("investedExposure must be within (0, 1] with at most 8 decimals");
            }
            // V1 的时间及交易形态是固定合同，不能通过附加配置暗中切换到盘中/做空/加仓。
            requireFixed(source, "confirmation", "BAR_CLOSE");
            requireFixed(source, "timeframe", "1h");
            requireFixed(source, "currentBarExcluded", true);
            requireFixed(source, "pyramiding", false);
            requireFixed(source, "allowShort", false);
            return new SpotTargetStrategy(null, executable, text(snapshot, "strategyVersionId"),
                    text(snapshot, "checksum"), exposure, entry, exit);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("invalid frozen strategy snapshot", failure);
        }
    }

    public void validate(List<HistoricalBar> bars) {
        SpotIndicators.validateClosedBars(bars);
        if (sma != null) return;
        for (HistoricalBar bar : bars) {
            if (bar.interval() != BarInterval.ONE_HOUR || !"SPOT".equals(bar.marketType())
                    || !bar.closeTime().equals(bar.openTime().plusSeconds(3600).minusMillis(1))
                    || bar.availableAt().isBefore(bar.openTime().plusSeconds(3600))
                    || bar.openPrice() == null || bar.highPrice() == null || bar.lowPrice() == null
                    || bar.lowPrice().signum() <= 0
                    || bar.highPrice().compareTo(bar.openPrice().max(bar.closePrice())) < 0
                    || bar.lowPrice().compareTo(bar.openPrice().min(bar.closePrice())) > 0) {
                throw new IllegalArgumentException("INVALID_CHANNEL_BAR");
            }
        }
    }

    public Decision evaluate(List<HistoricalBar> bars, PositionState previousState) {
        Objects.requireNonNull(previousState, "previousState");
        if (sma != null) {
            var decision = sma.evaluate(bars);
            return new Decision(decision.targetExposure(), decision.reason(), decision.availableAt(),
                    stateForTarget(decision.targetExposure()), Action.REBALANCE);
        }
        validate(bars);
        Instant availableAt = bars.isEmpty() ? null : bars.getLast().availableAt();
        if (bars.size() <= entryLookbackBars) {
            return decision(previousState, "INSUFFICIENT_HISTORY", availableAt, Action.HOLD);
        }
        BigDecimal close = bars.getLast().closePrice();
        if (previousState == PositionState.FLAT
                && close.compareTo(SpotIndicators.previousHigh(bars, entryLookbackBars)) > 0) {
            return decision(PositionState.LONG, "ENTER_LONG", availableAt, Action.ENTER_LONG);
        }
        if (previousState == PositionState.LONG
                && close.compareTo(SpotIndicators.previousLow(bars, exitLookbackBars)) < 0) {
            return decision(PositionState.FLAT, "EXIT_LONG", availableAt, Action.EXIT_LONG);
        }
        return decision(previousState, previousState == PositionState.LONG ? "HOLD_LONG" : "HOLD_FLAT",
                availableAt, Action.HOLD);
    }

    private Decision decision(PositionState state, String reason, Instant at, Action action) {
        return new Decision(state == PositionState.LONG ? exposure : BigDecimal.ZERO, reason, at, state, action);
    }

    /** target_exposure 是 durable 决策的目标方向，拒单/未成交不能把它改成实际持仓。 */
    public PositionState stateForTarget(BigDecimal target) {
        Objects.requireNonNull(target, "persisted strategy target");
        if (target.signum() == 0) return PositionState.FLAT;
        if (target.compareTo(exposure) == 0) return PositionState.LONG;
        throw new IllegalStateException("SIM_STRATEGY_STATE_INVALID");
    }

    public boolean sameFrozenStrategy(SpotTargetStrategy other) {
        return versionId.equals(other.versionId) && checksum.equals(other.checksum)
                && executable.equals(other.executable) && entryLookbackBars == other.entryLookbackBars
                && exitLookbackBars == other.exitLookbackBars && exposure.compareTo(other.exposure) == 0;
    }

    public String executable() { return executable; }
    public String strategyVersionId() { return versionId; }
    public String checksum() { return checksum; }
    public boolean stateful() { return sma == null; }

    private static JsonNode child(JsonNode node, String first, String second, ObjectMapper mapper)
            throws java.io.IOException {
        JsonNode value = node.path(first);
        if (value.isMissingNode()) value = node.path(second);
        if (value.isTextual()) value = mapper.readTree(value.asText());
        if (!value.isObject()) throw new IllegalArgumentException("missing " + first);
        return value;
    }

    private static String text(JsonNode node, String name) {
        String value = node.path(name).asText("").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("missing " + name);
        return value;
    }

    private static int integer(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return value.intValue();
    }

    private static void requireFixed(JsonNode source, String name, String expected) {
        if (source.has(name) && !expected.equals(source.path(name).asText())) {
            throw new IllegalArgumentException("unsupported " + name);
        }
    }

    private static void requireFixed(JsonNode source, String name, boolean expected) {
        if (source.has(name) && (!source.path(name).isBoolean() || source.path(name).booleanValue() != expected)) {
            throw new IllegalArgumentException("unsupported " + name);
        }
    }
}
